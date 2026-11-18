package com.ai.aiops.remediation;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/** Structured LLM output for a remediation proposal. actionType is validated server-side. */
public record RemediationOutput(
        @JsonPropertyDescription("Must be exactly one of: CHECK_APPLICATION_HEALTH, RETRIEVE_RECENT_LOGS, "
                + "CHECK_SERVICE_STATUS, INVESTIGATE_FURTHER, RESTART_DEPLOYMENT, SCALE_DEPLOYMENT, "
                + "ROLLBACK_DEPLOYMENT - no other value is permitted")
        String actionType,

        @JsonPropertyDescription("One or two sentences describing exactly what this action will do, specific to "
                + "this incident's evidence")
        String description,

        @JsonPropertyDescription("What should happen if this action resolves the problem")
        String expectedOutcome,

        @JsonPropertyDescription("The business impact if this incident continues unaddressed, based only on the "
                + "supplied evidence (severity, affected application)")
        String businessImpact,

        @JsonPropertyDescription("How to recover if this action does not fix the problem or makes it worse")
        String rollbackStrategy
) {
}
