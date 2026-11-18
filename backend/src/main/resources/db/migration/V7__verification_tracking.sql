-- Tracks whether a completed remediation execution has already been through post-remediation
-- verification, so the scheduled verification job never re-verifies (and re-decides incident
-- status for) the same execution twice.
ALTER TABLE remediation_executions
    ADD COLUMN verified_at TIMESTAMPTZ;

-- Read-only/diagnostic remediations (required_approval_level = NONE) execute without ever going
-- through an approval request, so this FK can no longer be mandatory.
ALTER TABLE remediation_executions
    ALTER COLUMN approval_request_id DROP NOT NULL;

ALTER TABLE remediation_executions
    ADD COLUMN remediation_recommendation_id UUID REFERENCES remediation_recommendations (id);

CREATE INDEX idx_remediation_executions_recommendation_id
    ON remediation_executions (remediation_recommendation_id);
