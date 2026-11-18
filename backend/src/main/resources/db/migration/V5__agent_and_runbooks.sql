-- Agent execution tracing and operational runbooks.
-- Schema-complete now, business logic lands in Phase 4+.
CREATE TABLE agent_executions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    incident_id UUID REFERENCES incidents (id),
    agent_type VARCHAR(50) NOT NULL
        CHECK (agent_type IN (
            'MONITORING', 'INVESTIGATION', 'ROOT_CAUSE_ANALYSIS',
            'REMEDIATION', 'APPROVAL', 'EXECUTION', 'VERIFICATION'
        )),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'RUNNING', 'SUCCEEDED', 'FAILED')),
    input JSONB,
    output JSONB,
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_agent_executions_incident_id ON agent_executions (incident_id);

CREATE TABLE runbooks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants (id),
    name VARCHAR(255) NOT NULL,
    trigger_pattern VARCHAR(255),
    steps JSONB NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_runbooks_tenant_id ON runbooks (tenant_id);
