import * as z from 'zod/v4';
import type { McpServer } from '@modelcontextprotocol/server';
import { apiGet, apiPost } from '../api-client.js';
import { toolError, toolResult } from '../tool-helpers.js';

export function registerRemediationTools(server: McpServer) {
    server.registerTool(
        'execute_remediation',
        {
            title: 'Execute a remediation against the real Kubernetes cluster',
            description: 'Runs the approved (or auto-allowed read-only) remediation action. Passes through ExecutionGuard: kill switch, namespace allowlist, per-target rate limit, and an idempotency key (the recommendation id) - calling this twice for the same recommendation just replays the first result rather than re-running the mutation. This is the one tool in this server that can change the state of a real cluster.',
            inputSchema: z.object({ recommendationId: z.string().describe('Remediation recommendation UUID') }),
            annotations: { readOnlyHint: false, destructiveHint: true, idempotentHint: true },
        },
        async ({ recommendationId }) => {
            try {
                return toolResult(await apiPost(`/remediations/${recommendationId}/execute`));
            } catch (e) {
                return toolError(e);
            }
        },
    );

    server.registerTool(
        'get_remediation_status',
        {
            title: 'Get remediation execution status',
            description: 'Fetch the execution status (PENDING|RUNNING|SUCCEEDED|FAILED|CANCELLED) and result log for a remediation recommendation.',
            inputSchema: z.object({ recommendationId: z.string().describe('Remediation recommendation UUID') }),
            annotations: { readOnlyHint: true },
        },
        async ({ recommendationId }) => {
            try {
                return toolResult(await apiGet(`/remediations/${recommendationId}/status`));
            } catch (e) {
                return toolError(e);
            }
        },
    );
}
