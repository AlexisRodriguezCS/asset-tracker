package com.assettracker.locationservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.assettracker.locationservice.entity.Location;
import com.assettracker.locationservice.entity.LocationKind;
import com.assettracker.locationservice.repository.LocationRepository;
import com.assettracker.locationservice.web.dto.CreateLocationRequest;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LocationServiceTest {

  @Mock LocationRepository repository;
  @InjectMocks LocationService service;

  @Test
  void createRejectsATakenQrTag() {
    when(repository.existsByQrTag("ACME-D-001")).thenReturn(true);
    CreateLocationRequest req =
        new CreateLocationRequest(1L, LocationKind.DESK, "Desk 001", "HQ", "2", "ACME-D-001");
    assertThatThrownBy(() -> service.create(req)).isInstanceOf(QrTagTakenException.class);
  }

  @Test
  void createKeepsBuildingAndFloor() {
    when(repository.existsByQrTag(any())).thenReturn(false);
    when(repository.save(any(Location.class))).thenAnswer(inv -> inv.getArgument(0));
    Location l =
        service.create(
            new CreateLocationRequest(1L, LocationKind.DESK, "Desk 002", "HQ", "3", "ACME-D-002"));
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
