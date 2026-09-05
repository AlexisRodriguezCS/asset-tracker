package com.assettracker.assetservice.web.dto;

import java.util.List;

/**
 * How heavily one asset type is used, plus a few examples.
 *
 * <p>The type manager needs both: the count to say "12 assets", and a handful of tags to show which
 * ones a deletion would affect. Counts alone will not do, which is why this exists rather than the
 * page reading {@code /assets/stats}.
 *
 * @param type the type name
 * @param total how many of the client's assets are on it
 * @param sample up to a few of them, for the confirmation list
 */
public record TypeUsage(String type, long total, List<AssetResponse> sample) {}
