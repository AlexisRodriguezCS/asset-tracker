package com.assettracker.clientservice.service;

import com.assettracker.clientservice.entity.Client;
import com.assettracker.clientservice.repository.ClientRepository;
import com.assettracker.clientservice.web.dto.CreateClientRequest;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Business operations for clients (tenants). */
@Service
public class ClientService {

  private final ClientRepository repository;

  public ClientService(ClientRepository repository) {
    this.repository = repository;
  }

  @Transactional
  public Client create(CreateClientRequest request) {
    if (repository.existsBySlug(request.slug())) {
      throw new SlugTakenException(request.slug());
    }
    return repository.save(new Client(request.name(), request.slug()));
  }

  @Transactional(readOnly = true)
  public List<Client> findAll() {
    return repository.findAll();
  }

  @Transactional(readOnly = true)
  public Client getById(Long id) {
    return repository.findById(id).orElseThrow(() -> notFound("id", String.valueOf(id)));
  }

  @Transactional(readOnly = true)
  public Client getBySlug(String slug) {
    return repository.findBySlug(slug).orElseThrow(() -> notFound("slug", slug));
  }

  private static ClientNotFoundException notFound(String field, String value) {
    return new ClientNotFoundException("No client with " + field + " '" + value + "'");
  }
}
