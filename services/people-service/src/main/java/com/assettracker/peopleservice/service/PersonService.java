package com.assettracker.peopleservice.service;

import com.assettracker.peopleservice.audit.AuditDetail;
import com.assettracker.peopleservice.audit.AuditService;
import com.assettracker.peopleservice.client.AssetClient;
import com.assettracker.peopleservice.entity.Person;
import com.assettracker.peopleservice.entity.PersonStatus;
import com.assettracker.peopleservice.repository.PersonRepository;
import com.assettracker.peopleservice.web.CallerContext;
import com.assettracker.peopleservice.web.TenantContext;
import com.assettracker.peopleservice.web.dto.CreatePersonRequest;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Business operations for people (employees of a client). */
@Service
public class PersonService {

  private final PersonRepository repository;
  private final AuditService audit;
  private final AssetClient assetClient;

  public PersonService(PersonRepository repository, AuditService audit, AssetClient assetClient) {
    this.repository = repository;
    this.audit = audit;
    this.assetClient = assetClient;
  }

  @Transactional
  public Person create(CreatePersonRequest request, String actor) {
    TenantContext.requireAllowed(request.clientId());
    if (repository.existsByClientIdAndEmailIgnoreCase(request.clientId(), request.email())) {
      throw new EmailTakenException(request.email());
    }
    Person person =
        new Person(request.clientId(), request.fullName(), request.email(), request.department());
    if (request.deskId() != null) {
      person.setDeskId(request.deskId());
    }
    Person saved = repository.save(person);
    audit.record(
        saved.getClientId(),
        actor,
        "PERSON_CREATED",
        saved.getId(),
        "added " + saved.getFullName() + " <" + saved.getEmail() + ">",
        null);
    return saved;
  }

  @Transactional(readOnly = true)
  public List<Person> list(Long clientId, PersonStatus status) {
    TenantContext.requireAllowed(clientId);
    if (CallerContext.isSelfServiceUser()) {
      // an ordinary employee is not a directory browser - they see themselves, nobody else
      Long self = CallerContext.personId();
      return self == null
          ? List.of()
          : repository.findById(self).filter(p -> p.getClientId().equals(clientId)).stream()
              .toList();
    }
    return status == null
        ? repository.findByClientIdOrderByFullNameAsc(clientId)
        : repository.findByClientIdAndStatus(clientId, status);
  }

  @Transactional(readOnly = true)
  public Person getById(Long id) {
    Person person = repository.findById(id).orElseThrow(() -> notFound(id));
    if (!TenantContext.allows(person.getClientId())
        || (CallerContext.isSelfServiceUser()
            && !person.getId().equals(CallerContext.personId()))) {
      // 404 rather than 403 - whether an id exists is itself information the caller lacks
      throw notFound(id);
    }
    return person;
  }

  /** Marks a person as offboarding - the signal for HR to collect their assets. */
  @Transactional
  public Person beginOffboarding(Long id, String actor) {
    Person person = getById(id);
    TenantContext.requireAllowed(person.getClientId());
    person.setStatus(PersonStatus.OFFBOARDING);
    audit.record(
        person.getClientId(),
        actor,
        "PERSON_OFFBOARDING",
        person.getId(),
        "started offboarding " + person.getFullName(),
        null);
    return person;
  }

  /**
   * Closes a person out. Refused while any asset is still assigned to them - the offboarding sweep
   * has to collect their gear first, otherwise those rows would point at a departed holder.
   */
  @Transactional
  public Person markDeparted(Long id, String actor) {
    Person person = getById(id);
    TenantContext.requireAllowed(person.getClientId());
    List<Long> stillHeld = assetClient.assetIdsHeldBy(person.getClientId(), person.getId());
    if (!stillHeld.isEmpty()) {
      throw new PersonStillHoldsAssetsException(person.getId(), stillHeld);
    }
    person.setStatus(PersonStatus.DEPARTED);
    audit.record(
        person.getClientId(),
        actor,
        "PERSON_DEPARTED",
        person.getId(),
        "marked " + person.getFullName() + " departed",
        null);
    return person;
  }

  @Transactional
  public Person assignDesk(Long id, Long deskId, String actor) {
    Person person = getById(id);
    TenantContext.requireAllowed(person.getClientId());
    Long before = person.getDeskId();
    person.setDeskId(deskId);
    audit.record(
        person.getClientId(),
        actor,
        deskId == null ? "PERSON_DESK_CLEARED" : "PERSON_DESK_SET",
        person.getId(),
        (deskId == null
            ? "cleared " + person.getFullName() + "'s desk"
            : "set " + person.getFullName() + "'s desk to " + deskId),
        AuditDetail.of("before", before, "after", deskId));
    return person;
  }

  private static PersonNotFoundException notFound(Long id) {
    return new PersonNotFoundException("No person with id '" + id + "'");
  }
}
