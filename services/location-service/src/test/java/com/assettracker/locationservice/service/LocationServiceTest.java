package com.assettracker.locationservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.assettracker.locationservice.audit.AuditService;
import com.assettracker.locationservice.client.AssetClient;
import com.assettracker.locationservice.entity.Location;
import com.assettracker.locationservice.entity.LocationKind;
import com.assettracker.locationservice.repository.LocationRepository;
import com.assettracker.locationservice.web.CallerContext;
import com.assettracker.locationservice.web.dto.CreateLocationRequest;
import com.assettracker.locationservice.web.dto.UpdateLocationRequest;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LocationServiceTest {

  // Exercised directly, with no request to carry a token, so the caller is stated here. An absent
  // role is a denial now rather than an assumed internal call.
  @BeforeEach
  void signIn() {
    CallerContext.set("ADMIN", null);
  }

  @AfterEach
  void signOut() {
    CallerContext.clear();
  }

  @Mock LocationRepository repository;
  @Mock AuditService audit;
  @Mock AssetClient assetClient;

  @InjectMocks LocationService service;

  @Test
  void updateChangesOnlyWhatWasSent() {
    Location d = new Location(1L, LocationKind.DESK, "Desk 001", "ACME-D-001");
    d.setBuilding("HQ");
    when(repository.findById(4L)).thenReturn(Optional.of(d));

    Location updated =
        service.update(4L, new UpdateLocationRequest("Desk 1A", null, null, null), "tech@acme");

    assertThat(updated.getLabel()).isEqualTo("Desk 1A");
    assertThat(updated.getQrTag()).isEqualTo("ACME-D-001");
    assertThat(updated.getBuilding()).isEqualTo("HQ");
  }

  /** The tag is what a phone reads off the wall, so it stays unique the way creation demands. */
  @Test
  void updateRefusesATagSomewhereElseHas() {
    Location d = new Location(1L, LocationKind.DESK, "Desk 001", "ACME-D-001");
    when(repository.findById(4L)).thenReturn(Optional.of(d));
    when(repository.existsByQrTag("ACME-D-002")).thenReturn(true);

    assertThatThrownBy(
            () ->
                service.update(
                    4L, new UpdateLocationRequest(null, "ACME-D-002", null, null), "tech@acme"))
        .isInstanceOf(QrTagTakenException.class);
  }

  /** Deleting a desk out from under a monitor would leave the monitor pointing at nothing. */
  @Test
  void deleteIsRefusedWhileSomethingIsOnIt() {
    Location d = new Location(1L, LocationKind.DESK, "Desk 001", "ACME-D-001");
    when(repository.findById(4L)).thenReturn(Optional.of(d));
    when(assetClient.assetIdsPlacedAt(1L, null)).thenReturn(List.of(7L, 8L));

    assertThatThrownBy(() -> service.delete(4L, "tech@acme"))
        .isInstanceOf(LocationOccupiedException.class);
    verify(repository, never()).delete(any());
  }

  @Test
  void anEmptyLocationIsDeletedAndAudited() {
    Location d = new Location(1L, LocationKind.DESK, "Desk 001", "ACME-D-001");
    when(repository.findById(4L)).thenReturn(Optional.of(d));
    when(assetClient.assetIdsPlacedAt(1L, null)).thenReturn(List.of());

    service.delete(4L, "tech@acme");

    verify(repository).delete(d);
    verify(audit)
        .record(eq(1L), eq("tech@acme"), eq("LOCATION_DELETED"), any(), anyString(), any());
  }

  @Test
  void createRejectsATakenQrTag() {
    when(repository.existsByQrTag("ACME-D-001")).thenReturn(true);
    CreateLocationRequest req =
        new CreateLocationRequest(1L, LocationKind.DESK, "Desk 001", "HQ", "2", "ACME-D-001");
    assertThatThrownBy(() -> service.create(req, "tech@acme.example"))
        .isInstanceOf(QrTagTakenException.class);
  }

  @Test
  void createKeepsBuildingAndFloor() {
    when(repository.existsByQrTag(any())).thenReturn(false);
    when(repository.save(any(Location.class))).thenAnswer(inv -> inv.getArgument(0));
    Location l =
        service.create(
            new CreateLocationRequest(1L, LocationKind.DESK, "Desk 002", "HQ", "3", "ACME-D-002"),
            "tech@acme.example");
    assertThat(l.getBuilding()).isEqualTo("HQ");
    assertThat(l.getFloor()).isEqualTo("3");
  }

  @Test
  void getByQrTagResolvesAScan() {
    Location desk = new Location(1L, LocationKind.DESK, "Desk 007", "ACME-D-007");
    when(repository.findByQrTag("ACME-D-007")).thenReturn(Optional.of(desk));
    assertThat(service.getByQrTag("ACME-D-007").getLabel()).isEqualTo("Desk 007");
  }

  @Test
  void unknownQrTagThrows() {
    when(repository.findByQrTag("ACME-D-999")).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.getByQrTag("ACME-D-999"))
        .isInstanceOf(LocationNotFoundException.class);
  }
}
