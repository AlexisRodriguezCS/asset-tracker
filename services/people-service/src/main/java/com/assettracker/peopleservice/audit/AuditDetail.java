package com.assettracker.peopleservice.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builds the JSON stored in an audit row's {@code detail} column. Using Jackson (rather than string
 * concatenation) keeps values that contain quotes, braces or newlines from producing broken JSON.
 */
public final class AuditDetail {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private AuditDetail() {}

  /**
   * Serialises alternating {@code key, value} pairs to a JSON object, preserving order. Null values
   * are kept (serialised as JSON {@code null}).
   */
  public static String of(Object... keyValuePairs) {
    if (keyValuePairs.length % 2 != 0) {
      throw new IllegalArgumentException("expected an even number of key/value arguments");
    }
    Map<String, Object> fields = new LinkedHashMap<>();
    for (int i = 0; i < keyValuePairs.length; i += 2) {
      fields.put(String.valueOf(keyValuePairs[i]), keyValuePairs[i + 1]);
    }
    try {
      return MAPPER.writeValueAsString(fields);
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("un-serialisable audit detail", e);
    }
  }
}
