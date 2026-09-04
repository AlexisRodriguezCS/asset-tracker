package com.assettracker.assetservice.entity;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Persists a {@code Map<String,String>} as a JSON string in a plain {@code TEXT} column - portable
 * across H2 (dev) and Postgres (prod) without a JSONB type mapping. Used for an asset's free-form
 * {@code attributes} (spreadsheet columns we don't natively model).
 */
@Converter
public class StringMapConverter implements AttributeConverter<Map<String, String>, String> {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final TypeReference<LinkedHashMap<String, String>> TYPE = new TypeReference<>() {};

  @Override
  public String convertToDatabaseColumn(Map<String, String> attribute) {
    if (attribute == null || attribute.isEmpty()) {
      return null;
    }
    try {
      return MAPPER.writeValueAsString(attribute);
    } catch (Exception e) {
      throw new IllegalArgumentException("un-serialisable attributes", e);
    }
  }

  @Override
  public Map<String, String> convertToEntityAttribute(String dbData) {
    if (dbData == null || dbData.isBlank()) {
      return new LinkedHashMap<>();
    }
    try {
      return MAPPER.readValue(dbData, TYPE);
    } catch (Exception e) {
      return new LinkedHashMap<>();
    }
  }
}
