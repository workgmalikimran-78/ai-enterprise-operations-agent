import * as z from 'zod/v4';
import type { McpServer } from '@modelcontextprotocol/server';
import { apiGet } from '../api-client.js';
import { toolError, toolResult } from '../tool-helpers.js';

export function registerDashboardTools(server: McpServer) {
    server.registerTool(
        'get_dashboard_overview',
        {
            title: 'Get dashboard overview',
            description: 'Platform-wide counts: total/critical/resolved incidents, active anomalies, MTTD/MTTR, AI investigation stats.',
            inputSchema: z.object({}),
            annotations: { readOnlyHint: true },
        },
        async () => {
            try {
                return toolResult(await apiGet('/dashboard/overview'));
            } catch (e) {
                return toolError(e);
            }
        },
    );

    server.registerTool(
        'get_dashboard_metrics',
        {
            title: 'Get dashboard breakdown metrics',
            description: 'Breakdowns: incidents by severity/status, anomalies by detection type, agent executions by type.',
            inputSchema: z.object({}),
            annotations: { readOnlyHint: true },
        },
        async () => {
            try {
                return toolResult(await apiGet('/dashboard/metrics'));
            } catch (e) {
                return toolError(e);
            }
        },
    );

    server.registerTool(
        'list_dashboard_incidents',
        {
            title: 'List most recent incidents',
            description: 'The N most recent incidents across all statuses (the Overview page\'s recent-incidents list).',
            inputSchema: z.object({ size: z.number().int().min(1).max(200).optional().describe('Default 10') }),
            annotations: { readOnlyHint: true },
        },
        async (params) => {
            try {
                return toolResult(await apiGet('/dashboard/incidents', params));
            } catch (e) {
                return toolError(e);
            }
        },
    );
}
