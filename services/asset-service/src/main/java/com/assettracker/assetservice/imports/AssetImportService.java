package com.assettracker.assetservice.imports;

import com.assettracker.assetservice.audit.AuditDetail;
import com.assettracker.assetservice.audit.AuditService;
import com.assettracker.assetservice.entity.Asset;
import com.assettracker.assetservice.entity.AssetCondition;
import com.assettracker.assetservice.entity.AssetType;
import com.assettracker.assetservice.imports.ImportViews.AnalyzeResult;
import com.assettracker.assetservice.imports.ImportViews.ImportPreview;
import com.assettracker.assetservice.imports.ImportViews.ImportResult;
import com.assettracker.assetservice.imports.ImportViews.ProfileView;
import com.assettracker.assetservice.imports.ImportViews.RowOutcome;
import com.assettracker.assetservice.repository.AssetRepository;
import com.assettracker.assetservice.type.AssetTypeRepository;
import com.assettracker.assetservice.web.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Imports assets from a client's own CSV. The client keeps their column names; a {@link
 * ColumnMapping} (auto-guessed, then edited in the wizard, optionally saved as a reusable profile)
 * ties them to our fields. Columns we don't model are kept verbatim under {@code asset.attributes}.
 * A re-upload updates matching rows (key: client + tag + type) rather than duplicating.
 */
@Service
public class AssetImportService {

  private static final int MAX_ROWS = 5000;
  private static final int MAX_PREVIEW_ROWS = 300;
  private static final int SAMPLE_ROWS = 5;

  private static final List<DateTimeFormatter> DATE_FORMATS =
      List.of(
          DateTimeFormatter.ISO_LOCAL_DATE,
          DateTimeFormatter.ofPattern("M/d/uuuu", Locale.ENGLISH),
          DateTimeFormatter.ofPattern("uuuu/MM/dd", Locale.ENGLISH),
          DateTimeFormatter.ofPattern("d-MMM-uuuu", Locale.ENGLISH));

  private static final CSVFormat CSV =
      CSVFormat.DEFAULT
          .builder()
          .setHeader()
          .setSkipHeaderRecord(true)
          .setIgnoreEmptyLines(true)
          .setTrim(true)
          .setIgnoreSurroundingSpaces(true)
          .build();

  private final AssetRepository assets;
  private final AssetTypeRepository types;
  private final ImportProfileRepository profileRepo;
  private final AuditService audit;
  private final ObjectMapper json = new ObjectMapper();

  public AssetImportService(
      AssetRepository assets,
      AssetTypeRepository types,
      ImportProfileRepository profileRepo,
      AuditService audit) {
    this.assets = assets;
    this.types = types;
    this.profileRepo = profileRepo;
    this.audit = audit;
  }

  // --- public API -------------------------------------------------------

  public AnalyzeResult analyze(Long clientId, byte[] csv) {
    TenantContext.requireAllowed(clientId);
    Parsed parsed = parse(csv);
    ColumnMapping suggested = MappingGuesser.guess(parsed.headers());

    List<Map<String, String>> sample = new ArrayList<>();
    for (CSVRecord r :
        parsed.records().subList(0, Math.min(SAMPLE_ROWS, parsed.records().size()))) {
      sample.add(r.toMap());
    }
    return new AnalyzeResult(parsed.headers(), suggested, sample, listProfiles(clientId));
  }

  public ImportPreview preview(
      Long clientId, byte[] csv, ColumnMapping mapping, boolean createMissingTypes) {
    TenantContext.requireAllowed(clientId);
    List<RowOutcome> outcomes = plan(clientId, parse(csv).records(), mapping, createMissingTypes);

    int create = 0;
    int update = 0;
    int invalid = 0;
    for (RowOutcome o : outcomes) {
      switch (o.action()) {
        case RowOutcome.CREATE -> create++;
        case RowOutcome.UPDATE -> update++;
        default -> invalid++;
      }
    }
    List<RowOutcome> shown = outcomes.subList(0, Math.min(MAX_PREVIEW_ROWS, outcomes.size()));
    return new ImportPreview(outcomes.size(), create, update, invalid, shown);
  }

