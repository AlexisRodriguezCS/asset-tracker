package com.assettracker.assetservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.assettracker.assetservice.audit.AuditService;
import com.assettracker.assetservice.entity.Asset;
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
  @Mock AuditService audit;
  @InjectMocks AssetService service;

  private Asset stored;

  @BeforeEach
  void setUp() {
    stored = new Asset(1L, "Laptop", "SN-1", "TAG-1");
  }

  @Test
  void createRejectsADuplicateTag() {
    when(repository.existsActiveWithTag(eq(1L), eq("TAG-1"), eq("Laptop"), any())).thenReturn(true);
    CreateAssetRequest req =
        new CreateAssetRequest(
            1L, "Laptop", null, null, "SN-1", "TAG-1", null, null, null, null, null, null, null);
    assertThatThrownBy(() -> service.create(req, "tech@acme.example"))
        .isInstanceOf(AssetTagTakenException.class);
  }

  @Test
  void createPersistsAndAuditsWithTheActor() {
    when(repository.existsActiveWithTag(eq(1L), eq("TAG-2"), eq("Cable"), any())).thenReturn(false);
    when(repository.save(any(Asset.class))).thenAnswer(inv -> inv.getArgument(0));
    CreateAssetRequest req =
        new CreateAssetRequest(
            1L, "Cable", "Anker", "USB-C", "SN-2", "TAG-2", null, null, null, null, 1900L, null,
            null);

    Asset created = service.create(req, "tech@acme.example");

    assertThat(created.getMake()).isEqualTo("Anker");
    verify(audit)
        .record(eq(1L), eq("tech@acme.example"), eq("ASSET_CREATED"), any(), anyString(), any());
  }

  @Test
  void createLinksTheReplacementToTheUnitItSupersedes() {
    Asset old = new Asset(1L, "Charger", "SN-OLD", "TAG-9");
    when(repository.existsActiveWithTag(eq(1L), eq("TAG-9"), eq("Charger"), any()))
        .thenReturn(false);
    when(repository.findById(77L)).thenReturn(Optional.of(old));
    when(repository.save(any(Asset.class))).thenAnswer(inv -> inv.getArgument(0));
    CreateAssetRequest req =
        new CreateAssetRequest(
            1L, "Charger", null, null, "SN-NEW", "TAG-9", null, null, null, null, null, null, 77L);

    Asset created = service.create(req, "tech@acme.example");

    assertThat(created.getSupersedesAssetId()).isEqualTo(77L);
  }

  @Test
  void createRejectsASupersededAssetFromAnotherClient() {
    Asset otherClient = new Asset(2L, "Charger", "SN-OLD", "TAG-9");
    when(repository.existsActiveWithTag(eq(1L), eq("TAG-9"), eq("Charger"), any()))
        .thenReturn(false);
    when(repository.findById(77L)).thenReturn(Optional.of(otherClient));
    CreateAssetRequest req =
        new CreateAssetRequest(
            1L, "Charger", null, null, "SN-NEW", "TAG-9", null, null, null, null, null, null, 77L);

    assertThatThrownBy(() -> service.create(req, "tech@acme.example"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void createRejectsASupersededAssetOnADifferentTag() {
    Asset differentTag = new Asset(1L, "Charger", "SN-OLD", "TAG-OTHER");
    when(repository.existsActiveWithTag(eq(1L), eq("TAG-9"), eq("Charger"), any()))
        .thenReturn(false);
    when(repository.findById(77L)).thenReturn(Optional.of(differentTag));
    CreateAssetRequest req =
        new CreateAssetRequest(
            1L, "Charger", null, null, "SN-NEW", "TAG-9", null, null, null, null, null, null, 77L);

    assertThatThrownBy(() -> service.create(req, "tech@acme.example"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("same tag and type");
  }

  @Test
  void assignDelegatesToTheEntityGuard() {
    stored.assignTo(HolderType.PERSON, 5L);
    when(repository.findById(1L)).thenReturn(Optional.of(stored));
    assertThatThrownBy(
            () -> service.assign(1L, new AssignRequest(HolderType.PERSON, 9L), "tech@acme.example"))
        .isInstanceOf(com.assettracker.assetservice.entity.AlreadyAssignedException.class);
  }

  @Test
  void changeStatusAuditsTheTransition() {
    when(repository.findById(1L)).thenReturn(Optional.of(stored));
    service.changeStatus(
        1L, com.assettracker.assetservice.entity.AssetStatus.RETIRED, "tech@acme.example");
    verify(audit)
        .record(
            eq(1L), eq("tech@acme.example"), eq("ASSET_STATUS_RETIRED"), any(), anyString(), any());
  }

  @Test
  void getByIdThrowsWhenMissing() {
    when(repository.findById(42L)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.getById(42L)).isInstanceOf(AssetNotFoundException.class);
  }
}
