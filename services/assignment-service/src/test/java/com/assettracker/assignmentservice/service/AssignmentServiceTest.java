package com.assettracker.assignmentservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.assettracker.assignmentservice.audit.AuditService;
import com.assettracker.assignmentservice.client.AssetClient;
import com.assettracker.assignmentservice.client.NotificationClient;
import com.assettracker.assignmentservice.entity.Assignment;
import com.assettracker.assignmentservice.entity.HolderType;
import com.assettracker.assignmentservice.web.dto.CheckOutRequest;
import com.assettracker.assignmentservice.web.dto.OffboardingResult;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AssignmentServiceTest {

  @Mock AssetClient assetClient;
  @Mock NotificationClient notificationClient;
  @Mock AssignmentTransactions store;
  @Mock AuditService audit;
  @InjectMocks AssignmentService service;

  private final CheckOutRequest checkOut =
      new CheckOutRequest(1L, 40L, HolderType.PERSON, 7L, "onboarding");

  @Test
  void checkOutAssignsThenRecordsThenNotifies() {
    Assignment saved =
        new Assignment(1L, 40L, HolderType.PERSON, 7L, "tech@acme.example", "onboarding");
    when(store.open(1L, 40L, HolderType.PERSON, 7L, "tech@acme.example", "onboarding"))
        .thenReturn(saved);

    Assignment result = service.checkOut(checkOut, "tech@acme.example");

    assertThat(result).isSameAs(saved);
    verify(assetClient).assign(eq(40L), eq("PERSON"), eq(7L), anyString());
    verify(notificationClient).send(eq(1L), eq("ASSET_CHECKED_OUT"), anyString());
  }

  @Test
  void aConflictFromAssetServiceAbortsWithNoHistoryRow() {
    doThrow(new AssetUnavailableException(40L))
        .when(assetClient)
        .assign(40L, "PERSON", 7L, "tech@acme.example");

    assertThatThrownBy(() -> service.checkOut(checkOut, "tech@acme.example"))
        .isInstanceOf(AssetUnavailableException.class);

    verifyNoInteractions(store);
    verifyNoInteractions(notificationClient);
  }

  @Test
  void checkInReturnsToStockAndClosesTheAssignment() {
    Assignment open = new Assignment(1L, 40L, HolderType.PERSON, 7L, "tech@acme.example", null);
    when(store.close(40L, "tech@acme.example")).thenReturn(open);

    service.checkIn(40L, "tech@acme.example");

    verify(assetClient).returnToStock(eq(40L), anyString());
    verify(store).close(40L, "tech@acme.example");
    verify(notificationClient).send(eq(1L), eq("ASSET_RETURNED"), anyString());
  }

  @Test
  void offboardingIsBestEffortPerAsset() {
    when(assetClient.assetsHeldByPerson(1L, 7L)).thenReturn(List.of(40L, 41L, 42L));
    // asset 41 will not come back (lenient: the 40L / 42L calls are the normal path)
    lenient()
        .doThrow(new RuntimeException("stuck"))
        .when(assetClient)
        .returnToStock(eq(41L), anyString());

    OffboardingResult result = service.offboardPerson(1L, 7L, "hr@acme.example");

    assertThat(result.returned()).containsExactly(40L, 42L);
    assertThat(result.failed()).containsExactly(41L);
    verify(store, never()).close(eq(41L), anyString());
    verify(store).close(40L, "hr@acme.example");
    verify(store).close(42L, "hr@acme.example");
    verify(notificationClient).send(eq(1L), eq("OFFBOARDING_COLLECTED"), anyString());
  }

  @Test
  void getByIdDelegatesToTheStore() {
    Assignment a = new Assignment(1L, 40L, HolderType.PERSON, 7L, "x", null);
    when(store.getById(5L)).thenReturn(a);
    assertThat(service.getById(5L)).isSameAs(a);
  }
}
