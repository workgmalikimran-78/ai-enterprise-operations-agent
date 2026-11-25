import * as z from 'zod/v4';
import type { McpServer } from '@modelcontextprotocol/server';
import { apiGet, apiPost } from '../api-client.js';
import { toolError, toolResult } from '../tool-helpers.js';
import { paginationParams } from './shared.js';

const dateRangeParams = {
    from: z.string().optional().describe('LocalDate yyyy-MM-dd, defaults to 30 days ago'),
    to: z.string().optional().describe('LocalDate yyyy-MM-dd, defaults to today'),
};

export function registerWarehouseTools(server: McpServer) {
    server.registerTool(
        'get_data_catalog',
        {
            title: 'Get the data governance catalog',
            description: 'Every dataset in the platform (OLTP tables, bronze lake, warehouse facts, search index) with its layer, classification, PII flag, and retention policy.',
            inputSchema: z.object({}),
            annotations: { readOnlyHint: true },
        },
        async () => {
            try {
                return toolResult(await apiGet('/warehouse/catalog'));
            } catch (e) {
                return toolError(e);
            }
        },
    );

    server.registerTool(
        'list_data_quality_checks',
        {
            title: 'List data quality check results',
            description: 'Completeness/freshness/referential-integrity/PII-safety check results from warehouse ETL runs.',
            inputSchema: z.object({ ...paginationParams }),
            annotations: { readOnlyHint: true },
        },
        async (params) => {
            try {
                return toolResult(await apiGet('/warehouse/quality-checks', params));
            } catch (e) {
                return toolError(e);
            }
        },
    );

    server.registerTool(
        'list_etl_runs',
        {
            title: 'List warehouse ETL run history',
            description: 'Run history for the warehouse ETL job - watermarks, row counts, status.',
            inputSchema: z.object({ ...paginationParams }),
            annotations: { readOnlyHint: true },
        },
        async (params) => {
            try {
                return toolResult(await apiGet('/warehouse/etl-runs', params));
            } catch (e) {
                return toolError(e);
            }
        },
    );

    server.registerTool(
        'run_warehouse_etl',
        {
            title: 'Trigger the warehouse ETL job now',
            description: 'Runs the extract -> bronze lake -> warehouse aggregation -> data quality checks pipeline synchronously and returns its result, instead of waiting for the hourly schedule.',
            inputSchema: z.object({}),
            annotations: { readOnlyHint: false, destructiveHint: false, idempotentHint: false },
        },
        async () => {
            try {
                return toolResult(await apiPost('/warehouse/etl/run'));
            } catch (e) {
                return toolError(e);
            }
        },
    );

    server.registerTool(
        'get_incidents_daily_metrics',
        {
            title: 'Get warehouse incidents-by-day metrics',
            description: 'Aggregated incident counts and MTTR by application/severity/day from the analytics warehouse.',
            inputSchema: z.object({ applicationId: z.string().optional(), ...dateRangeParams }),
            annotations: { readOnlyHint: true },
        },
        async (params) => {
            try {
                return toolResult(await apiGet('/warehouse/metrics/incidents-daily', params));
            } catch (e) {
                return toolError(e);
            }
        },
    );

    server.registerTool(
        'get_remediation_daily_metrics',
        {
            title: 'Get warehouse remediation-outcomes-by-day metrics',
            description: 'Aggregated remediation execution outcomes by application/action-type/day from the analytics warehouse.',
            inputSchema: z.object({ ...dateRangeParams }),
            annotations: { readOnlyHint: true },
        },
        async (params) => {
            try {
                return toolResult(await apiGet('/warehouse/metrics/remediation-daily', params));
            } catch (e) {
                return toolError(e);
            }
        },
    );

    server.registerTool(
        'get_log_volume_hourly_metrics',
        {
            title: 'Get warehouse log-volume-by-hour metrics',
            description: 'Aggregated log event counts by application/level/hour from the analytics warehouse.',
            inputSchema: z.object({ ...dateRangeParams }),
            annotations: { readOnlyHint: true },
        },
        async (params) => {
            try {
                return toolResult(await apiGet('/warehouse/metrics/log-volume-hourly', params));
            } catch (e) {
                return toolError(e);
            }
        },
    );
}
