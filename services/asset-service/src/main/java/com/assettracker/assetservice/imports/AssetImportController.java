package com.assettracker.assetservice.imports;

import com.assettracker.assetservice.imports.ImportViews.AnalyzeResult;
import com.assettracker.assetservice.imports.ImportViews.ImportPreview;
import com.assettracker.assetservice.imports.ImportViews.ImportResult;
import com.assettracker.assetservice.imports.ImportViews.ProfileView;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * The spreadsheet-import wizard's backend: <b>analyze</b> a CSV (headers + a guessed mapping + a
 * few sample rows + saved profiles), <b>preview</b> a mapping (row-by-row create/update/skip), then
 * <b>import</b>. Rides the {@code /api/assets/**} gateway route.
 */
@RestController
@RequestMapping("/assets/import")
public class AssetImportController {

  private static final String ACTOR = "X-User-Id";

  private final AssetImportService service;
  private final ObjectMapper json = new ObjectMapper();

  public AssetImportController(AssetImportService service) {
    this.service = service;
  }

  @PostMapping("/analyze")
  public AnalyzeResult analyze(
      @RequestParam Long clientId, @RequestPart("file") MultipartFile file) {
    return service.analyze(clientId, bytes(file));
  }

  @PostMapping("/preview")
  public ImportPreview preview(
      @RequestParam Long clientId,
      @RequestPart("file") MultipartFile file,
      @RequestParam("mapping") String mappingJson,
      @RequestParam(defaultValue = "false") boolean createMissingTypes) {
    return service.preview(clientId, bytes(file), mapping(mappingJson), createMissingTypes);
  }

  @PostMapping
  public ImportResult run(
      @RequestParam Long clientId,
      @RequestPart("file") MultipartFile file,
      @RequestParam("mapping") String mappingJson,
      @RequestParam(defaultValue = "false") boolean createMissingTypes,
      @RequestParam(required = false) String saveProfileAs,
      @RequestHeader(value = ACTOR, defaultValue = "system") String actor) {
    return service.commit(
        clientId, bytes(file), mapping(mappingJson), createMissingTypes, saveProfileAs, actor);
  }

  @GetMapping("/profiles")
  public List<ProfileView> profiles(@RequestParam Long clientId) {
    return service.listProfiles(clientId);
  }

  private static byte[] bytes(MultipartFile file) {
    try {
      return file.getBytes();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private ColumnMapping mapping(String mappingJson) {
    try {
      return json.readValue(mappingJson, ColumnMapping.class);
    } catch (IOException e) {
      throw new IllegalArgumentException("mapping is not valid JSON");
    }
  }
}
