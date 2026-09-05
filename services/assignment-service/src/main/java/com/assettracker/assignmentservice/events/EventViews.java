package com.assettracker.assignmentservice.events;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

/** Request and response bodies for event sign-out. */
public final class EventViews {

  private EventViews() {}

  /** What the sign-out form posts. */
  public record CreateRequest(
      @NotNull Long clientId,
      @NotBlank String eventName,
      @NotNull LocalDate eventDate,
      String location,
      String notes,
      @NotEmpty List<@Valid LineRequest> lines) {}

  public record LineRequest(@NotBlank String itemType, @Min(1) int quantity, String notes) {}

  /** Approve / deny body. */
  public record DecisionRequest(String note) {}

  /** Fulfilment body: which concrete assets are going out for which line. */
  public record FulfilRequest(@NotEmpty List<@Valid FulfilLine> lines) {}

  public record FulfilLine(@NotNull Long lineId, @NotEmpty List<Long> assetIds) {}

  public record LineView(
      Long id, String itemType, int quantity, String notes, List<Long> fulfilledAssetIds) {

    static LineView from(EventRequestLine line) {
      String ids = line.getFulfilledAssetIds();
      List<Long> assetIds =
          ids.isBlank()
              ? List.of()
              : Arrays.stream(ids.split(",")).map(String::trim).map(Long::valueOf).toList();
      return new LineView(
          line.getId(), line.getItemType(), line.getQuantity(), line.getNotes(), assetIds);
    }
  }

  public record RequestView(
      Long id,
      Long clientId,
      String eventName,
      LocalDate eventDate,
      String location,
      String notes,
      String requestedBy,
      Long requesterPersonId,
      EventRequestStatus status,
      String decidedBy,
      Instant decidedAt,
      String decisionNote,
      Instant createdAt,
      List<LineView> lines) {

    public static RequestView from(EventRequest r) {
      return new RequestView(
          r.getId(),
          r.getClientId(),
          r.getEventName(),
          r.getEventDate(),
          r.getLocation(),
          r.getNotes(),
          r.getRequestedBy(),
          r.getRequesterPersonId(),
          r.getStatus(),
          r.getDecidedBy(),
          r.getDecidedAt(),
          r.getDecisionNote(),
          r.getCreatedAt(),
          r.getLines().stream().map(LineView::from).toList());
    }
  }
}
