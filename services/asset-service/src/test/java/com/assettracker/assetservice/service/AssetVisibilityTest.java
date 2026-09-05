package com.assettracker.assetservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.assettracker.assetservice.audit.AuditService;
import com.assettracker.assetservice.entity.Asset;
import com.assettracker.assetservice.entity.HolderType;
import com.assettracker.assetservice.repository.AssetRepository;
import com.assettracker.assetservice.web.CallerContextTestSupport;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * What an ordinary employee is allowed to see. The console only ever renders their own gear, but
 * the guarantee has to hold at the service, not the page - anyone can curl the API.
 */
@ExtendWith(MockitoExtension.class)
class AssetVisibilityTest {

  private static final long ACME = 1L;
  private static final long DANA = 1L;
  private static final long SAM = 2L;

  @Mock AssetRepository repository;

  @Mock AuditService audit;

  private AssetService service() {
    return new AssetService(repository, audit);
  }

  @AfterEach
  void clearCaller() {
    CallerContextTestSupport.reset();
    CallerContextTestSupport.clearTenant();
  }

  private Asset heldBy(Long personId) {
    Asset a = new Asset(ACME, "Laptop", "SN-1", "ACME-1");
    a.assignTo(HolderType.PERSON, personId);
    return a;
  }

  @Test
  void anEmployeesListIsForcedOntoTheirOwnPersonWhateverTheyAskFor() {
    CallerContextTestSupport.as("USER", DANA);
    when(repository.search(eq(ACME), isNull(), isNull(), eq(HolderType.PERSON), eq(DANA), isNull()))
        .thenReturn(List.of(heldBy(DANA)));

    // ask for somebody else's gear explicitly - the filter is overridden, not honoured
    List<Asset> visible = service().search(ACME, null, null, HolderType.PERSON, SAM, null);

    assertThat(visible).hasSize(1);
    verify(repository, never()).search(any(), any(), any(), any(), eq(SAM), any());
  }

  @Test
  void anEmployeeWithNoPersonRecordSeesNothingRatherThanEverything() {
    CallerContextTestSupport.as("USER", null);

    assertThat(service().search(ACME, null, null, null, null, null)).isEmpty();
    verify(repository, never()).search(any(), any(), any(), any(), any(), any());
  }

  @Test
  void staffKeepTheFilterTheyAskedFor() {
    CallerContextTestSupport.as("TECH", null);
    when(repository.search(ACME, null, null, HolderType.PERSON, SAM, null))
        .thenReturn(List.of(heldBy(SAM)));

    assertThat(service().search(ACME, null, null, HolderType.PERSON, SAM, null)).hasSize(1);
  }

  @Test
  void anEmployeeCannotOpenAnAssetHeldBySomeoneElse() {
    CallerContextTestSupport.as("USER", DANA);
    when(repository.findById(9L)).thenReturn(Optional.of(heldBy(SAM)));

    assertThatThrownBy(() -> service().getById(9L)).isInstanceOf(AssetNotFoundException.class);
  }

  @Test
  void anEmployeeCanOpenTheirOwnAsset() {
    CallerContextTestSupport.as("USER", DANA);
    when(repository.findById(9L)).thenReturn(Optional.of(heldBy(DANA)));

    assertThat(service().getById(9L).getHolderId()).isEqualTo(DANA);
  }
}
