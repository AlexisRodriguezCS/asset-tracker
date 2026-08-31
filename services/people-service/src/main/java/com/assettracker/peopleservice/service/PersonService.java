package com.assettracker.peopleservice.service;

import com.assettracker.peopleservice.entity.Person;
import com.assettracker.peopleservice.entity.PersonStatus;
import com.assettracker.peopleservice.repository.PersonRepository;
import com.assettracker.peopleservice.web.dto.CreatePersonRequest;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Business operations for people (employees of a client). */
@Service
public class PersonService {

  private final PersonRepository repository;

  public PersonService(PersonRepository repository) {
    this.repository = repository;
  }

  @Transactional
  public Person create(CreatePersonRequest request) {
    if (repository.existsByClientIdAndEmailIgnoreCase(request.clientId(), request.email())) {
      throw new EmailTakenException(request.email());
    }
    Person person =
        new Person(request.clientId(), request.fullName(), request.email(), request.department());
    if (request.deskId() != null) {
      person.setDeskId(request.deskId());
    }
    return repository.save(person);
  }

  @Transactional(readOnly = true)
  public List<Person> list(Long clientId, PersonStatus status) {
    return status == null
        ? repository.findByClientIdOrderByFullNameAsc(clientId)
        : repository.findByClientIdAndStatus(clientId, status);
  }

  @Transactional(readOnly = true)
  public Person getById(Long id) {
    return repository.findById(id).orElseThrow(() -> notFound(id));
  }

  /** Marks a person as offboarding - the signal for HR to collect their assets. */
  @Transactional
  public Person beginOffboarding(Long id) {
    Person person = getById(id);
    person.setStatus(PersonStatus.OFFBOARDING);
    return person;
  }

  @Transactional
  public Person markDeparted(Long id) {
    Person person = getById(id);
    person.setStatus(PersonStatus.DEPARTED);
    return person;
  }

  @Transactional
  public Person assignDesk(Long id, Long deskId) {
    Person person = getById(id);
    person.setDeskId(deskId);
    return person;
  }

  private static PersonNotFoundException notFound(Long id) {
    return new PersonNotFoundException("No person with id '" + id + "'");
  }
}