  @Transactional
  public ImportResult commit(
      Long clientId,
      byte[] csv,
      ColumnMapping mapping,
      boolean createMissingTypes,
      String saveProfileAs,
      String actor) {
    TenantContext.requireAllowed(clientId);
    List<CSVRecord> records = parse(csv).records();
    List<RowOutcome> outcomes = plan(clientId, records, mapping, createMissingTypes);

    int created = 0;
    int updated = 0;
    List<RowOutcome> skipped = new ArrayList<>();

    for (int i = 0; i < outcomes.size(); i++) {
      RowOutcome outcome = outcomes.get(i);
      if (outcome.action().equals(RowOutcome.SKIP)) {
        skipped.add(outcome);
        continue;
      }
      Map<String, String> v = outcome.values();
      if (createMissingTypes && !typeExists(clientId, v.get("type"))) {
        types.save(new AssetType(clientId, v.get("type").trim()));
      }
      if (outcome.action().equals(RowOutcome.CREATE)) {
        assets.save(
            apply(
                new Asset(
                    clientId,
                    v.get("type"),
                    blankToEmpty(v.get("serialNumber")),
                    v.get("assetTag")),
                v));
        created++;
      } else {
        Asset existing =
            assets
                .findByClientIdAndAssetTagAndType(clientId, v.get("assetTag"), v.get("type"))
                .get(0);
        assets.save(apply(existing, v));
        updated++;
      }
    }

    if (saveProfileAs != null && !saveProfileAs.isBlank()) {
      saveProfile(clientId, saveProfileAs.trim(), mapping);
    }

    audit.record(
        clientId,
        actor,
        "ASSETS_IMPORTED",
        null,
        created + " created, " + updated + " updated, " + skipped.size() + " skipped",
        AuditDetail.of("created", created, "updated", updated, "skipped", skipped.size()));

    return new ImportResult(created, updated, skipped.size(), skipped);
  }

  public List<ProfileView> listProfiles(Long clientId) {
    return profileRepo.findByClientIdOrderByName(clientId).stream()
        .map(p -> new ProfileView(p.getId(), p.getName(), readMapping(p.getMapping())))
        .toList();
  }

  // --- internals -------------------------------------------------------

  /** Turn every record into an outcome: create / update / skip (with the reasons). */
  private List<RowOutcome> plan(
      Long clientId, List<CSVRecord> records, ColumnMapping mapping, boolean createMissingTypes) {
    List<RowOutcome> out = new ArrayList<>();
    for (CSVRecord record : records) {
      out.add(planRow(clientId, record, mapping, createMissingTypes));
    }
    return out;
  }

  private RowOutcome planRow(
      Long clientId, CSVRecord record, ColumnMapping mapping, boolean createMissingTypes) {
    List<String> errors = new ArrayList<>();
    Map<String, String> values = resolveValues(record, mapping, errors);
    validate(clientId, values, createMissingTypes, errors);

    int line = (int) record.getRecordNumber() + 1; // +1 for the header row
    if (!errors.isEmpty()) {
      return new RowOutcome(line, values, RowOutcome.SKIP, errors);
    }
    boolean exists =
        !assets
            .findByClientIdAndAssetTagAndType(clientId, values.get("assetTag"), values.get("type"))
            .isEmpty();
    return new RowOutcome(line, values, exists ? RowOutcome.UPDATE : RowOutcome.CREATE, List.of());
  }

  /**
   * Apply the mapping to one CSV row: our fields, parsed/validated, plus the kept attribute
   * columns.
   */
  private Map<String, String> resolveValues(
      CSVRecord record, ColumnMapping mapping, List<String> errors) {
    Map<String, String> values = new LinkedHashMap<>();
    for (String field : ColumnMapping.SUPPORTED) {
      String header = mapping.fields().get(field);
      resolveField(field, header == null ? null : cell(record, header), errors, values);
    }
    for (String col : mapping.attributeColumns()) {
      String raw = cell(record, col);
      if (raw != null && !raw.isBlank()) {
        values.put("attr:" + col, raw.trim());
      }
    }
    return values;
  }

  private void resolveField(
      String field, String raw, List<String> errors, Map<String, String> values) {
    if (raw == null || raw.isBlank()) {
      return;
    }
    switch (field) {
      case "purchaseDate", "warrantyEndsOn", "deployedOn" -> {
        LocalDate d = parseDate(raw, errors, field);
        if (d != null) {
          values.put(field, d.toString());
        }
      }
      case "condition" -> {
        AssetCondition c = parseCondition(raw, errors);
        if (c != null) {
          values.put(field, c.name());
        }
      }
      default -> values.put(field, raw.trim());
    }
  }

