package com.assettracker.assetservice.imports;

import java.util.List;
import java.util.Map;

/**
 * How a client's spreadsheet columns line up with our asset fields.
 *
 * @param fields our field name -&gt; the client's column header (only mapped fields appear)
 * @param attributeColumns client column headers to keep verbatim under {@code asset.attributes}
 */
public record ColumnMapping(Map<String, String> fields, List<String> attributeColumns) {

  public ColumnMapping {
    fields = fields == null ? Map.of() : Map.copyOf(fields);
    attributeColumns = attributeColumns == null ? List.of() : List.copyOf(attributeColumns);
  }

  /** The fields a row must supply for us to accept it. */
  public static final List<String> REQUIRED = List.of("assetTag", "type");

  /** Every field the importer understands, in display order. */
  public static final List<String> SUPPORTED =
      List.of(
          "assetTag",
          "type",
          "serialNumber",
          "make",
          "model",
          "condition",
          "purchaseDate",
          "warrantyEndsOn",
          "deployedOn",
          "notes");
}
