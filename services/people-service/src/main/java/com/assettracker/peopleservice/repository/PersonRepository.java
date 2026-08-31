package com.assettracker.peopleservice.repository;

import com.assettracker.peopleservice.entity.Person;
import com.assettracker.peopleservice.entity.PersonStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PersonRepository extends JpaRepository<Person, Long> {

  List<Person> findByClientIdOrderByFullNameAsc(Long clientId);

  List<Person> findByClientIdAndStatus(Long clientId, PersonStatus status);

  Optional<Person> findByClientIdAndEmailIgnoreCase(Long clientId, String email);

  boolean existsByClientIdAndEmailIgnoreCase(Long clientId, String email);
}
