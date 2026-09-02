package com.assettracker.assetservice.type;

import com.assettracker.assetservice.entity.AssetType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssetTypeRepository extends JpaRepository<AssetType, Long> {

  List<AssetType> findByClientIdOrderByName(Long clientId);

  boolean existsByClientIdAndNameIgnoreCase(Long clientId, String name);

  Optional<AssetType> findByClientIdAndNameIgnoreCase(Long clientId, String name);
}
