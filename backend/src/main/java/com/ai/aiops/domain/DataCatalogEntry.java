package com.ai.aiops.domain;

import com.ai.aiops.common.BaseCreatedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Governance catalog: one row per dataset in the platform (OLTP, bronze lake, warehouse, search index). */
@Entity
@Table(name = "data_catalog_entries")
@Getter
@Setter
@NoArgsConstructor
public class DataCatalogEntry extends BaseCreatedEntity {

    @Column(name = "dataset_name", nullable = false, unique = true)
    private String datasetName;

    @Column(nullable = false)
    private String layer;

    @Column(nullable = false)
    private String location;

    @Column(nullable = false)
    private String owner;

    @Column(nullable = false)
    private String classification;

    @Column(name = "contains_pii", nullable = false)
    private boolean containsPii;

    @Column(name = "retention_days")
    private Integer retentionDays;

    @Column(columnDefinition = "text")
    private String description;
}
