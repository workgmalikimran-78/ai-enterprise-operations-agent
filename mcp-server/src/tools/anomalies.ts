import * as z from 'zod/v4';
import type { McpServer } from '@modelcontextprotocol/server';
import { apiGet } from '../api-client.js';
import { toolError, toolResult } from '../tool-helpers.js';
import { paginationParams } from './shared.js';

export function registerAnomalyTools(server: McpServer) {
    server.registerTool(
        'list_anomalies',
        {
            title: 'List anomalies',
            description: 'List detected anomalies (rule-based or statistical), optionally filtered by status (OPEN|ACKNOWLEDGED|RESOLVED).',
            inputSchema: z.object({
                status: z.string().optional(),
                ...paginationParams,
            }),
            annotations: { readOnlyHint: true },
        },
        async (params) => {
            try {
                return toolResult(await apiGet('/anomalies', params));
            } catch (e) {
                return toolError(e);
            }
        },
    );
}
