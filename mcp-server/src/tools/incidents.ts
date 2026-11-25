import * as z from 'zod/v4';
import type { McpServer } from '@modelcontextprotocol/server';
import { apiGet, apiPost } from '../api-client.js';
import { toolError, toolResult } from '../tool-helpers.js';
import { paginationParams } from './shared.js';

export function registerIncidentTools(server: McpServer) {
    server.registerTool(
        'list_incidents',
        {
            title: 'List incidents',
            description: 'List incidents, optionally filtered by status (OPEN|INVESTIGATING|MITIGATED|RESOLVED|CLOSED).',
            inputSchema: z.object({ status: z.string().optional(), ...paginationParams }),
            annotations: { readOnlyHint: true },
        },
        async (params) => {
            try {
                return toolResult(await apiGet('/incidents', params));
            } catch (e) {
                return toolError(e);
            }
        },
    );

    server.registerTool(
        'get_incident',
        {
            title: 'Get an incident',
            description: 'Fetch one incident by its UUID.',
            inputSchema: z.object({ id: z.string().describe('Incident UUID') }),
            annotations: { readOnlyHint: true },
        },
        async ({ id }) => {
            try {
                return toolResult(await apiGet(`/incidents/${id}`));
            } catch (e) {
                return toolError(e);
            }
        },
    );

    server.registerTool(
        'investigate_incident',
        {
            title: 'Investigate an incident (AI root cause analysis)',
            description: 'Triggers the InvestigationAgent + RootCauseAnalysisAgent pipeline for an incident - gathers evidence, then calls the configured LLM for root cause analysis. Needs OPENAI_API_KEY/ANTHROPIC_API_KEY configured on the backend; returns 503 otherwise.',
            inputSchema: z.object({ id: z.string().describe('Incident UUID') }),
            annotations: { readOnlyHint: false, destructiveHint: false, idempotentHint: false },
        },
        async ({ id }) => {
            try {
                return toolResult(await apiPost(`/incidents/${id}/investigate`));
            } catch (e) {
                return toolError(e);
            }
        },
    );

    server.registerTool(
        'get_incident_analysis',
        {
            title: 'Get the latest root cause analysis',
            description: 'Fetch the most recent RCA result for an incident (summary, confidence, confirmed facts vs. hypotheses).',
            inputSchema: z.object({ id: z.string().describe('Incident UUID') }),
            annotations: { readOnlyHint: true },
        },
        async ({ id }) => {
            try {
                return toolResult(await apiGet(`/incidents/${id}/analysis`));
            } catch (e) {
                return toolError(e);
            }
        },
    );

    server.registerTool(
        'generate_remediation_recommendation',
        {
            title: 'Generate a remediation recommendation',
            description: 'Triggers the RemediationRecommendationAgent for an incident - the LLM picks exactly one action from a fixed catalog; risk/approval level is always derived server-side, never trusted from the LLM. Requires a prior investigation (RCA) to exist.',
            inputSchema: z.object({ id: z.string().describe('Incident UUID') }),
            annotations: { readOnlyHint: false, destructiveHint: false, idempotentHint: false },
        },
        async ({ id }) => {
            try {
                return toolResult(await apiPost(`/incidents/${id}/recommendations`));
            } catch (e) {
                return toolError(e);
            }
        },
    );

    server.registerTool(
        'list_incident_recommendations',
        {
            title: 'List remediation recommendations for an incident',
            description: 'List every remediation recommendation generated for an incident (not paginated).',
            inputSchema: z.object({ id: z.string().describe('Incident UUID') }),
            annotations: { readOnlyHint: true },
        },
        async ({ id }) => {
            try {
                return toolResult(await apiGet(`/incidents/${id}/recommendations`));
            } catch (e) {
                return toolError(e);
            }
        },
    );

    server.registerTool(
        'list_incident_agent_executions',
        {
            title: 'List agent executions for an incident',
            description: 'Paginated trace of every agent run (investigation, RCA, remediation, execution, verification) tied to an incident.',
            inputSchema: z.object({ id: z.string().describe('Incident UUID'), ...paginationParams }),
            annotations: { readOnlyHint: true },
        },
        async ({ id, ...pagination }) => {
            try {
                return toolResult(await apiGet(`/incidents/${id}/agent-executions`, pagination));
            } catch (e) {
                return toolError(e);
            }
        },
    );
}
