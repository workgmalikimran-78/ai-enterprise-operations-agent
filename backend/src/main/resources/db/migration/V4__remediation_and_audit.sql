-- Root cause analysis, remediation, approval, execution, and audit.
-- These tables are schema-complete now but have no business logic until Phases 4-6.
CREATE TABLE root_cause_analyses (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    incident_id UUID NOT NULL REFERENCES incidents (id),
    summary TEXT NOT NULL,
    confidence NUMERIC(4, 3) NOT NULL CHECK (confidence >= 0 AND confidence <= 1),
    llm_provider VARCHAR(50),
    raw_response JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_root_cause_analyses_incident_id ON root_cause_analyses (incident_id);

CREATE TABLE remediation_recommendations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    root_cause_analysis_id UUID NOT NULL REFERENCES root_cause_analyses (id),
    action_type VARCHAR(100) NOT NULL,
    description TEXT NOT NULL,
    risk_level VARCHAR(10) NOT NULL
        CHECK (risk_level IN ('LOW', 'MEDIUM', 'HIGH')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_remediation_recommendations_rca_id ON remediation_recommendations (root_cause_analysis_id);

CREATE TABLE approval_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    remediation_recommendation_id UUID NOT NULL REFERENCES remediation_recommendations (id),
    requested_by UUID REFERENCES users (id),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'EXPIRED', 'CANCELLED')),
    decided_by UUID REFERENCES users (id),
    decided_at TIMESTAMPTZ,
    expires_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_approval_requests_status ON approval_requests (status);

CREATE TABLE remediation_executions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    approval_request_id UUID NOT NULL REFERENCES approval_requests (id),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'RUNNING', 'SUCCEEDED', 'FAILED', 'CANCELLED')),
    idempotency_key VARCHAR(100) NOT NULL UNIQUE,
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    k8s_job_ref VARCHAR(255),
    result_log TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_remediation_executions_approval_id ON remediation_executions (approval_request_id);

CREATE TABLE operational_audits (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants (id),
    actor_user_id UUID REFERENCES users (id),
    action VARCHAR(100) NOT NULL,
    entity_type VARCHAR(100) NOT NULL,
    entity_id UUID NOT NULL,
    details JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_operational_audits_entity ON operational_audits (entity_type, entity_id);
CREATE INDEX idx_operational_audits_created_at ON operational_audits (created_at);
