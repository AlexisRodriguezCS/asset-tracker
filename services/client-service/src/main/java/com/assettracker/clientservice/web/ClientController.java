package com.assettracker.clientservice.web;

import com.assettracker.clientservice.service.ClientService;
import com.assettracker.clientservice.web.dto.ClientResponse;
import com.assettracker.clientservice.web.dto.CreateClientRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** REST endpoints for clients (tenants). */
@RestController
@RequestMapping("/clients")
public class ClientController {

  private final ClientService service;

  public ClientController(ClientService service) {
    this.service = service;
  }

  @PostMapping
  public ResponseEntity<ClientResponse> create(@Valid @RequestBody CreateClientRequest request) {
    ClientResponse body = ClientResponse.from(service.create(request));
    return ResponseEntity.created(URI.create("/clients/" + body.id())).body(body);
  }

  @GetMapping
  public List<ClientResponse> list() {
    return service.findAll().stream().map(ClientResponse::from).toList();
  }

  @GetMapping("/{id}")
  public ClientResponse getById(@PathVariable Long id) {
    return ClientResponse.from(service.getById(id));
  }

  @GetMapping("/by-slug")
  public ClientResponse getBySlug(@RequestParam String slug) {
    return ClientResponse.from(service.getBySlug(slug));
  }
}
