-- Seed the data catalog with every dataset the platform already has, not just the new
-- warehouse/lake additions - governance coverage is complete from day one.
INSERT INTO data_catalog_entries
    (dataset_name, layer, location, owner, classification, contains_pii, retention_days, description)
VALUES
    ('log_events', 'OLTP', 'public.log_events', 'Platform Engineering', 'CONFIDENTIAL', true, 90,
        'Raw ingested application log lines; message/exception_message may contain customer or '
        'operational PII and are the system of record for anomaly detection.'),
    ('anomalies', 'OLTP', 'public.anomalies', 'Platform Engineering', 'INTERNAL', false, NULL,
        'Rule-based and statistical anomaly detections, derived from log_events counts only.'),
    ('incidents', 'OLTP', 'public.incidents', 'Platform Engineering', 'INTERNAL', false, NULL,
        'Correlated incidents opened from HIGH/CRITICAL anomalies.'),
    ('remediation_executions', 'OLTP', 'public.remediation_executions', 'Platform Engineering', 'INTERNAL', false, NULL,
        'Record of every Kubernetes remediation action taken, approved or auto-allowed.'),
    ('operational_audits', 'OLTP', 'public.operational_audits', 'Platform Engineering', 'INTERNAL', false, 2555,
        'Append-only audit trail of every approval decision and remediation execution.'),
    ('log-events-search-index', 'SEARCH_INDEX', 'opensearch: log-events-yyyy.MM (monthly indices)',
        'Platform Engineering', 'CONFIDENTIAL', true, 90,
        'Full-text-searchable mirror of log_events, indexed asynchronously via Kafka.'),
    ('bronze-log-events', 'BRONZE', 's3://aiops-lake/bronze/log_events/', 'Platform Engineering', 'CONFIDENTIAL', true, 90,
        'Raw NDJSON.gz export of log_events written by WarehouseEtlJob, full fidelity, retained via '
        'bucket lifecycle rule.'),
    ('bronze-incidents', 'BRONZE', 's3://aiops-lake/bronze/incidents/', 'Platform Engineering', 'INTERNAL', false, 90,
        'Raw NDJSON.gz export of incidents written by WarehouseEtlJob.'),
    ('bronze-remediation-executions', 'BRONZE', 's3://aiops-lake/bronze/remediation_executions/',
        'Platform Engineering', 'INTERNAL', false, 90,
        'Raw NDJSON.gz export of remediation_executions written by WarehouseEtlJob.'),
    ('fact_incident_daily', 'WAREHOUSE', 'analytics.fact_incident_daily', 'Platform Engineering', 'INTERNAL', false, 730,
        'Daily incident counts and MTTR by application/severity. Aggregated only - PII-safe by '
        'construction, no free-text columns.'),
    ('fact_remediation_daily', 'WAREHOUSE', 'analytics.fact_remediation_daily', 'Platform Engineering', 'INTERNAL', false, 730,
        'Daily remediation execution outcomes by application/action type. Aggregated only.'),
    ('fact_log_volume_hourly', 'WAREHOUSE', 'analytics.fact_log_volume_hourly', 'Platform Engineering', 'INTERNAL', false, 730,
        'Hourly log event counts by application/level. Aggregated only.'),
    ('data_catalog_entries', 'OLTP', 'public.data_catalog_entries', 'Platform Engineering', 'PUBLIC', false, NULL,
        'This catalog itself.'),
    ('data_quality_checks', 'OLTP', 'public.data_quality_checks', 'Platform Engineering', 'INTERNAL', false, 365,
        'Per-run data quality check results for the warehouse ETL job.'),
    ('etl_run_log', 'OLTP', 'public.etl_run_log', 'Platform Engineering', 'INTERNAL', false, 365,
        'Run history for the warehouse ETL job (watermarks, row counts, status).');
