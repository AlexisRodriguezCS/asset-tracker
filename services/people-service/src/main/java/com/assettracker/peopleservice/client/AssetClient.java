package com.assettracker.peopleservice.client;

import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClient.Builder;

/**
 * Read-only view of asset-service. people-service only needs one thing from it: what a person
 * currently holds, so a departing employee can't be closed out while gear is still on them.
 */
@Component
public class AssetClient {

  private final RestClient client;

  public AssetClient(
      Builder loadBalancedRestClientBuilder, @Value("${downstream.asset-service}") String baseUrl) {
    this.client = loadBalancedRestClientBuilder.baseUrl(baseUrl).build();
  }

  /**
   * Ids of the assets currently assigned to a person. Propagates a failure to reach asset-service
   * rather than returning empty - "couldn't verify" must not read as "holds nothing".
   */
  @SuppressWarnings("unchecked")
  public List<Long> assetIdsHeldBy(Long clientId, Long personId) {
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
}
