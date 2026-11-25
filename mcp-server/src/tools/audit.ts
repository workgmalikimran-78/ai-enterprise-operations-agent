import * as z from 'zod/v4';
import type { McpServer } from '@modelcontextprotocol/server';
import { apiGet } from '../api-client.js';
import { toolError, toolResult } from '../tool-helpers.js';
import { paginationParams } from './shared.js';

export function registerAuditTools(server: McpServer) {
    server.registerTool(
        'list_operational_audit',
        {
            title: 'List operational audit log',
            description: 'Who/what/when audit trail of every approval decision and remediation execution.',
            inputSchema: z.object({ ...paginationParams }),
            annotations: { readOnlyHint: true },
        },
        async (params) => {
            try {
                return toolResult(await apiGet('/audit', params));
            } catch (e) {
                return toolError(e);
            }
        },
    );

    server.registerTool(
        'list_agent_executions',
        {
            title: 'List all agent executions',
            description: 'Platform-wide (not per-incident) paginated trace of every AI agent run, success or failure.',
            inputSchema: z.object({ ...paginationParams }),
            annotations: { readOnlyHint: true },
        },
        async (params) => {
            try {
                return toolResult(await apiGet('/audit/agent-executions', params));
            } catch (e) {
                return toolError(e);
            }
        },
    );
}
