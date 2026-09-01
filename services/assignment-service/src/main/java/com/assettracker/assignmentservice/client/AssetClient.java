package com.assettracker.assignmentservice.client;

import com.assettracker.assignmentservice.service.AssetNotMovableException;
import com.assettracker.assignmentservice.service.AssetUnavailableException;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClient.Builder;

/**
 * Talks to asset-service. The guarded transitions there are what give the orchestrator its failure
 * paths: a 409 becomes {@link AssetUnavailableException}, a 422 {@link AssetNotMovableException}.
 * The acting tech's identity is forwarded as {@code X-User-Id} so asset-service's audit trail
 * attributes the change to a person, not to {@code system}.
 */
@Component
public class AssetClient {

  private static final String ACTOR_HEADER = "X-User-Id";

  private final RestClient client;

  public AssetClient(
      Builder loadBalancedRestClientBuilder, @Value("${downstream.asset-service}") String baseUrl) {
    this.client = loadBalancedRestClientBuilder.baseUrl(baseUrl).build();
  }

  /** Sets the asset's holder. Throws on 404 / 409 / 422. */
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

  /** Returns the asset to the stockroom. */
  public void returnToStock(Long assetId, String actor) {
    client
        .post()
        .uri("/assets/{id}/return", assetId)
        .header(ACTOR_HEADER, actor)
        .retrieve()
        .toBodilessEntity();
  }

  /** ids of the assets currently held by a person - the offboarding worklist. */
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

  /**
   * Every asset a client currently has deployed - used to seed custody history on a fresh stack.
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

  /** A currently-deployed asset and where it sits. */
  public record Deployed(Long assetId, String holderType, Long holderId) {}
}
