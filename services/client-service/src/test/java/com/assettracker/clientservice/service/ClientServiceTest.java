package com.assettracker.clientservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.assettracker.clientservice.entity.Client;
import com.assettracker.clientservice.entity.ClientStatus;
import com.assettracker.clientservice.repository.ClientRepository;
import com.assettracker.clientservice.web.dto.CreateClientRequest;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ClientServiceTest {

  @Mock ClientRepository repository;
  @InjectMocks ClientService service;

  @Test
  void createRejectsATakenSlug() {
    when(repository.existsBySlug("acme")).thenReturn(true);
    assertThatThrownBy(() -> service.create(new CreateClientRequest("Acme", "acme")))
        .isInstanceOf(SlugTakenException.class);
  }

  @Test
  void newClientsAreActive() {
    when(repository.existsBySlug("globex")).thenReturn(false);
    when(repository.save(any(Client.class))).thenAnswer(inv -> inv.getArgument(0));
    Client c = service.create(new CreateClientRequest("Globex", "globex"));
    assertThat(c.getStatus()).isEqualTo(ClientStatus.ACTIVE);
  }

  @Test
  void getBySlugThrowsWhenMissing() {
    when(repository.findBySlug("nope")).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.getBySlug("nope")).isInstanceOf(ClientNotFoundException.class);
  }
}
