package com.assettracker.peopleservice.web;

import com.assettracker.peopleservice.entity.PersonStatus;
import com.assettracker.peopleservice.service.PersonService;
import com.assettracker.peopleservice.web.dto.AssignDeskRequest;
import com.assettracker.peopleservice.web.dto.CreatePersonRequest;
import com.assettracker.peopleservice.web.dto.PersonResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** REST endpoints for people. Lists are always scoped to a client; writes are audited. */
@RestController
@RequestMapping("/people")
public class PersonController {

  private static final String ACTOR = "X-User-Id";

  private final PersonService service;

  public PersonController(PersonService service) {
    this.service = service;
  }

  @PostMapping
  public ResponseEntity<PersonResponse> create(
      @Valid @RequestBody CreatePersonRequest request,
      @RequestHeader(value = ACTOR, defaultValue = "system") String actor) {
    PersonResponse body = PersonResponse.from(service.create(request, actor));
    return ResponseEntity.created(URI.create("/people/" + body.id())).body(body);
  }

  @GetMapping
  public List<PersonResponse> list(
      @RequestParam Long clientId, @RequestParam(required = false) PersonStatus status) {
    return service.list(clientId, status).stream().map(PersonResponse::from).toList();
  }

  @GetMapping("/{id}")
  public PersonResponse getById(@PathVariable Long id) {
    return PersonResponse.from(service.getById(id));
  }

  @PostMapping("/{id}/offboarding")
  public PersonResponse beginOffboarding(
      @PathVariable Long id, @RequestHeader(value = ACTOR, defaultValue = "system") String actor) {
    return PersonResponse.from(service.beginOffboarding(id, actor));
  }

  @PostMapping("/{id}/departed")
  public PersonResponse markDeparted(
      @PathVariable Long id, @RequestHeader(value = ACTOR, defaultValue = "system") String actor) {
    return PersonResponse.from(service.markDeparted(id, actor));
  }

  @PostMapping("/{id}/desk")
  public PersonResponse assignDesk(
      @PathVariable Long id,
      @RequestBody AssignDeskRequest request,
      @RequestHeader(value = ACTOR, defaultValue = "system") String actor) {
    return PersonResponse.from(service.assignDesk(id, request.deskId(), actor));
  }
}
