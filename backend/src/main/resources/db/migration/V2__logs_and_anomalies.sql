-- Log event storage and anomaly detection results.
CREATE TABLE log_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants (id),
    application_id UUID NOT NULL REFERENCES applications (id),
    environment_id UUID NOT NULL REFERENCES environments (id),
    "timestamp" TIMESTAMPTZ NOT NULL,
    service VARCHAR(150) NOT NULL,
    level VARCHAR(10) NOT NULL
        CHECK (level IN ('TRACE', 'DEBUG', 'INFO', 'WARN', 'ERROR', 'FATAL')),
    trace_id VARCHAR(100),
    message TEXT NOT NULL,
    exception_type VARCHAR(255),
    exception_message TEXT,
    raw_payload JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_log_events_application_timestamp ON log_events (application_id, "timestamp");
CREATE INDEX idx_log_events_trace_id ON log_events (trace_id) WHERE trace_id IS NOT NULL;
CREATE INDEX idx_log_events_application_level_created ON log_events (application_id, level, created_at);

CREATE TABLE anomalies (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants (id),
    application_id UUID NOT NULL REFERENCES applications (id),
    detection_type VARCHAR(20) NOT NULL
        CHECK (detection_type IN ('RULE_BASED', 'STATISTICAL')),
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN'
        CHECK (status IN ('OPEN', 'ACKNOWLEDGED', 'RESOLVED')),
    severity VARCHAR(10) NOT NULL
        CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    description TEXT NOT NULL,
    metadata JSONB,
    window_start TIMESTAMPTZ NOT NULL,
    window_end TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_anomalies_application_status_created ON anomalies (application_id, status, created_at);
