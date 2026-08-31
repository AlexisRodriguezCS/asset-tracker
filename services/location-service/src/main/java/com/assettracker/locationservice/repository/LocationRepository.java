package com.assettracker.locationservice.repository;

import com.assettracker.locationservice.entity.Location;
import com.assettracker.locationservice.entity.LocationKind;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LocationRepository extends JpaRepository<Location, Long> {

  List<Location> findByClientIdOrderByLabelAsc(Long clientId);

  List<Location> findByClientIdAndKind(Long clientId, LocationKind kind);

  Optional<Location> findByQrTag(String qrTag);

  boolean existsByQrTag(String qrTag);
}
