import * as z from 'zod/v4';
import type { McpServer } from '@modelcontextprotocol/server';
import { apiGet, apiPost } from '../api-client.js';
import { toolError, toolResult } from '../tool-helpers.js';
import { paginationParams } from './shared.js';

export function registerApprovalTools(server: McpServer) {
    server.registerTool(
        'list_approvals',
        {
            title: 'List approval requests',
            description: 'List human-in-the-loop approval requests, optionally filtered by status (PENDING|APPROVED|REJECTED|EXPIRED|CANCELLED).',
            inputSchema: z.object({ status: z.string().optional(), ...paginationParams }),
            annotations: { readOnlyHint: true },
        },
        async (params) => {
            try {
                return toolResult(await apiGet('/approvals', params));
            } catch (e) {
                return toolError(e);
            }
        },
    );

    server.registerTool(
        'approve_remediation',
        {
            title: 'Approve a pending remediation',
            description: 'Approves a PENDING approval request so its remediation can be executed. This is the human-in-the-loop gate for mutating Kubernetes actions - calling this tool IS the human approval, so only call it when a real person has reviewed and decided to approve.',
            inputSchema: z.object({
                id: z.string().describe('Approval request UUID'),
                approverEmail: z.string().email(),
            }),
            annotations: { readOnlyHint: false, destructiveHint: true, idempotentHint: false },
        },
        async ({ id, approverEmail }) => {
            try {
                return toolResult(await apiPost(`/approvals/${id}/approve`, { approverEmail }));
            } catch (e) {
                return toolError(e);
            }
        },
    );

    server.registerTool(
        'reject_remediation',
        {
            title: 'Reject a pending remediation',
            description: 'Rejects a PENDING approval request with a reason.',
            inputSchema: z.object({
                id: z.string().describe('Approval request UUID'),
                approverEmail: z.string().email(),
                reason: z.string().optional(),
            }),
            annotations: { readOnlyHint: false, destructiveHint: false, idempotentHint: false },
        },
        async ({ id, approverEmail, reason }) => {
            try {
                return toolResult(await apiPost(`/approvals/${id}/reject`, { approverEmail, reason }));
            } catch (e) {
                return toolError(e);
            }
        },
    );
}
