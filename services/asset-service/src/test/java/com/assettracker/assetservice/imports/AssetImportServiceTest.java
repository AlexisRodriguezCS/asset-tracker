package com.assettracker.assetservice.imports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.assettracker.assetservice.audit.AuditService;
import com.assettracker.assetservice.entity.Asset;
import com.assettracker.assetservice.imports.ImportViews.ImportPreview;
import com.assettracker.assetservice.imports.ImportViews.ImportResult;
import com.assettracker.assetservice.imports.ImportViews.RowOutcome;
import com.assettracker.assetservice.repository.AssetRepository;
import com.assettracker.assetservice.type.AssetTypeRepository;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AssetImportServiceTest {

  @Mock AssetRepository assets;
  @Mock AssetTypeRepository types;
  @Mock ImportProfileRepository profiles;
  @Mock AuditService audit;

  AssetImportService service;

  private static final String CSV =
      """
      Barcode,Device Type,Manufacturer,S/N,Date of Purchase,Cost Center
      IMP-1,Laptop,Apple,SN-A,3/15/2024,ENG-1
      IMP-2,Laptop,Dell,SN-B,2024-01-08,ENG-1
      IMP-3,Widget,Acme,,not-a-date,OPS-9
      """;

  @BeforeEach
  void setUp() {
    service = new AssetImportService(assets, types, profiles, audit);
    lenient().when(types.existsByClientIdAndNameIgnoreCase(eq(1L), any())).thenReturn(false);
    lenient().when(types.existsByClientIdAndNameIgnoreCase(eq(1L), eq("Laptop"))).thenReturn(true);
    lenient()
        .when(assets.findByClientIdAndAssetTagAndType(anyLong(), any(), any()))
        .thenReturn(List.of());
  }

  private ColumnMapping mapping() {
    return new ColumnMapping(
        Map.of(
            "assetTag", "Barcode",
            "type", "Device Type",
            "make", "Manufacturer",
            "serialNumber", "S/N",
            "purchaseDate", "Date of Purchase"),
        List.of("Cost Center"));
  }

  private byte[] csv() {
    return CSV.getBytes(StandardCharsets.UTF_8);
  }

  @Test
  void analyzeGuessesTheObviousColumns() {
    var result = service.analyze(1L, csv());
    assertThat(result.headers()).contains("Barcode", "Device Type", "S/N");
    assertThat(result.suggested().fields())
        .containsEntry("assetTag", "Barcode")
        .containsEntry("type", "Device Type")
        .containsEntry("make", "Manufacturer")
        .containsEntry("serialNumber", "S/N")
        .containsEntry("purchaseDate", "Date of Purchase");
    assertThat(result.sampleRows()).hasSize(3);
  }

  @Test
  void previewClassifiesRows() {
    ImportPreview preview = service.preview(1L, csv(), mapping(), false);

    assertThat(preview.total()).isEqualTo(3);
    assertThat(preview.willCreate()).isEqualTo(2); // the two Laptop rows
    assertThat(preview.invalid()).isEqualTo(1); // Widget: unknown type + bad date

    RowOutcome bad =
        preview.rows().stream().filter(r -> r.action().equals(RowOutcome.SKIP)).findFirst().get();
    assertThat(bad.errors()).anyMatch(e -> e.contains("date"));
    assertThat(bad.errors()).anyMatch(e -> e.contains("unknown asset type"));
  }

  @Test
  void commitCreatesValidRowsAndKeepsUnmappedColumnsAsAttributes() {
    when(assets.save(any(Asset.class))).thenAnswer(inv -> inv.getArgument(0));

    ImportResult result = service.commit(1L, csv(), mapping(), false, null, "tech@acme.example");

    assertThat(result.created()).isEqualTo(2);
    assertThat(result.updated()).isZero();
    assertThat(result.skipped()).isEqualTo(1);

    var saved = org.mockito.ArgumentCaptor.forClass(Asset.class);
    org.mockito.Mockito.verify(assets, org.mockito.Mockito.times(2)).save(saved.capture());
    assertThat(saved.getAllValues())
        .allSatisfy(a -> assertThat(a.getAttributes()).containsEntry("Cost Center", "ENG-1"));
    assertThat(saved.getAllValues().get(0).getSerialNumber()).isEqualTo("SN-A");
  }

  @Test
  void reUploadUpdatesTheMatchingRowInsteadOfDuplicating() {
    Asset existing = new Asset(1L, "Laptop", "OLD-SN", "IMP-1");
    when(assets.findByClientIdAndAssetTagAndType(1L, "IMP-1", "Laptop"))
        .thenReturn(List.of(existing));
    when(assets.save(any(Asset.class))).thenAnswer(inv -> inv.getArgument(0));

    ImportResult result = service.commit(1L, csv(), mapping(), false, null, "tech@acme.example");

    assertThat(result.updated()).isEqualTo(1);
    assertThat(result.created()).isEqualTo(1); // IMP-2 is still new
    assertThat(existing.getSerialNumber()).isEqualTo("SN-A"); // overwritten from the sheet
  }

  @Test
  void createMissingTypesRemovesTheUnknownTypeError() {
    ImportPreview strict = service.preview(1L, csv(), mapping(), false);
    RowOutcome widgetStrict =
        strict.rows().stream()
            .filter(r -> r.values().get("assetTag").equals("IMP-3"))
            .findFirst()
            .get();
    assertThat(widgetStrict.errors()).anyMatch(e -> e.contains("unknown asset type"));

    ImportPreview lenientRun = service.preview(1L, csv(), mapping(), true);
    RowOutcome widgetLenient =
        lenientRun.rows().stream()
            .filter(r -> r.values().get("assetTag").equals("IMP-3"))
            .findFirst()
            .get();
    // the type no longer blocks it; only the bad date remains
    assertThat(widgetLenient.errors()).noneMatch(e -> e.contains("unknown asset type"));
    assertThat(widgetLenient.errors()).anyMatch(e -> e.contains("date"));
  }
}
