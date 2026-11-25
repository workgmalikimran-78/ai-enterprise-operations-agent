-- Analytics warehouse: a dimensional (star-schema) mart, separate from the OLTP tables above,
-- fed by the ETL/ELT job in com.ai.aiops.warehouse. Lives in its own Postgres schema so it reads
-- as a distinct analytical store rather than more operational tables, while staying runnable in
-- the same local/docker-compose Postgres instance. Dimensions are refreshed/extended by the ETL
-- job; fact tables only ever hold aggregated counts/averages (see V9's data_catalog_entries
-- seed for why that matters for PII).
CREATE SCHEMA IF NOT EXISTS analytics;

CREATE TABLE analytics.dim_date (
    date_id DATE PRIMARY KEY,
    year INT NOT NULL,
    month INT NOT NULL,
    day INT NOT NULL,
    iso_week INT NOT NULL
);

CREATE TABLE analytics.dim_application (
    application_id UUID PRIMARY KEY REFERENCES applications (id),
    tenant_id UUID NOT NULL REFERENCES tenants (id),
    name VARCHAR(255) NOT NULL
);

CREATE TABLE analytics.dim_severity (
    severity VARCHAR(10) PRIMARY KEY,
    sort_order INT NOT NULL
);

INSERT INTO analytics.dim_severity (severity, sort_order) VALUES
    ('LOW', 1), ('MEDIUM', 2), ('HIGH', 3), ('CRITICAL', 4);

CREATE TABLE analytics.fact_incident_daily (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    date_id DATE NOT NULL REFERENCES analytics.dim_date (date_id),
    application_id UUID NOT NULL REFERENCES analytics.dim_application (application_id),
    severity VARCHAR(10) NOT NULL REFERENCES analytics.dim_severity (severity),
    incidents_opened INT NOT NULL DEFAULT 0,
    incidents_resolved INT NOT NULL DEFAULT 0,
    avg_mttr_seconds NUMERIC,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_fact_incident_daily UNIQUE (date_id, application_id, severity)
);

CREATE TABLE analytics.fact_remediation_daily (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    date_id DATE NOT NULL REFERENCES analytics.dim_date (date_id),
    application_id UUID NOT NULL REFERENCES analytics.dim_application (application_id),
    action_type VARCHAR(100) NOT NULL,
    executions_total INT NOT NULL DEFAULT 0,
    executions_succeeded INT NOT NULL DEFAULT 0,
    executions_failed INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_fact_remediation_daily UNIQUE (date_id, application_id, action_type)
);

CREATE TABLE analytics.fact_log_volume_hourly (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hour_bucket TIMESTAMPTZ NOT NULL,
    application_id UUID NOT NULL REFERENCES analytics.dim_application (application_id),
    level VARCHAR(10) NOT NULL,
    event_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_fact_log_volume_hourly UNIQUE (hour_bucket, application_id, level)
);

CREATE INDEX idx_fact_incident_daily_date ON analytics.fact_incident_daily (date_id);
CREATE INDEX idx_fact_remediation_daily_date ON analytics.fact_remediation_daily (date_id);
CREATE INDEX idx_fact_log_volume_hourly_bucket ON analytics.fact_log_volume_hourly (hour_bucket);
