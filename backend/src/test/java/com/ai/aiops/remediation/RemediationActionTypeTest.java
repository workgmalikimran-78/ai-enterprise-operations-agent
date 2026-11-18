package com.ai.aiops.remediation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

class RemediationActionTypeTest {

    @Test
    void fromStringIsCaseInsensitiveAndTolerant() {
        assertThat(RemediationActionType.fromString("restart_deployment"))
                .contains(RemediationActionType.RESTART_DEPLOYMENT);
        assertThat(RemediationActionType.fromString("  RESTART_DEPLOYMENT  "))
                .contains(RemediationActionType.RESTART_DEPLOYMENT);
    }

    @Test
    void fromStringReturnsEmptyForUnknownOrNullValuesRatherThanThrowing() {
        assertThat(RemediationActionType.fromString("DELETE_DATABASE")).isEmpty();
        assertThat(RemediationActionType.fromString(null)).isEmpty();
        assertThat(RemediationActionType.fromString("")).isEmpty();
    }

    @Test
    void everyMutatingActionRequiresHighApproval() {
        for (RemediationActionType type : Arrays.asList(
                RemediationActionType.RESTART_DEPLOYMENT,
                RemediationActionType.SCALE_DEPLOYMENT,
                RemediationActionType.ROLLBACK_DEPLOYMENT)) {
            assertThat(type.requiredApprovalLevel()).isEqualTo("HIGH");
        }
    }

    @Test
    void everyReadOnlyOrDiagnosticActionNeedsNoApproval() {
        for (RemediationActionType type : Arrays.asList(
                RemediationActionType.CHECK_APPLICATION_HEALTH,
                RemediationActionType.RETRIEVE_RECENT_LOGS,
                RemediationActionType.CHECK_SERVICE_STATUS,
                RemediationActionType.INVESTIGATE_FURTHER)) {
            assertThat(type.requiredApprovalLevel()).isEqualTo("NONE");
        }
    }
}
