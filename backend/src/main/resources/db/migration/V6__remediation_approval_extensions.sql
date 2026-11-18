-- Phase 5 needs more than the Phase 1 shell columns: the risk-assessed remediation proposal and
-- its approval lifecycle. required_approval_level is deterministic (derived server-side from the
-- chosen action type, never trusted from LLM output) - see RemediationActionType.
ALTER TABLE remediation_recommendations
    ADD COLUMN expected_outcome TEXT,
    ADD COLUMN business_impact TEXT,
    ADD COLUMN rollback_strategy TEXT,
    ADD COLUMN required_approval_level VARCHAR(10) NOT NULL DEFAULT 'HIGH'
        CHECK (required_approval_level IN ('NONE', 'LOW', 'HIGH')),
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'PROPOSED'
        CHECK (status IN ('PROPOSED', 'APPROVAL_REQUESTED', 'APPROVED', 'REJECTED', 'EXECUTED', 'SUPERSEDED'));

ALTER TABLE approval_requests
    ADD COLUMN rejection_reason TEXT;

CREATE INDEX idx_remediation_recommendations_status ON remediation_recommendations (status);