  private void validate(
      Long clientId, Map<String, String> values, boolean createMissingTypes, List<String> errors) {
    for (String required : ColumnMapping.REQUIRED) {
      if (blank(values.get(required))) {
        errors.add(required + " is required (map a column that has it)");
      }
    }
    String type = values.get("type");
    if (!blank(type) && !createMissingTypes && !typeExists(clientId, type)) {
      errors.add("unknown asset type '" + type + "' - create it, or tick 'add missing types'");
    }
  }

  /** Copy resolved values (and attr: entries) onto an asset. Leaves status / holder / tag alone. */
  private Asset apply(Asset asset, Map<String, String> values) {
    setIfPresent(values.get("serialNumber"), asset::setSerialNumber);
    setIfPresent(values.get("make"), asset::setMake);
    setIfPresent(values.get("model"), asset::setModel);
    setIfPresent(values.get("notes"), asset::setNotes);
    if (values.containsKey("condition")) {
      asset.setCondition(AssetCondition.valueOf(values.get("condition")));
    }
    setDate(values.get("purchaseDate"), asset::setPurchaseDate);
    setDate(values.get("warrantyEndsOn"), asset::setWarrantyEndsOn);
    setDate(values.get("deployedOn"), asset::setDeployedOn);

    Map<String, String> attrs = new LinkedHashMap<>(asset.getAttributes());
    values.forEach(
        (k, v) -> {
          if (k.startsWith("attr:")) {
            attrs.put(k.substring("attr:".length()), v);
          }
        });
    asset.setAttributes(attrs);
    return asset;
  }

  private boolean typeExists(Long clientId, String type) {
    return type != null && types.existsByClientIdAndNameIgnoreCase(clientId, type.trim());
  }

  private void saveProfile(Long clientId, String name, ColumnMapping mapping) {
    String body = writeMapping(mapping);
    profileRepo
        .findByClientIdAndNameIgnoreCase(clientId, name)
        .ifPresentOrElse(
            p -> p.setMapping(body),
            () -> profileRepo.save(new ImportProfile(clientId, name, body)));
  }

  // --- parsing helpers ----------------------------------------------------

  private record Parsed(List<String> headers, List<CSVRecord> records) {}

  private Parsed parse(byte[] csv) {
    String text = new String(csv, StandardCharsets.UTF_8);
    if (!text.isEmpty() && text.charAt(0) == '﻿') {
      text = text.substring(1);
    }
    try (CSVParser parser = CSV.parse(new StringReader(text))) {
      List<CSVRecord> records = parser.getRecords();
      if (records.size() > MAX_ROWS) {
        throw new IllegalArgumentException(
            "file has " + records.size() + " rows; the limit is " + MAX_ROWS);
      }
      return new Parsed(parser.getHeaderNames(), records);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static String cell(CSVRecord record, String header) {
    return record.isMapped(header) ? record.get(header) : null;
  }

  private static LocalDate parseDate(String raw, List<String> errors, String field) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    for (DateTimeFormatter f : DATE_FORMATS) {
      try {
        return LocalDate.parse(raw.trim(), f);
      } catch (DateTimeParseException ignored) {
        // try the next format
      }
    }
    errors.add(field + " '" + raw + "' is not a date we recognise (try YYYY-MM-DD)");
    return null;
  }

  private static AssetCondition parseCondition(String raw, List<String> errors) {
    try {
      return AssetCondition.valueOf(raw.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      errors.add("condition '" + raw + "' must be one of NEW/GOOD/FAIR/POOR/DAMAGED");
      return null;
    }
  }

  private ColumnMapping readMapping(String body) {
    try {
      return json.readValue(body, ColumnMapping.class);
    } catch (IOException e) {
      return new ColumnMapping(Map.of(), List.of());
    }
  }

  private String writeMapping(ColumnMapping mapping) {
    try {
      return json.writeValueAsString(mapping);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static boolean blank(String s) {
    return s == null || s.isBlank();
  }

  private static String blankToEmpty(String s) {
    return s == null ? "" : s;
  }

  private static void setIfPresent(String value, java.util.function.Consumer<String> setter) {
    if (value != null && !value.isBlank()) {
      setter.accept(value);
    }
  }

  private static void setDate(String iso, java.util.function.Consumer<LocalDate> setter) {
    if (iso != null && !iso.isBlank()) {
      setter.accept(LocalDate.parse(iso));
    }
  }
}
