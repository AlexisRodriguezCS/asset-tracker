package com.assettracker.assetservice.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.assettracker.assetservice.entity.Asset;
import com.assettracker.assetservice.entity.AssetStatus;
import com.assettracker.assetservice.entity.AssetType;
import com.assettracker.assetservice.entity.HolderType;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

/** The one search() query behind every catalog view, plus tag-slot reuse. */
@DataJpaTest
class AssetRepositoryTest {

  @Autowired AssetRepository repository;

  @BeforeEach
  void seed() {
    Asset laptopForPerson = new Asset(1L, AssetType.LAPTOP, "SN-A", "TAG-A");
    laptopForPerson.assignTo(HolderType.PERSON, 7L);

    Asset laptopInStock = new Asset(1L, AssetType.LAPTOP, "SN-B", "TAG-B");

    Asset cableOnDesk = new Asset(1L, AssetType.CABLE, "SN-C", "TAG-C");
    cableOnDesk.assignTo(HolderType.LOCATION, 3L);

    Asset otherClient = new Asset(2L, AssetType.LAPTOP, "SN-D", "TAG-D");

    repository.saveAll(List.of(laptopForPerson, laptopInStock, cableOnDesk, otherClient));
  }

  @Test
  void allLaptopsForAClient() {
    List<Asset> result = repository.search(1L, AssetType.LAPTOP, null, null, null, null, null);
    assertThat(result).extracting(Asset::getSerialNumber).containsExactlyInAnyOrder("SN-A", "SN-B");
  }

  @Test
  void whatIsOnDeskThree() {
    List<Asset> result = repository.search(1L, null, null, HolderType.LOCATION, 3L, null, null);
    assertThat(result).extracting(Asset::getSerialNumber).containsExactly("SN-C");
  }

  @Test
  void inStockOnly() {
    List<Asset> result = repository.search(1L, null, AssetStatus.IN_STOCK, null, null, null, null);
    assertThat(result).extracting(Asset::getSerialNumber).containsExactly("SN-B");
  }

  @Test
  void everythingOnATag() {
    Asset oldCharger = new Asset(1L, AssetType.CHARGER, "SN-OLD", "TAG-A");
    oldCharger.setStatus(AssetStatus.LOST);
    Asset newCharger = new Asset(1L, AssetType.CHARGER, "SN-NEW", "TAG-A");
    repository.saveAll(List.of(oldCharger, newCharger));

    List<Asset> onTag = repository.search(1L, null, null, null, null, "TAG-A", null);
    assertThat(onTag)
        .extracting(Asset::getSerialNumber)
        .containsExactlyInAnyOrder("SN-A", "SN-OLD", "SN-NEW");
  }

  @Test
  void aTagSlotIsFreeOnceTheOldUnitLeavesService() {
    Asset oldCharger = new Asset(1L, AssetType.CHARGER, "SN-OLD", "TAG-A");
    oldCharger.setStatus(AssetStatus.LOST);
    repository.save(oldCharger);

    assertThat(repository.existsActiveWithTag(1L, "TAG-A", AssetType.CHARGER, AssetStatus.ACTIVE))
        .isFalse();

    Asset newCharger = new Asset(1L, AssetType.CHARGER, "SN-NEW", "TAG-A");
    repository.save(newCharger);

    assertThat(repository.existsActiveWithTag(1L, "TAG-A", AssetType.CHARGER, AssetStatus.ACTIVE))
        .isTrue();
  }

  @Test
  void neverLeaksAnotherClientsAssets() {
    List<Asset> result = repository.search(1L, AssetType.LAPTOP, null, null, null, null, null);
    assertThat(result).extracting(Asset::getClientId).containsOnly(1L);
  }
}
