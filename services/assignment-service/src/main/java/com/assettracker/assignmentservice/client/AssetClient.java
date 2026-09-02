package com.assettracker.assignmentservice.client;

import com.assettracker.assignmentservice.service.AssetNotMovableException;
import com.assettracker.assignmentservice.service.AssetServiceUnavailableException;
import com.assettracker.assignmentservice.service.AssetUnavailableException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClient.Builder;

/**
 * Talks to asset-service. Its guarded transitions give the orchestrator its failure paths: a 409
 * becomes {@link AssetUnavailableException}, a 422 {@link AssetNotMovableException}. Those are
 * business outcomes and pass straight through - Resilience4j is configured to ignore them. A
 * transport failure (timeout, connection refused, 5xx) is retried a few times and, if it keeps
 * failing, opens the {@code asset-service} circuit and surfaces as {@link
 * AssetServiceUnavailableException} (HTTP 503) instead of a stuck request.
 */
@Component
public class AssetClient {

  private static final String CB = "asset-service";
  private static final String ACTOR_HEADER = "X-User-Id";

  private final RestClient client;

  public AssetClient(
      Builder loadBalancedRestClientBuilder, @Value("${downstream.asset-service}") String baseUrl) {
    this.client = loadBalancedRestClientBuilder.baseUrl(baseUrl).build();
  }

  /** Sets the asset's holder. Throws on 404 / 409 / 422; 503 when asset-service is down. */
  @Retry(name = CB)
  @CircuitBreaker(name = CB, fallbackMethod = "assignFallback")
  public void assign(Long assetId, String holderType, Long holderId, String actor) {
    client
        .post()
        .uri("/assets/{id}/assign", assetId)
        .header(ACTOR_HEADER, actor)
        .body(Map.of("holderType", holderType, "holderId", holderId))
        .retrieve()
        .onStatus(
            status -> status.isSameCodeAs(HttpStatus.CONFLICT),
            (req, res) -> {
              throw new AssetUnavailableException(assetId);
            })
        .onStatus(
            status -> status.isSameCodeAs(HttpStatus.UNPROCESSABLE_ENTITY),
            (req, res) -> {
              throw new AssetNotMovableException(assetId);
            })
        .toBodilessEntity();
  }

  private void assignFallback(
      Long assetId, String holderType, Long holderId, String actor, Throwable t) {
    throw translate(assetId, t);
  }

  /** Returns the asset to the stockroom. */
  @Retry(name = CB)
  @CircuitBreaker(name = CB, fallbackMethod = "returnFallback")
  public void returnToStock(Long assetId, String actor) {
    client
        .post()
        .uri("/assets/{id}/return", assetId)
        .header(ACTOR_HEADER, actor)
        .retrieve()
        .toBodilessEntity();
  }

  private void returnFallback(Long assetId, String actor, Throwable t) {
    throw translate(assetId, t);
  }

  /** ids of the assets currently held by a person - the offboarding worklist. */
  @Retry(name = CB)
  @CircuitBreaker(name = CB, fallbackMethod = "heldByFallback")
  @SuppressWarnings("unchecked")
  public List<Long> assetsHeldByPerson(Long clientId, Long personId) {
    List<Map<String, Object>> rows =
        client
            .get()
            .uri(
                uri ->
                    uri.path("/assets")
                        .queryParam("clientId", clientId)
                        .queryParam("holderType", "PERSON")
                        .queryParam("holderId", personId)
                        .build())
            .retrieve()
            .body(List.class);
    return rows == null
        ? List.of()
        : rows.stream().map(r -> ((Number) r.get("id")).longValue()).toList();
  }

  private List<Long> heldByFallback(Long clientId, Long personId, Throwable t) {
    throw new AssetServiceUnavailableException(t);
  }

  /**
   * Every asset a client currently has deployed - used to seed custody history on a fresh stack.
   * Deliberately outside the circuit breaker: {@code AssignmentSeeder} has its own long retry loop.
   */
  @SuppressWarnings("unchecked")
  public List<Deployed> deployedAssets(Long clientId) {
    List<Map<String, Object>> rows =
        client
            .get()
            .uri(
                uri ->
                    uri.path("/assets")
                        .queryParam("clientId", clientId)
                        .queryParam("status", "ASSIGNED")
                        .build())
            .retrieve()
            .body(List.class);
    if (rows == null) {
      return List.of();
    }
    return rows.stream()
        .map(
            r ->
                new Deployed(
                    ((Number) r.get("id")).longValue(),
                    (String) r.get("holderType"),
                    ((Number) r.get("holderId")).longValue()))
        .toList();
  }

  /** Re-surface a 409 / 422 business outcome; otherwise it's an outage -> 503. */
  private static RuntimeException translate(Long assetId, Throwable t) {
    if (t instanceof AssetUnavailableException || t instanceof AssetNotMovableException) {
      return (RuntimeException) t;
    }
    return new AssetServiceUnavailableException(assetId, t);
  }

  /** A currently-deployed asset and where it sits. */
  public record Deployed(Long assetId, String holderType, Long holderId) {}
}
