package com.assettracker.assetservice.web.dto;

import java.util.List;
import org.springframework.data.domain.Page;

/**
 * One page of the catalog, plus what the caller needs to render pagination without a second call.
 *
 * @param items the assets on this page
 * @param total how many match the filter in total
 * @param page zero-based page index
 * @param size page size actually applied (the request's, clamped)
 * @param totalPages how many pages the filter yields
 */
public record PagedAssets(
    List<AssetResponse> items, long total, int page, int size, int totalPages) {

  public static PagedAssets from(Page<com.assettracker.assetservice.entity.Asset> page) {
    return new PagedAssets(
        page.getContent().stream().map(AssetResponse::from).toList(),
        page.getTotalElements(),
        page.getNumber(),
        page.getSize(),
        page.getTotalPages());
  }
}
