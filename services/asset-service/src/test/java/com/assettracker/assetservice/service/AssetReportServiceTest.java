package com.assettracker.assetservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.assettracker.assetservice.audit.AuditEventRepository;
import com.assettracker.assetservice.entity.HolderType;
import com.assettracker.assetservice.repository.AssetRepository;
import com.assettracker.assetservice.repository.CountBucket;
import com.assettracker.assetservice.web.CallerContextTestSupport;
import com.assettracker.assetservice.web.ForbiddenRoleException;
import com.assettracker.assetservice.web.dto.AssetReport;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * The reports rollup is tenant-wide by construction - fleet value, spend by department, who breaks
 * what - so the only correct answer for an ordinary employee is a refusal rather than a filtered
 * version. The rest of this checks the two numbers the service works out itself rather than reading
 * off a query.
 */
@ExtendWith(MockitoExtension.class)
class AssetReportServiceTest {

  private static final long ACME = 1L;
  private static final int SOON_DAYS = 60;
  private static final int TOP_SLOTS = 8;

  @Mock AssetRepository assets;
  @Mock AuditEventRepository audit;

  @AfterEach
  void clearCaller() {
    CallerContextTestSupport.reset();
    CallerContextTestSupport.clearTenant();
  }

  private AssetReportService service() {
    return new AssetReportService(assets, audit);
  }

  @Test
  void anEmployeeIsRefusedRatherThanGivenAScopedVersion() {
    CallerContextTestSupport.tenant(java.util.Set.of(ACME));
    CallerContextTestSupport.as("USER", 1L);

    assertThatThrownBy(() -> service().report(ACME, SOON_DAYS, TOP_SLOTS))
        .isInstanceOf(ForbiddenRoleException.class);

    // and nothing was read on the way to the refusal
    verifyNoInteractions(audit);
  }

  @Test
  void hrAndPocSeeTheirTenantsReport() {
    CallerContextTestSupport.tenant(java.util.Set.of(ACME));
    stubEmptyRollups();

    for (String role : List.of("ADMIN", "TECH", "POC", "HR")) {
      CallerContextTestSupport.as(role, null);
      assertThat(service().report(ACME, SOON_DAYS, TOP_SLOTS)).isNotNull();
    }
  }

  /**
   * "Expiring soon" and "in warranty" are subtractions between three counts, not three queries -
   * the shape that let the stats endpoint answer the same question with one fewer round trip.
   */
  @Test
  void theWarrantySplitIsSubtractionBetweenWindows() {
    CallerContextTestSupport.tenant(java.util.Set.of(ACME));
    CallerContextTestSupport.as("TECH", null);
    stubEmptyRollups();

    LocalDate today = LocalDate.now();
    when(assets.countWarrantyEndingBefore(anyLong(), any(LocalDate.class), any()))
        .thenAnswer(call -> call.getArgument(1, LocalDate.class).isAfter(today) ? 30L : 10L);
    when(assets.countWarrantyKnown(anyLong(), any())).thenReturn(100L);

    AssetReport report = service().report(ACME, SOON_DAYS, TOP_SLOTS);

    assertThat(report.outOfWarranty()).isEqualTo(10L);
    assertThat(report.warrantyExpiringSoon()).isEqualTo(20L);
    assertThat(report.inWarranty()).isEqualTo(70L);
  }

  @Test
  void incidentTotalIsTheSumOfItsBucketsRatherThanASeparateCount() {
    CallerContextTestSupport.tenant(java.util.Set.of(ACME));
    CallerContextTestSupport.as("TECH", null);
    stubEmptyRollups();
    when(audit.countIncidentsByAssetType(anyLong(), any(), any()))
        .thenReturn(List.of(bucket("Laptop", 3L), bucket("Cable", 4L)));

    AssetReport report = service().report(ACME, SOON_DAYS, TOP_SLOTS);

    assertThat(report.incidents().total()).isEqualTo(7L);
    assertThat(report.incidents().byType()).containsEntry("Cable", 4L);
  }

  /** Holder types the page names outright become their own fields; people stay as ids. */
  @Test
  void deskAndStockroomAreLiftedOutOfTheHolderTypeGroupBy() {
    CallerContextTestSupport.tenant(java.util.Set.of(ACME));
    CallerContextTestSupport.as("TECH", null);
    stubEmptyRollups();
    when(assets.countByHolderType(ACME))
        .thenReturn(
            List.of(
                bucket(HolderType.LOCATION.name(), 5L),
                bucket(HolderType.STOCKROOM.name(), 9L),
                bucket(HolderType.PERSON.name(), 40L)));
    when(assets.countByHolder(ACME, HolderType.PERSON))
        .thenReturn(List.of(bucket("7", 2L), bucket("8", 38L)));

    AssetReport.Holdings holdings = service().report(ACME, SOON_DAYS, TOP_SLOTS).holdings();

    assertThat(holdings.onDesk()).isEqualTo(5L);
    assertThat(holdings.unassigned()).isEqualTo(9L);
    // the department behind each id is people-service's to know, so they pass through as ids
    assertThat(holdings.byPerson()).containsOnly(entry("7", 2L), entry("8", 38L));
  }

  private void stubEmptyRollups() {
    lenient().when(assets.countByClientId(anyLong())).thenReturn(0L);
    lenient().when(assets.countByType(anyLong())).thenReturn(List.of());
    lenient().when(assets.countByStatus(anyLong())).thenReturn(List.of());
    lenient().when(assets.countByCondition(anyLong())).thenReturn(List.of());
    lenient().when(assets.countByHolderType(anyLong())).thenReturn(List.of());
    lenient().when(assets.countByHolder(anyLong(), any(HolderType.class))).thenReturn(List.of());
    lenient().when(assets.countReplacedSlots(anyLong(), any())).thenReturn(List.of());
    lenient().when(assets.sumPurchaseCost(anyLong(), any(Collection.class))).thenReturn(0L);
    lenient().when(assets.countWarrantyKnown(anyLong(), any())).thenReturn(0L);
    lenient()
        .when(assets.countWarrantyEndingBefore(anyLong(), any(LocalDate.class), any()))
        .thenReturn(0L);
    lenient().when(audit.countByAction(anyLong(), any())).thenReturn(List.of());
    lenient().when(audit.countIncidentsByAssetType(anyLong(), any(), any())).thenReturn(List.of());
    lenient().when(audit.countIncidentsByHolderType(anyLong(), any(), any())).thenReturn(List.of());
    lenient()
        .when(audit.countIncidentsByHolder(anyLong(), any(), any(), any(HolderType.class)))
        .thenReturn(List.of());
  }

  private static java.util.Map.Entry<String, Long> entry(String bucket, long total) {
    return java.util.Map.entry(bucket, total);
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
}
