package com.assettracker.assetservice.imports;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImportProfileRepository extends JpaRepository<ImportProfile, Long> {

  List<ImportProfile> findByClientIdOrderByName(Long clientId);

  Optional<ImportProfile> findByClientIdAndNameIgnoreCase(Long clientId, String name);
}
