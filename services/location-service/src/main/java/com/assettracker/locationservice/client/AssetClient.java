package com.assettracker.locationservice.client;

import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClient.Builder;

/**
 * Read-only view of asset-service. location-service needs one thing from it: whether anything is
 * currently sitting on a location, so a desk cannot be deleted out from under a monitor.
 *
 * <p>The same shape as people-service's client, and for the same reason - the service that owns the
 * rule has to ask the service that owns the facts.
 */
@Component
public class AssetClient {

  private final RestClient client;

  public AssetClient(
      Builder loadBalancedRestClientBuilder, @Value("${downstream.asset-service}") String baseUrl) {
    this.client = loadBalancedRestClientBuilder.baseUrl(baseUrl).build();
  }

  /**
   * Ids of the assets currently placed at a location. Propagates a failure to reach asset-service
   * rather than returning empty - "couldn't verify" must not read as "nothing is there", or an
   * outage would start letting deletions through.
   */
  @SuppressWarnings("unchecked")
  public List<Long> assetIdsPlacedAt(Long clientId, Long locationId) {
    List<Map<String, Object>> rows =
        client
            .get()
            .uri(
                uri ->
                    uri.path("/assets")
                        .queryParam("clientId", clientId)
                        .queryParam("holderType", "LOCATION")
                        .queryParam("holderId", locationId)
                        .build())
            .retrieve()
            .body(List.class);
    return rows == null
        ? List.of()
        : rows.stream().map(r -> ((Number) r.get("id")).longValue()).toList();
  }
}
