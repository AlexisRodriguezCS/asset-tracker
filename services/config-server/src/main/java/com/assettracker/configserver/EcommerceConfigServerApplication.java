package com.assettracker.configserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.config.server.EnableConfigServer;

/** Spring Cloud Config Server for the eCommerce platform. */
@SpringBootApplication
@EnableConfigServer
public class EcommerceConfigServerApplication {
  /**
   * Main entry point for the Spring Boot application.
   *
   * @param args command-line arguments
   */
  public static void main(String[] args) {
    SpringApplication.run(EcommerceConfigServerApplication.class, args);
  }
}
