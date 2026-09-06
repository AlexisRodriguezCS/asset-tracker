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
import com.assettracker.assetservice.entity.AssetStatus;
import com.assettracker.assetservice.entity.HolderType;
import com.assettracker.assetservice.repository.AssetRepository;
import com.assettracker.assetservice.repository.CountBucket;
import com.assettracker.assetservice.web.CallerContextTestSupport;
import com.assettracker.assetservice.web.ForbiddenRoleException;
import com.assettracker.assetservice.web.dto.AssetStats;
import com.assettracker.assetservice.web.dto.CreateAssetRequest;
import com.assettracker.assetservice.web.dto.UpdateAssetRequest;
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

  private AssetSummaryService summary() {
    return new AssetSummaryService(repository, service());
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

  /**
   * The counts are aggregated in the database for staff, so the scoping cannot be inherited from
   * the list query - it has to be applied to the summary too, or an employee could read tenant-wide
   * totals off a page that shows them none of the rows.
   */
  @Test
  void anEmployeesStatsCountOnlyTheirOwnGear() {
    CallerContextTestSupport.as("USER", DANA);
    when(repository.search(eq(ACME), isNull(), isNull(), eq(HolderType.PERSON), eq(DANA), isNull()))
        .thenReturn(List.of(heldBy(DANA), heldBy(DANA)));

    AssetStats stats = summary().stats(ACME, 60);

    assertThat(stats.total()).isEqualTo(2);
    assertThat(stats.byStatus()).containsEntry("ASSIGNED", 2L);
    // the grouped queries are tenant-wide; an employee must never reach them
    verify(repository, never()).countByClientId(any());
    verify(repository, never()).countByStatus(any());
  }

  @Test
  void staffStatsComeFromTheDatabaseNotTheCatalog() {
    CallerContextTestSupport.as("TECH", null);
    when(repository.countByClientId(ACME)).thenReturn(1069L);
    when(repository.countByStatus(ACME)).thenReturn(List.of(bucket("IN_STOCK", 1028)));
    when(repository.countByType(ACME)).thenReturn(List.of(bucket("Laptop", 1008)));
    when(repository.countByCondition(ACME)).thenReturn(List.of(bucket("GOOD", 1069)));
    when(repository.countWarrantyEndingBefore(eq(ACME), any(), any())).thenReturn(12L, 17L);

    AssetStats stats = summary().stats(ACME, 60);

    assertThat(stats.total()).isEqualTo(1069);
    assertThat(stats.byStatus()).containsEntry("IN_STOCK", 1028L);
    assertThat(stats.outOfWarranty()).isEqualTo(12);
    // "expiring soon" is the difference between the two windows, not a third query
    assertThat(stats.warrantyExpiringSoon()).isEqualTo(5);
    verify(repository, never()).search(any(), any(), any(), any(), any(), any());
  }

  /**
   * Reads were scoped long before writes were. An employee could edit an asset, retire it, and
   * create new ones - the UI simply did not offer the buttons, which is not a control.
   */
  @Test
  void anEmployeeCannotChangeAnything() {
    CallerContextTestSupport.as("USER", DANA);
    AssetService service = service();

    assertThatThrownBy(
            () ->
                service.create(
                    new CreateAssetRequest(
                        ACME, "Cable", null, null, "SN-X", null, "EMP-1", null, null, null, null,
                        null, null, null),
                    "dana.reyes@acme.example"))
        .isInstanceOf(ForbiddenRoleException.class);

    assertThatThrownBy(
            () ->
                service.update(
                    9L,
                    new UpdateAssetRequest(
                        null, null, "edited", null, null, null, null, null, null),
                    "dana.reyes@acme.example"))
        .isInstanceOf(ForbiddenRoleException.class);

    assertThatThrownBy(
            () -> service.changeStatus(9L, AssetStatus.RETIRED, "dana.reyes@acme.example"))
        .isInstanceOf(ForbiddenRoleException.class);

    // nothing reached the database
    verify(repository, never()).save(any());
  }

  @Test
  void aTechStillCan() {
    CallerContextTestSupport.as("TECH", null);
    // update() mutates the managed entity; JPA dirty-checking persists it, so
    // there is no save() to stub here
    when(repository.findById(9L)).thenReturn(Optional.of(heldBy(SAM)));

    Asset updated =
        service()
            .update(
                9L,
                new UpdateAssetRequest(null, null, "tech note", null, null, null, null, null, null),
                "tech@acme.example");

    assertThat(updated.getNotes()).isEqualTo("tech note");
  }

  private static CountBucket bucket(String name, long total) {
    return new CountBucket() {
      @Override
      public String getBucket() {
        return name;
      }

      @Override
      public long getTotal() {
        return total;
      }
    };
  }

  @Test
  void anEmployeeCanOpenTheirOwnAsset() {
    CallerContextTestSupport.as("USER", DANA);
    when(repository.findById(9L)).thenReturn(Optional.of(heldBy(DANA)));

    assertThat(service().getById(9L).getHolderId()).isEqualTo(DANA);
  }
}
