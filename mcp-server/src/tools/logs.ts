import * as z from 'zod/v4';
import type { McpServer } from '@modelcontextprotocol/server';
import { apiGet, apiPost } from '../api-client.js';
import { toolError, toolResult } from '../tool-helpers.js';
import { paginationParams } from './shared.js';
import type { LogSearchPagedResponse } from '../types.js';

export function registerLogTools(server: McpServer) {
    server.registerTool(
        'ingest_log',
        {
            title: 'Ingest a log event',
            description: 'Ingest a single application log event (writes to Postgres + Kafka, feeds anomaly detection immediately).',
            inputSchema: z.object({
                service: z.string().describe('Application/service name - doubles as the application identifier'),
                environment: z.string().describe('e.g. production, staging'),
                level: z.string().describe('TRACE|DEBUG|INFO|WARN|ERROR|FATAL'),
                message: z.string(),
                timestamp: z.string().optional().describe('ISO-8601 instant; defaults to now if omitted'),
                traceId: z.string().optional(),
                exceptionType: z.string().optional(),
                exceptionMessage: z.string().optional(),
                exceptionStackTrace: z.string().optional(),
            }),
            annotations: { readOnlyHint: false, destructiveHint: false, idempotentHint: false },
        },
        async ({ service, environment, level, message, timestamp, traceId, exceptionType, exceptionMessage, exceptionStackTrace }) => {
            try {
                const exception = exceptionType || exceptionMessage || exceptionStackTrace
                    ? { type: exceptionType, message: exceptionMessage, stackTrace: exceptionStackTrace }
                    : undefined;
                const result = await apiPost('/logs/ingest', { service, environment, level, message, timestamp, traceId, exception });
                return toolResult(result);
            } catch (e) {
                return toolError(e);
            }
        },
    );

    server.registerTool(
        'search_logs',
        {
            title: 'Search logs',
            description: 'Search ingested log events by service/environment/level/traceId/free-text, paginated.',
            inputSchema: z.object({
                service: z.string().optional(),
                environment: z.string().optional(),
                level: z.string().optional(),
                traceId: z.string().optional(),
                from: z.string().optional().describe('ISO-8601 instant lower bound'),
                to: z.string().optional().describe('ISO-8601 instant upper bound'),
                q: z.string().optional().describe('Free-text search'),
                ...paginationParams,
            }),
            annotations: { readOnlyHint: true },
        },
        async (params) => {
            try {
                const result = await apiGet<LogSearchPagedResponse<unknown>>('/logs/search', params);
                return toolResult(result);
            } catch (e) {
                return toolError(e);
            }
        },
    );

    server.registerTool(
        'get_log',
        {
            title: 'Get a log event by id',
            description: 'Fetch a single log event by its UUID.',
            inputSchema: z.object({ id: z.string().describe('Log event UUID') }),
            annotations: { readOnlyHint: true },
        },
        async ({ id }) => {
            try {
                return toolResult(await apiGet(`/logs/${id}`));
            } catch (e) {
                return toolError(e);
            }
        },
    );
}
