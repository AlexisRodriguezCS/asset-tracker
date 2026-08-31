package com.assettracker.assetservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.assettracker.assetservice.entity.Asset;
import com.assettracker.assetservice.entity.AssetType;
import com.assettracker.assetservice.entity.HolderType;
import com.assettracker.assetservice.repository.AssetRepository;
import com.assettracker.assetservice.web.dto.AssignRequest;
import com.assettracker.assetservice.web.dto.CreateAssetRequest;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AssetServiceTest {

  @Mock AssetRepository repository;
  @InjectMocks AssetService service;

  private Asset stored;

  @BeforeEach
  void setUp() {
    stored = new Asset(1L, AssetType.LAPTOP, "SN-1", "TAG-1");
  }

  @Test
  void createRejectsADuplicateTag() {
    when(repository.existsByAssetTag("TAG-1")).thenReturn(true);
    CreateAssetRequest req =
        new CreateAssetRequest(1L, AssetType.LAPTOP, null, null, "SN-1", "TAG-1", null, null, null);
    assertThatThrownBy(() -> service.create(req)).isInstanceOf(AssetTagTakenException.class);
  }

  @Test
  void createPersistsAndReturnsTheAsset() {
    when(repository.existsByAssetTag("TAG-2")).thenReturn(false);
    when(repository.save(any(Asset.class))).thenAnswer(inv -> inv.getArgument(0));
    CreateAssetRequest req =
        new CreateAssetRequest(
            1L, AssetType.CABLE, "Anker", "USB-C", "SN-2", "TAG-2", null, 1900L, null);
    Asset created = service.create(req);
    assertThat(created.getMake()).isEqualTo("Anker");
    assertThat(created.getPurchaseCostCents()).isEqualTo(1900L);
  }

  @Test
  void assignDelegatesToTheEntityGuard() {
    stored.assignTo(HolderType.PERSON, 5L);
    when(repository.findById(1L)).thenReturn(Optional.of(stored));
    assertThatThrownBy(() -> service.assign(1L, new AssignRequest(HolderType.PERSON, 9L)))
        .isInstanceOf(com.assettracker.assetservice.entity.AlreadyAssignedException.class);
  }

  @Test
  void getByIdThrowsWhenMissing() {
    when(repository.findById(42L)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.getById(42L)).isInstanceOf(AssetNotFoundException.class);
  }
}
