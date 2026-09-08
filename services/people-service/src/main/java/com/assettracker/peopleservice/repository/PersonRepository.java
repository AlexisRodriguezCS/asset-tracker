package com.assettracker.peopleservice.repository;

import com.assettracker.peopleservice.entity.Person;
import com.assettracker.peopleservice.entity.PersonStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PersonRepository extends JpaRepository<Person, Long> {

  List<Person> findByClientIdOrderByFullNameAsc(Long clientId);

  List<Person> findByClientIdAndStatus(Long clientId, PersonStatus status);

  Page<Person> findByClientId(Long clientId, Pageable pageable);

  Page<Person> findByClientIdAndStatus(Long clientId, PersonStatus status, Pageable pageable);

  Optional<Person> findByClientIdAndEmailIgnoreCase(Long clientId, String email);

  long countByClientId(Long clientId);

  long countByClientIdAndStatus(Long clientId, PersonStatus status);

  long countByClientIdAndDeskIdIsNotNull(Long clientId);

  boolean existsByClientIdAndEmailIgnoreCase(Long clientId, String email);
}
