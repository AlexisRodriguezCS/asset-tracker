package com.assettracker.clientservice.repository;

import com.assettracker.clientservice.entity.Client;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClientRepository extends JpaRepository<Client, Long> {

  Optional<Client> findBySlug(String slug);

  boolean existsBySlug(String slug);
}
