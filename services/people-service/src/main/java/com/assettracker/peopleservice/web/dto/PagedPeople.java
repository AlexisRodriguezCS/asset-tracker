package com.assettracker.peopleservice.web.dto;

import com.assettracker.peopleservice.entity.Person;
import java.util.List;
import org.springframework.data.domain.Page;

/**
 * One page of the directory, plus what a caller needs to render pagination without a second call.
 *
 * <p>The same shape asset-service already uses, deliberately: two services answering the same
 * question two different ways is a cost the console pays forever.
 */
public record PagedPeople(
    List<PersonResponse> items, long total, int page, int size, int totalPages) {

  public static PagedPeople from(Page<Person> page) {
    return new PagedPeople(
        page.getContent().stream().map(PersonResponse::from).toList(),
        page.getTotalElements(),
        page.getNumber(),
        page.getSize(),
        page.getTotalPages());
  }
}
