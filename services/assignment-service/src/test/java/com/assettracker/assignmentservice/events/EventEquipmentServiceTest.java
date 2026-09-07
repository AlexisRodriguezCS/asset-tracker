package com.assettracker.assignmentservice.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.assettracker.assignmentservice.audit.AuditService;
import com.assettracker.assignmentservice.events.EventViews.Availability;
import com.assettracker.assignmentservice.events.EventViews.LineRequest;
import com.assettracker.assignmentservice.web.CallerContextTestSupport;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Availability is "what the client owns, minus what is already booked that day", computed from the
 * requests rather than from a running counter. Against a real database, because the subtraction is
 * a group-by over request lines and getting it wrong is invisible until two people turn up for the
 * same TV.
 */
@DataJpaTest
@Import(EventEquipmentService.class)
class EventEquipmentServiceTest {

  private static final long ACME = 1L;
  private static final long GLOBEX = 2L;
  private static final LocalDate FAIR = LocalDate.of(2026, 10, 3);
  private static final LocalDate OTHER_DAY = LocalDate.of(2026, 10, 4);

  @Autowired EventEquipmentService service;
  @Autowired EventEquipmentRepository equipment;
  @Autowired EventRequestRepository requests;

  @MockitoBean AuditService audit;

  @BeforeEach
  void seed() {
    CallerContextTestSupport.tenant(Set.of(ACME, GLOBEX));
    CallerContextTestSupport.as("TECH", null);
    equipment.saveAll(
        List.of(
            new EventEquipment(ACME, "TV", 2),
            new EventEquipment(ACME, "Speaker", 2),
            new EventEquipment(ACME, "Mic", 1),
            new EventEquipment(GLOBEX, "TV", 1)));
  }

  @AfterEach
  void clearCaller() {
    CallerContextTestSupport.reset();
    CallerContextTestSupport.clearTenant();
  }

  @Test
  void anUntouchedDayHasEverythingFree() {
    assertThat(available("TV")).isEqualTo(2);
    assertThat(available("Mic")).isEqualTo(1);
  }

  @Test
  void aRequestHoldsItsGearFromTheMomentItIsRaised() {
    book(ACME, FAIR, EventRequestStatus.SUBMITTED, "TV", 2);

    assertThat(available("TV")).isZero();
    // and the rest of the pool is untouched
    assertThat(available("Speaker")).isEqualTo(2);
  }

  /** The whole point: the second booking of the same day is refused. */
  @Test
  void theSameGearCannotBeBookedTwiceOnOneDay() {
    book(ACME, FAIR, EventRequestStatus.SUBMITTED, "TV", 2);

    assertThatThrownBy(() -> service.requireAvailable(ACME, FAIR, List.of(line("TV", 1))))
        .isInstanceOf(EventEquipmentUnavailableException.class)
        .hasMessageContaining("only 0 of 2 free");
  }

  @Test
  void anotherDayIsUnaffected() {
    book(ACME, FAIR, EventRequestStatus.SUBMITTED, "TV", 2);

    assertThat(service.availability(ACME, OTHER_DAY))
        .filteredOn(a -> a.itemType().equals("TV"))
        .singleElement()
        .extracting(Availability::available)
        .isEqualTo(2L);
  }

  @Test
  void aDeniedRequestGivesItsGearBack() {
    book(ACME, FAIR, EventRequestStatus.DENIED, "TV", 2);

    assertThat(available("TV")).isEqualTo(2);
    assertThatCode(() -> service.requireAvailable(ACME, FAIR, List.of(line("TV", 2))))
        .doesNotThrowAnyException();
  }

  @Test
  void aClosedRequestGivesItsGearBackToo() {
    book(ACME, FAIR, EventRequestStatus.CLOSED, "TV", 2);

    assertThat(available("TV")).isEqualTo(2);
  }

  @Test
  void gearOutOnAFulfilledRequestIsStillGone() {
    book(ACME, FAIR, EventRequestStatus.FULFILLED, "TV", 1);

    assertThat(available("TV")).isEqualTo(1);
  }

  /** Two lines of one TV each are two TVs, not one - so they are summed before the comparison. */
  @Test
  void linesOnOneRequestAreSummedBeforeChecking() {
    assertThatThrownBy(
            () -> service.requireAvailable(ACME, FAIR, List.of(line("Mic", 1), line("Mic", 1))))
        .isInstanceOf(EventEquipmentUnavailableException.class)
        .hasMessageContaining("asked for 2 x Mic");
  }

  @Test
  void askingForSomethingTheClientDoesNotLendIsRefusedByName() {
    assertThatThrownBy(() -> service.requireAvailable(ACME, FAIR, List.of(line("Podium", 1))))
        .isInstanceOf(EventEquipmentUnavailableException.class)
        .hasMessageContaining("Podium is not something this client lends");
  }

  /** Every failing line is named at once, so the form can be fixed in one pass. */
  @Test
  void everyProblemIsReportedTogether() {
    book(ACME, FAIR, EventRequestStatus.APPROVED, "TV", 2);

    assertThatThrownBy(
            () -> service.requireAvailable(ACME, FAIR, List.of(line("TV", 1), line("Podium", 1))))
        .isInstanceOf(EventEquipmentUnavailableException.class)
        .hasMessageContaining("TV")
        .hasMessageContaining("Podium");
  }

  @Test
  void anotherTenantsBookingsDoNotConsumeThisOnesPool() {
    book(GLOBEX, FAIR, EventRequestStatus.SUBMITTED, "TV", 1);

    assertThat(available("TV")).isEqualTo(2);
  }

  @Test
  void itemTypesMatchWithoutRegardToCase() {
    book(ACME, FAIR, EventRequestStatus.SUBMITTED, "tv", 2);

    assertThat(available("TV")).isZero();
  }

  /**
   * Lowering the pool below what is already booked floors availability at zero rather than going
   * negative - gear gets written off, and next Tuesday's agreed bookings do not evaporate with it.
   */
  @Test
  void shrinkingThePoolBelowWhatIsBookedFloorsAtZero() {
    book(ACME, FAIR, EventRequestStatus.APPROVED, "TV", 2);
    EventEquipment tv = equipment.findByClientIdAndItemTypeIgnoreCase(ACME, "TV").orElseThrow();
    tv.setQuantity(1);
    equipment.save(tv);

    assertThat(available("TV")).isZero();
  }

  private long available(String itemType) {
    return service.availability(ACME, FAIR).stream()
        .filter(a -> a.itemType().equalsIgnoreCase(itemType))
        .findFirst()
        .orElseThrow()
        .available();
  }

  private void book(
      long clientId, LocalDate date, EventRequestStatus status, String itemType, int quantity) {
    EventRequest request =
        new EventRequest(clientId, "Career Fair", date, null, null, "dana@acme.example", 1L);
    request.addLine(new EventRequestLine(itemType, quantity, null));
    if (status == EventRequestStatus.DENIED) {
      request.decide(EventRequestStatus.DENIED, "poc@acme.example", null);
    } else if (status != EventRequestStatus.SUBMITTED) {
      request.decide(EventRequestStatus.APPROVED, "poc@acme.example", null);
      if (status == EventRequestStatus.FULFILLED || status == EventRequestStatus.CLOSED) {
        request.markFulfilled();
      }
      if (status == EventRequestStatus.CLOSED) {
        request.close();
      }
    }
    requests.save(request);
  }

  private static LineRequest line(String itemType, int quantity) {
    return new LineRequest(itemType, quantity, null);
  }
}
