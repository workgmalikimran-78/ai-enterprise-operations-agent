-- Incidents aggregate one or more anomalies into a single operational event.
CREATE TABLE incidents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants (id),
    application_id UUID NOT NULL REFERENCES applications (id),
    title VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN'
        CHECK (status IN ('OPEN', 'INVESTIGATING', 'MITIGATED', 'RESOLVED', 'CLOSED')),
    severity VARCHAR(10) NOT NULL
        CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    opened_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    resolved_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_incidents_status_severity_opened ON incidents (status, severity, opened_at);
CREATE INDEX idx_incidents_application_id ON incidents (application_id);

CREATE TABLE incident_anomalies (
    incident_id UUID NOT NULL REFERENCES incidents (id) ON DELETE CASCADE,
    anomaly_id UUID NOT NULL REFERENCES anomalies (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (incident_id, anomaly_id)
);
