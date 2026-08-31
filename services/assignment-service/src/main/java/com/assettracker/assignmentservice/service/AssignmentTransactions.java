package com.assettracker.assignmentservice.service;

import com.assettracker.assignmentservice.entity.Assignment;
import com.assettracker.assignmentservice.entity.HolderType;
import com.assettracker.assignmentservice.repository.AssignmentRepository;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Each method is its own short transaction. The orchestration in {@link AssignmentService} is NOT
 * transactional - it spans HTTP calls to asset-service - so the history writes here must stand on
 * their own, exactly as in the e-commerce order flow this was reshaped from.
 */
@Component
public class AssignmentTransactions {

  private final AssignmentRepository repository;

  public AssignmentTransactions(AssignmentRepository repository) {
    this.repository = repository;
  }

  @Transactional
  public Assignment open(
      Long clientId,
      Long assetId,
      HolderType holderType,
      Long holderId,
      String actor,
      String note) {
    return repository.save(new Assignment(clientId, assetId, holderType, holderId, actor, note));
  }

  @Transactional
  public Assignment close(Long assetId, String actor) {
    Assignment open =
        repository
            .findByAssetIdAndReturnedAtIsNull(assetId)
            .orElseThrow(() -> new NoOpenAssignmentException(assetId));
    open.markReturned(actor);
    return open;
  }

  @Transactional(readOnly = true)
  public List<Assignment> openForPerson(Long personId) {
    return repository.findByHolderTypeAndHolderIdAndReturnedAtIsNull(HolderType.PERSON, personId);
  }

  @Transactional(readOnly = true)
  public Assignment getById(Long id) {
    return repository
        .findById(id)
        .orElseThrow(() -> new AssignmentNotFoundException("No assignment with id '" + id + "'"));
  }

  @Transactional(readOnly = true)
  public List<Assignment> byClient(Long clientId) {
    return repository.findByClientIdOrderByCheckedOutAtDesc(clientId);
  }

  @Transactional(readOnly = true)
  public List<Assignment> byAsset(Long assetId) {
    return repository.findByAssetIdOrderByCheckedOutAtDesc(assetId);
  }
}
