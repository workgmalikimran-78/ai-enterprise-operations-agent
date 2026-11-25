import * as z from 'zod/v4';
import type { McpServer } from '@modelcontextprotocol/server';
import { apiGet, apiPost } from '../api-client.js';
import { toolError, toolResult } from '../tool-helpers.js';

export function registerAdminTools(server: McpServer) {
    server.registerTool(
        'disable_execution',
        {
            title: 'Emergency kill switch: disable ALL remediation execution',
            description: 'Immediately blocks every mutating Kubernetes action cluster-wide, no redeploy needed. Use this if something is going wrong with automated remediation.',
            inputSchema: z.object({}),
            annotations: { readOnlyHint: false, destructiveHint: true, idempotentHint: true },
        },
        async () => {
            try {
                return toolResult(await apiPost('/admin/execution/disable'));
            } catch (e) {
                return toolError(e);
            }
        },
    );

    server.registerTool(
        'enable_execution',
        {
            title: 'Re-enable remediation execution',
            description: 'Re-enables mutating Kubernetes actions after disable_execution was called.',
            inputSchema: z.object({}),
            annotations: { readOnlyHint: false, destructiveHint: true, idempotentHint: true },
        },
        async () => {
            try {
                return toolResult(await apiPost('/admin/execution/enable'));
            } catch (e) {
                return toolError(e);
            }
        },
    );

    server.registerTool(
        'get_execution_status',
        {
            title: 'Get kill switch status',
            description: 'Returns whether remediation execution is currently enabled.',
            inputSchema: z.object({}),
            annotations: { readOnlyHint: true },
        },
        async () => {
            try {
                return toolResult(await apiGet('/admin/execution/status'));
            } catch (e) {
                return toolError(e);
            }
        },
    );
}
