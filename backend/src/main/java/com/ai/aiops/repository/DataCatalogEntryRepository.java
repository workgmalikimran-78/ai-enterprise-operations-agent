package com.ai.aiops.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ai.aiops.domain.DataCatalogEntry;

public interface DataCatalogEntryRepository extends JpaRepository<DataCatalogEntry, UUID> {
    List<DataCatalogEntry> findAllByOrderByDatasetNameAsc();
}
