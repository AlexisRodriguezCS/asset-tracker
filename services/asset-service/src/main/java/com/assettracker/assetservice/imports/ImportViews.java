package com.assettracker.assetservice.imports;

import java.util.List;
import java.util.Map;

/** Response shapes for the spreadsheet-import endpoints. */
public final class ImportViews {

  private ImportViews() {}

  /** What {@code POST /assets/import/analyze} returns so the wizard opens pre-filled. */
  public record AnalyzeResult(
      List<String> headers,
      ColumnMapping suggested,
      List<Map<String, String>> sampleRows,
      List<ProfileView> profiles) {}

  public record ProfileView(Long id, String name, ColumnMapping mapping) {}

  /** One parsed row, its resolved values, and why it will or won't import. */
  public record RowOutcome(
      int line, Map<String, String> values, String action, List<String> errors) {

    public static final String CREATE = "create";
    public static final String UPDATE = "update";
    public static final String SKIP = "skip";
  }

  /** Dry run: what a commit would do, row by row. */
  public record ImportPreview(
      int total, int willCreate, int willUpdate, int invalid, List<RowOutcome> rows) {}

  /** The outcome of an actual import. */
  public record ImportResult(int created, int updated, int skipped, List<RowOutcome> errors) {}
}
