package com.assettracker.assetservice.type;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.assettracker.assetservice.audit.AuditService;
import com.assettracker.assetservice.entity.Asset;
import com.assettracker.assetservice.entity.AssetType;
import com.assettracker.assetservice.repository.AssetRepository;
import com.assettracker.assetservice.service.AssetNotFoundException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AssetTypeServiceTest {

  @Mock AssetTypeRepository types;
  @Mock AssetRepository assets;
  @Mock AuditService audit;
  @InjectMocks AssetTypeService service;

  @Test
  void createRejectsADuplicateNameCaseInsensitivelyAndTrimmed() {
    when(types.existsByClientIdAndNameIgnoreCase(1L, "Laptop")).thenReturn(true);

    assertThatThrownBy(() -> service.create(1L, "  Laptop  ", "tech@acme.example"))
        .isInstanceOf(AssetTypeExistsException.class);

    verify(types, never()).save(any());
  }

  @Test
  void createRejectsABlankName() {
    assertThatThrownBy(() -> service.create(1L, "   ", "tech@acme.example"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void createSavesTheTrimmedNameAndAudits() {
    when(types.existsByClientIdAndNameIgnoreCase(1L, "Headset")).thenReturn(false);
    when(types.save(any(AssetType.class))).thenAnswer(inv -> inv.getArgument(0));

    AssetType created = service.create(1L, " Headset ", "tech@acme.example");

    assertThat(created.getName()).isEqualTo("Headset");
    verify(audit)
        .record(eq(1L), eq("tech@acme.example"), eq("TYPE_CREATED"), any(), anyString(), any());
  }

  @Test
  void deletingAnUnusedTypeJustRemovesIt() {
    AssetType hotspot = new AssetType(1L, "Hotspot");
    when(types.findById(5L)).thenReturn(Optional.of(hotspot));
    when(assets.findByClientIdAndType(1L, "Hotspot")).thenReturn(List.of());

    service.delete(5L, null, "tech@acme.example");

    verify(assets, never()).saveAll(any());
    verify(types).delete(hotspot);
    verify(audit)
        .record(eq(1L), eq("tech@acme.example"), eq("TYPE_DELETED"), eq(5L), anyString(), any());
  }

  @Test
  void deletingAnInUseTypeWithoutReassignThrowsAndListsTheAssets() {
    AssetType hotspot = new AssetType(1L, "Hotspot");
    when(types.findById(5L)).thenReturn(Optional.of(hotspot));
    when(assets.findByClientIdAndType(1L, "Hotspot"))
        .thenReturn(List.of(asset("H-001"), asset("H-002")));

    assertThatThrownBy(() -> service.delete(5L, null, "tech@acme.example"))
        .isInstanceOf(AssetTypeInUseException.class)
        .satisfies(ex -> assertThat(((AssetTypeInUseException) ex).getAssets()).hasSize(2));

    verify(types, never()).delete(any());
  }

  @Test
  void deletingAnInUseTypeWithReassignMovesTheAssetsThenDeletes() {
    AssetType hotspot = new AssetType(1L, "Hotspot");
    Asset a1 = asset("H-001");
    Asset a2 = asset("H-002");
    when(types.findById(5L)).thenReturn(Optional.of(hotspot));
    when(assets.findByClientIdAndType(1L, "Hotspot")).thenReturn(List.of(a1, a2));
    when(types.findByClientIdAndNameIgnoreCase(1L, "Peripheral"))
        .thenReturn(Optional.of(new AssetType(1L, "Peripheral")));

    service.delete(5L, "Peripheral", "tech@acme.example");

    assertThat(a1.getType()).isEqualTo("Peripheral");
    assertThat(a2.getType()).isEqualTo("Peripheral");
    verify(assets).saveAll(List.of(a1, a2));
    verify(types).delete(hotspot);
  }

  @Test
  void deletingAnInUseTypeWithAnUnknownReassignTargetIsRejected() {
    AssetType hotspot = new AssetType(1L, "Hotspot");
    when(types.findById(5L)).thenReturn(Optional.of(hotspot));
    when(assets.findByClientIdAndType(1L, "Hotspot")).thenReturn(List.of(asset("H-001")));
    when(types.findByClientIdAndNameIgnoreCase(anyLong(), anyString()))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.delete(5L, "Nope", "tech@acme.example"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void deletingAMissingTypeIs404() {
    when(types.findById(99L)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.delete(99L, null, "tech@acme.example"))
        .isInstanceOf(AssetNotFoundException.class);
  }

  private static Asset asset(String tag) {
    return new Asset(1L, "Hotspot", "SN-" + tag, tag);
  }
}
