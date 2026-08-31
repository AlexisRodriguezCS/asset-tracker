package com.assettracker.assignmentservice.web.dto;

import com.assettracker.assignmentservice.entity.HolderType;
import jakarta.validation.constraints.NotNull;

/** Body for moving an asset straight from one holder to another. */
public record TransferRequest(
    @NotNull Long clientId,
    @NotNull Long assetId,
    @NotNull HolderType holderType,
    @NotNull Long holderId,
    String note) {}
