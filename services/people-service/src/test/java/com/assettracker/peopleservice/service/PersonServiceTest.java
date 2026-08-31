package com.assettracker.peopleservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.assettracker.peopleservice.audit.AuditService;
import com.assettracker.peopleservice.entity.Person;
import com.assettracker.peopleservice.entity.PersonStatus;
import com.assettracker.peopleservice.repository.PersonRepository;
import com.assettracker.peopleservice.web.dto.CreatePersonRequest;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PersonServiceTest {

  @Mock PersonRepository repository;
  @Mock AuditService audit;
  @InjectMocks PersonService service;

  @Test
  void createRejectsADuplicateEmailWithinTheClient() {
    when(repository.existsByClientIdAndEmailIgnoreCase(1L, "dana@acme.example")).thenReturn(true);
    CreatePersonRequest req = new CreatePersonRequest(1L, "Dana", "dana@acme.example", "Eng", null);
    assertThatThrownBy(() -> service.create(req, "tech@acme.example"))
        .isInstanceOf(EmailTakenException.class);
  }

  @Test
  void newPeopleStartActiveAndAreAudited() {
    when(repository.existsByClientIdAndEmailIgnoreCase(any(), any())).thenReturn(false);
    when(repository.save(any(Person.class))).thenAnswer(inv -> inv.getArgument(0));
    Person p =
        service.create(
            new CreatePersonRequest(1L, "Sam", "sam@acme.example", "Design", null),
            "tech@acme.example");
    assertThat(p.getStatus()).isEqualTo(PersonStatus.ACTIVE);
    verify(audit)
        .record(eq(1L), eq("tech@acme.example"), eq("PERSON_CREATED"), any(), anyString(), any());
  }

  @Test
  void beginOffboardingFlipsTheStatusAndAudits() {
    Person p = new Person(1L, "Leo", "leo@acme.example", "Support");
    when(repository.findById(3L)).thenReturn(Optional.of(p));
    assertThat(service.beginOffboarding(3L, "hr@acme.example").getStatus())
        .isEqualTo(PersonStatus.OFFBOARDING);
    verify(audit)
        .record(eq(1L), eq("hr@acme.example"), eq("PERSON_OFFBOARDING"), any(), anyString(), any());
  }

  @Test
  void assignDeskSetsAndClearsTheDesk() {
    Person p = new Person(1L, "Priya", "priya@acme.example", "Finance");
    when(repository.findById(4L)).thenReturn(Optional.of(p));
    assertThat(service.assignDesk(4L, 12L, "tech@acme.example").getDeskId()).isEqualTo(12L);
    assertThat(service.assignDesk(4L, null, "tech@acme.example").getDeskId()).isNull();
  }

  @Test
  void missingPersonThrows() {
    when(repository.findById(99L)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.getById(99L)).isInstanceOf(PersonNotFoundException.class);
  }
}
