package com.assettracker.assignmentservice.repository;

import com.assettracker.assignmentservice.entity.Assignment;
import com.assettracker.assignmentservice.entity.HolderType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssignmentRepository extends JpaRepository<Assignment, Long> {

  Optional<Assignment> findByAssetIdAndReturnedAtIsNull(Long assetId);

  List<Assignment> findByClientIdOrderByCheckedOutAtDesc(Long clientId);

  List<Assignment> findByAssetIdOrderByCheckedOutAtDesc(Long assetId);

  List<Assignment> findByHolderTypeAndHolderIdAndReturnedAtIsNull(
      HolderType holderType, Long holderId);
}
