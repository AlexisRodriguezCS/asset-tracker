package com.assettracker.assetservice.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.assettracker.assetservice.entity.AlreadyAssignedException;
import com.assettracker.assetservice.entity.Asset;
import com.assettracker.assetservice.entity.HolderType;
import com.assettracker.assetservice.service.AssetNotFoundException;
import com.assettracker.assetservice.service.AssetService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AssetController.class)
class AssetControllerTest {

  @Autowired MockMvc mvc;
  @MockitoBean AssetService service;

  @Test
  void searchReturnsTheMatchingAssets() throws Exception {
    Asset a = new Asset(1L, "Laptop", "SN-1", "TAG-1");
    when(service.search(eq(1L), eq("Laptop"), any(), any(), any(), any())).thenReturn(List.of(a));

    mvc.perform(get("/assets").param("clientId", "1").param("type", "Laptop"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].assetTag").value("TAG-1"))
        .andExpect(jsonPath("$[0].status").value("IN_STOCK"));
  }

  @Test
  void unknownAssetIs404() throws Exception {
    when(service.getById(99L)).thenThrow(new AssetNotFoundException("nope"));
    mvc.perform(get("/assets/99"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("ASSET_NOT_FOUND"));
  }

  @Test
  void assigningAnAssignedAssetIs409() throws Exception {
    when(service.assign(eq(1L), any(), any()))
        .thenThrow(new AlreadyAssignedException(1L, HolderType.PERSON, 7L));
    mvc.perform(
            post("/assets/1/assign")
                .contentType("application/json")
                .content("{\"holderType\":\"PERSON\",\"holderId\":9}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("ALREADY_ASSIGNED"));
  }
}
