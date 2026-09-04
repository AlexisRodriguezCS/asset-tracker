package com.assettracker.assetservice.imports;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Best-effort auto-mapping of a client's headers onto our fields by normalised-substring match, so
 * the wizard opens pre-filled. Every guess is editable in the UI.
 */
final class MappingGuesser {

  private MappingGuesser() {}

  /** our field -> the header fragments that imply it, most specific first. */
  private static final Map<String, List<String>> SYNONYMS = new LinkedHashMap<>();

  static {
    SYNONYMS.put(
        "assetTag",
        List.of(
            "assettag",
            "assetid",
            "assetnumber",
            "assetno",
            "tagnumber",
            "inventorytag",
            "barcode",
            "tag"));
    SYNONYMS.put("serialNumber", List.of("serialnumber", "serialno", "servicetag", "serial", "sn"));
    SYNONYMS.put("type", List.of("assettype", "devicetype", "category", "type", "class", "kind"));
    SYNONYMS.put("make", List.of("manufacturer", "vendor", "brand", "make"));
    SYNONYMS.put("model", List.of("modelnumber", "productname", "model", "device"));
    SYNONYMS.put("condition", List.of("condition", "grade", "state"));
    SYNONYMS.put(
        "purchaseDate",
        List.of(
            "purchasedate",
            "dateofpurchase",
            "acquisitiondate",
            "orderdate",
            "purchased",
            "acquired",
            "bought"));
    SYNONYMS.put(
        "warrantyEndsOn",
        List.of(
            "warrantyexpiration",
            "warrantyexpiry",
            "warrantyend",
            "warrantyends",
            "coverageend",
            "warranty"));
    SYNONYMS.put(
        "deployedOn",
        List.of(
            "deploymentdate",
            "inservicedate",
            "issuedate",
            "assigneddate",
            "deployedon",
            "deployed",
            "issued"));
    SYNONYMS.put("notes", List.of("notes", "comments", "remarks"));
  }

  static ColumnMapping guess(List<String> headers) {
    Map<String, String> fields = new LinkedHashMap<>();
    List<String> used = new ArrayList<>();

    for (Map.Entry<String, List<String>> entry : SYNONYMS.entrySet()) {
      for (String fragment : entry.getValue()) {
        String hit = firstHeaderContaining(headers, used, fragment);
        if (hit != null) {
          fields.put(entry.getKey(), hit);
          used.add(hit);
          break;
        }
      }
    }

    List<String> leftovers = new ArrayList<>();
    for (String h : headers) {
      if (!used.contains(h)) {
        leftovers.add(h);
      }
    }
    return new ColumnMapping(fields, leftovers);
  }

  private static String firstHeaderContaining(
      List<String> headers, List<String> alreadyUsed, String fragment) {
    for (String h : headers) {
      if (!alreadyUsed.contains(h) && normalise(h).contains(fragment)) {
        return h;
      }
    }
    return null;
  }

  private static String normalise(String s) {
    return s == null ? "" : s.toLowerCase().replaceAll("[^a-z0-9]", "");
  }
}
