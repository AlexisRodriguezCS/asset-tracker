package com.assettracker.assignmentservice.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.assettracker.assignmentservice.audit.AuditService;
import com.assettracker.assignmentservice.events.EventViews.CreateRequest;
import com.assettracker.assignmentservice.events.EventViews.FulfilLine;
import com.assettracker.assignmentservice.events.EventViews.FulfilRequest;
import com.assettracker.assignmentservice.events.EventViews.LineRequest;
import com.assettracker.assignmentservice.service.AssignmentService;
import com.assettracker.assignmentservice.web.CallerContextTestSupport;
import com.assettracker.assignmentservice.web.dto.CheckOutRequest;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EventRequestServiceTest {

  private static final long ACME = 1L;
  private static final long DANA = 1L;
  private static final long SAM = 2L;

  @Mock EventRequestRepository requests;
  @Mock AssignmentService assignments;
  @Mock AuditService audit;

  @InjectMocks EventRequestService service;

  @AfterEach
  void clearCaller() {
    CallerContextTestSupport.reset();
    CallerContextTestSupport.clearTenant();
  }

  private CreateRequest form() {
    return new CreateRequest(
        ACME,
        "Career Fair",
        LocalDate.of(2026, 10, 3),
        "Main Gym",
        "need it by 8am",
        List.of(new LineRequest("Laptop", 1, "loaner"), new LineRequest("TV", 2, null)));
  }

  private EventRequest saved(EventRequestStatus status, Long requesterPersonId) {
    EventRequest r =
        new EventRequest(
            ACME,
            "Career Fair",
            LocalDate.of(2026, 10, 3),
            "Main Gym",
            null,
            "dana.reyes@acme.example",
            requesterPersonId);
    if (status == EventRequestStatus.APPROVED) {
      r.decide(EventRequestStatus.APPROVED, "poc@acme.example", null);
    }
    return r;
  }

  @Test
  void submittingKeepsEveryLineAndStartsUndecided() {
    when(requests.save(any(EventRequest.class))).thenAnswer(i -> i.getArgument(0));

    EventRequest created = service.create(form(), "dana.reyes@acme.example", DANA);

    assertThat(created.getStatus()).isEqualTo(EventRequestStatus.SUBMITTED);
    assertThat(created.getEventName()).isEqualTo("Career Fair");
    assertThat(created.getRequesterPersonId()).isEqualTo(DANA);
    assertThat(created.getLines())
        .extracting(EventRequestLine::getItemType, EventRequestLine::getQuantity)
        .containsExactly(
            org.assertj.core.api.Assertions.tuple("Laptop", 1),
            org.assertj.core.api.Assertions.tuple("TV", 2));
  }

  @Test
  void anEmployeeSeesOnlyTheirOwnRequests() {
    CallerContextTestSupport.as("USER", DANA);
    when(requests.findByClientIdAndRequesterPersonIdOrderByEventDateDesc(ACME, DANA))
        .thenReturn(List.of(saved(EventRequestStatus.SUBMITTED, DANA)));

    assertThat(service.list(ACME, null)).hasSize(1);
    verify(requests, never()).findByClientIdOrderByEventDateDesc(any());
  }

  @Test
  void anEmployeeWithNoPersonRecordSeesNothingRatherThanEverything() {
    CallerContextTestSupport.as("USER", null);

    assertThat(service.list(ACME, null)).isEmpty();
    verify(requests, never()).findByClientIdOrderByEventDateDesc(any());
  }

  @Test
  void staffSeeTheWholeTenant() {
    CallerContextTestSupport.as("POC", null);
    when(requests.findByClientIdOrderByEventDateDesc(ACME))
        .thenReturn(
            List.of(
                saved(EventRequestStatus.SUBMITTED, DANA),
                saved(EventRequestStatus.SUBMITTED, SAM)));

    assertThat(service.list(ACME, null)).hasSize(2);
  }

  @Test
  void anEmployeeCannotOpenSomeoneElsesRequest() {
    CallerContextTestSupport.as("USER", DANA);
    when(requests.findById(9L)).thenReturn(Optional.of(saved(EventRequestStatus.SUBMITTED, SAM)));

    assertThatThrownBy(() -> service.getById(9L)).isInstanceOf(EventRequestNotFoundException.class);
  }

  @Test
  void decidingTwiceIsRejected() {
    when(requests.findById(9L)).thenReturn(Optional.of(saved(EventRequestStatus.APPROVED, DANA)));

    assertThatThrownBy(
            () -> service.decide(9L, EventRequestStatus.APPROVED, null, "poc@acme.example"))
        .isInstanceOf(EventRequestStateException.class)
        .hasMessageContaining("already APPROVED");
  }

  @Test
  void gearCannotBeHandedOutBeforeApproval() {
    when(requests.findById(9L)).thenReturn(Optional.of(saved(EventRequestStatus.SUBMITTED, DANA)));

    assertThatThrownBy(
            () ->
                service.fulfil(
                    9L,
                    new FulfilRequest(List.of(new FulfilLine(1L, List.of(40L)))),
                    "tech@acme.example"))
        .isInstanceOf(EventRequestStateException.class)
        .hasMessageContaining("only an APPROVED request can be fulfilled");
    verify(assignments, never()).checkOut(any(), anyString());
  }

  @Test
  void fulfillingChecksEveryAssetOutToTheRequesterThroughTheNormalPath() {
    EventRequest approved = saved(EventRequestStatus.APPROVED, DANA);
    EventRequestLine line = new EventRequestLine("TV", 2, null);
    setId(line, 5L);
    approved.addLine(line);
    when(requests.findById(9L)).thenReturn(Optional.of(approved));
    when(requests.save(any(EventRequest.class))).thenAnswer(i -> i.getArgument(0));

    service.fulfil(
        9L, new FulfilRequest(List.of(new FulfilLine(5L, List.of(40L, 41L)))), "tech@acme.example");

    ArgumentCaptor<CheckOutRequest> sent = ArgumentCaptor.forClass(CheckOutRequest.class);
    verify(assignments, times(2)).checkOut(sent.capture(), anyString());
    assertThat(sent.getAllValues())
        .allSatisfy(r -> assertThat(r.holderId()).isEqualTo(DANA))
        .extracting(CheckOutRequest::assetId)
        .containsExactly(40L, 41L);
    assertThat(approved.getStatus()).isEqualTo(EventRequestStatus.FULFILLED);
    assertThat(line.getFulfilledAssetIds()).isEqualTo("40,41");
  }

  /** The id is database-generated; tests need one to match a fulfilment line against. */
  private static void setId(EventRequestLine line, Long id) {
    try {
      var field = EventRequestLine.class.getDeclaredField("id");
      field.setAccessible(true);
      field.set(line, id);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }
}
