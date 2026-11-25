-- Governance and ETL observability metadata (public schema, append-only / BaseCreatedEntity
-- style - no updated_at). These three tables are how the warehouse ETL job (com.ai.aiops.warehouse
-- .WarehouseEtlJob) records what it did, what it checked, and how every dataset in the platform
-- (OLTP, bronze lake, warehouse, and OpenSearch) is classified and retained.
CREATE TABLE etl_run_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_name VARCHAR(100) NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    status VARCHAR(20) NOT NULL DEFAULT 'RUNNING'
        CHECK (status IN ('RUNNING', 'SUCCEEDED', 'FAILED')),
    watermark_from TIMESTAMPTZ,
    watermark_to TIMESTAMPTZ,
    rows_extracted BIGINT NOT NULL DEFAULT 0,
    rows_loaded BIGINT NOT NULL DEFAULT 0,
    error_message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_etl_run_log_job_status ON etl_run_log (job_name, status, started_at);

CREATE TABLE data_quality_checks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    etl_run_id UUID REFERENCES etl_run_log (id),
    check_name VARCHAR(150) NOT NULL,
    dataset_name VARCHAR(150) NOT NULL,
    run_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    status VARCHAR(10) NOT NULL
        CHECK (status IN ('PASS', 'FAIL', 'WARN')),
    row_count BIGINT,
    details JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_data_quality_checks_run_id ON data_quality_checks (etl_run_id);
CREATE INDEX idx_data_quality_checks_dataset_status ON data_quality_checks (dataset_name, status, run_at);

CREATE TABLE data_catalog_entries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    dataset_name VARCHAR(150) NOT NULL UNIQUE,
    layer VARCHAR(20) NOT NULL
        CHECK (layer IN ('OLTP', 'BRONZE', 'WAREHOUSE', 'SEARCH_INDEX')),
    location VARCHAR(255) NOT NULL,
    owner VARCHAR(150) NOT NULL,
    classification VARCHAR(20) NOT NULL
        CHECK (classification IN ('PUBLIC', 'INTERNAL', 'CONFIDENTIAL')),
    contains_pii BOOLEAN NOT NULL DEFAULT false,
    retention_days INT,
    description TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
