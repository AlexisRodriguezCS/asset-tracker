package com.assettracker.assetservice.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** The guarded custody transitions - these throws are the orchestrator's 409 / 422. */
class AssetTest {

  private Asset newAsset() {
    return new Asset(1L, "Laptop", "SN-1", "TAG-1");
  }

  @Test
  void newAssetStartsInStockInTheStockroom() {
    Asset a = newAsset();
    assertThat(a.getStatus()).isEqualTo(AssetStatus.IN_STOCK);
    assertThat(a.getHolderType()).isEqualTo(HolderType.STOCKROOM);
    assertThat(a.getHolderId()).isNull();
  }

  @Test
  void assignToSetsHolderAndStatus() {
    Asset a = newAsset();
    a.assignTo(HolderType.PERSON, 7L);
    assertThat(a.getStatus()).isEqualTo(AssetStatus.ASSIGNED);
    assertThat(a.getHolderType()).isEqualTo(HolderType.PERSON);
    assertThat(a.getHolderId()).isEqualTo(7L);
  }

  @Test
  void assigningAnAlreadyAssignedAssetIsRejected() {
    Asset a = newAsset();
    a.assignTo(HolderType.PERSON, 7L);
    assertThatThrownBy(() -> a.assignTo(HolderType.LOCATION, 3L))
        .isInstanceOf(AlreadyAssignedException.class);
  }

  @Test
  void assigningARetiredAssetIsRejected() {
    Asset a = newAsset();
    a.setStatus(AssetStatus.RETIRED);
    assertThatThrownBy(() -> a.assignTo(HolderType.PERSON, 7L))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void returnToStockClearsTheHolder() {
    Asset a = newAsset();
    a.assignTo(HolderType.PERSON, 7L);
    a.returnToStock();
    assertThat(a.getStatus()).isEqualTo(AssetStatus.IN_STOCK);
    assertThat(a.getHolderType()).isEqualTo(HolderType.STOCKROOM);
    assertThat(a.getHolderId()).isNull();
  }

  @Test
  void retiringAnAssetAlsoClearsItsHolder() {
    Asset a = newAsset();
    a.assignTo(HolderType.PERSON, 7L);
    a.setStatus(AssetStatus.RETIRED);
    assertThat(a.getHolderType()).isEqualTo(HolderType.STOCKROOM);
    assertThat(a.getHolderId()).isNull();
  }

  @Test
  void markingAnAssetBrokenAlsoMarksItDamaged() {
    Asset a = newAsset();
    a.setCondition(AssetCondition.GOOD);
    a.setStatus(AssetStatus.BROKEN);
    assertThat(a.getCondition()).isEqualTo(AssetCondition.DAMAGED);
  }

  @Test
  void damagingAnInStockAssetPullsItFromThePool() {
    Asset a = newAsset();
    a.setCondition(AssetCondition.DAMAGED);
    assertThat(a.getStatus()).isEqualTo(AssetStatus.BROKEN);
    assertThat(a.getCondition()).isEqualTo(AssetCondition.DAMAGED);
  }

  @Test
  void anAssignedAssetMayBeDamagedWithoutChangingStatus() {
    Asset a = newAsset();
    a.assignTo(HolderType.PERSON, 7L);
    a.setCondition(AssetCondition.DAMAGED);
    assertThat(a.getStatus()).isEqualTo(AssetStatus.ASSIGNED);
    assertThat(a.getCondition()).isEqualTo(AssetCondition.DAMAGED);
  }

  @Test
  void returningADamagedUnitToStockDropsItToPoorNotDamaged() {
    Asset a = newAsset();
    a.assignTo(HolderType.PERSON, 7L);
    a.setCondition(AssetCondition.DAMAGED);
    a.returnToStock();
    assertThat(a.getStatus()).isEqualTo(AssetStatus.IN_STOCK);
    assertThat(a.getCondition()).isEqualTo(AssetCondition.POOR);
  }
}
