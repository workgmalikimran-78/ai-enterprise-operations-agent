package com.ai.aiops.remediation;

import java.util.Arrays;
import java.util.Optional;

/**
 * The complete, fixed catalog of remediation actions the LLM is allowed to choose from. Risk
 * level and required approval level are fixed per action here - they are never derived from or
 * overridable by LLM output, which is the structural defense against a prompt-injected log
 * message talking the model into downgrading risk/approval for a dangerous action.
 */
public enum RemediationActionType {
    CHECK_APPLICATION_HEALTH("Check application health", "LOW", "NONE"),
    RETRIEVE_RECENT_LOGS("Retrieve recent application logs", "LOW", "NONE"),
    CHECK_SERVICE_STATUS("Check Kubernetes service/pod status", "LOW", "NONE"),
    INVESTIGATE_FURTHER("No safe automated action identified; recommend manual investigation",
            "LOW", "NONE"),
    RESTART_DEPLOYMENT("Restart the affected Kubernetes deployment", "HIGH", "HIGH"),
    SCALE_DEPLOYMENT("Scale the affected Kubernetes deployment", "MEDIUM", "HIGH"),
    ROLLBACK_DEPLOYMENT("Roll back to the previous healthy deployment", "HIGH", "HIGH");

    private final String defaultDescription;
    private final String riskLevel;
    private final String requiredApprovalLevel;

    RemediationActionType(String defaultDescription, String riskLevel, String requiredApprovalLevel) {
        this.defaultDescription = defaultDescription;
        this.riskLevel = riskLevel;
        this.requiredApprovalLevel = requiredApprovalLevel;
    }

    public String defaultDescription() {
        return defaultDescription;
    }

    public String riskLevel() {
        return riskLevel;
    }

    public String requiredApprovalLevel() {
        return requiredApprovalLevel;
    }

    /** Case-insensitive lookup that never throws - callers must handle an empty result (fail-safe). */
    public static Optional<RemediationActionType> fromString(String value) {
        if (value == null) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(a -> a.name().equalsIgnoreCase(value.trim()))
                .findFirst();
    }
}
