import { beforeEach, describe, expect, it, vi } from 'vitest';
import { apiPost } from '../api-client.js';
import { registerApprovalTools } from './approvals.js';

vi.mock(import('../api-client.js'), async (importOriginal) => {
    const actual = await importOriginal();
    return { ...actual, apiGet: vi.fn(), apiPost: vi.fn() };
});

function fakeServer() {
    const handlers = new Map<string, (args: any) => Promise<any>>();
    return {
        registerTool: (name: string, _config: unknown, handler: (args: any) => Promise<any>) => {
            handlers.set(name, handler);
        },
        handlers,
    };
}

describe('approval tools (request-body-carrying mutations)', () => {
    beforeEach(() => {
        vi.clearAllMocks();
    });

    it('approve_remediation sends approverEmail in the POST body to the right path', async () => {
        const server = fakeServer();
        registerApprovalTools(server as any);
        (apiPost as ReturnType<typeof vi.fn>).mockResolvedValueOnce({ id: 'appr-1', status: 'APPROVED' });

        const result = await server.handlers.get('approve_remediation')!({
            id: 'appr-1',
            approverEmail: 'ops@example.com',
        });

        expect(apiPost).toHaveBeenCalledWith('/approvals/appr-1/approve', { approverEmail: 'ops@example.com' });
        expect(JSON.parse(result.content[0].text)).toEqual({ id: 'appr-1', status: 'APPROVED' });
    });

    it('reject_remediation sends both approverEmail and reason in the POST body', async () => {
        const server = fakeServer();
        registerApprovalTools(server as any);
        (apiPost as ReturnType<typeof vi.fn>).mockResolvedValueOnce({ id: 'appr-2', status: 'REJECTED' });

        await server.handlers.get('reject_remediation')!({
            id: 'appr-2',
            approverEmail: 'ops@example.com',
            reason: 'too risky right now',
        });

        expect(apiPost).toHaveBeenCalledWith('/approvals/appr-2/reject', {
            approverEmail: 'ops@example.com',
            reason: 'too risky right now',
        });
    });

    it('reject_remediation works without a reason (optional field)', async () => {
        const server = fakeServer();
        registerApprovalTools(server as any);
        (apiPost as ReturnType<typeof vi.fn>).mockResolvedValueOnce({ id: 'appr-3', status: 'REJECTED' });

        await server.handlers.get('reject_remediation')!({ id: 'appr-3', approverEmail: 'ops@example.com' });

        expect(apiPost).toHaveBeenCalledWith('/approvals/appr-3/reject', {
            approverEmail: 'ops@example.com',
            reason: undefined,
        });
    });
});
