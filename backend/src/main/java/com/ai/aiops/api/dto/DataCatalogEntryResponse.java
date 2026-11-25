package com.ai.aiops.api.dto;

import java.time.Instant;
import java.util.UUID;

import com.ai.aiops.domain.DataCatalogEntry;

public record DataCatalogEntryResponse(
        UUID id,
        String datasetName,
        String layer,
        String location,
        String owner,
        String classification,
        boolean containsPii,
        Integer retentionDays,
        String description,
        Instant createdAt
) {
    public static DataCatalogEntryResponse from(DataCatalogEntry e) {
        return new DataCatalogEntryResponse(e.getId(), e.getDatasetName(), e.getLayer(), e.getLocation(),
                e.getOwner(), e.getClassification(), e.isContainsPii(), e.getRetentionDays(), e.getDescription(),
                e.getCreatedAt());
    }
}
