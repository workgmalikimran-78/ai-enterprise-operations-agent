import { beforeEach, describe, expect, it, vi } from 'vitest';
import { apiGet, apiPost, ApiError } from '../api-client.js';
import { registerIncidentTools } from './incidents.js';

vi.mock(import('../api-client.js'), async (importOriginal) => {
    const actual = await importOriginal();
    return { ...actual, apiGet: vi.fn(), apiPost: vi.fn() };
});

/** Minimal fake McpServer that just captures each tool's handler, so it can be invoked directly. */
function fakeServer() {
    const handlers = new Map<string, (args: any) => Promise<any>>();
    return {
        registerTool: (name: string, _config: unknown, handler: (args: any) => Promise<any>) => {
            handlers.set(name, handler);
        },
        handlers,
    };
}

describe('incident tools', () => {
    beforeEach(() => {
        vi.clearAllMocks();
    });

    it('list_incidents (read tool): calls GET /incidents with the given params and returns the raw page', async () => {
        const server = fakeServer();
        registerIncidentTools(server as any);
        (apiGet as ReturnType<typeof vi.fn>).mockResolvedValueOnce({ content: [{ id: '1' }], totalElements: 1 });

        const result = await server.handlers.get('list_incidents')!({ status: 'OPEN', size: 5 });

        expect(apiGet).toHaveBeenCalledWith('/incidents', { status: 'OPEN', size: 5 });
        expect(result.isError).toBeUndefined();
        expect(JSON.parse(result.content[0].text)).toEqual({ content: [{ id: '1' }], totalElements: 1 });
    });

    it('investigate_incident (parameterless POST action tool): calls POST on the path-parameterized endpoint', async () => {
        const server = fakeServer();
        registerIncidentTools(server as any);
        (apiPost as ReturnType<typeof vi.fn>).mockResolvedValueOnce({ id: 'rca-1', summary: 'ok' });

        const result = await server.handlers.get('investigate_incident')!({ id: 'incident-123' });

        expect(apiPost).toHaveBeenCalledWith('/incidents/incident-123/investigate');
        expect(JSON.parse(result.content[0].text)).toEqual({ id: 'rca-1', summary: 'ok' });
    });

    it('surfaces a thrown ApiError as isError:true with the backend message', async () => {
        const server = fakeServer();
        registerIncidentTools(server as any);
        const realApiError = new ApiError({
            timestamp: '2026-11-25T00:00:00Z', status: 404, error: 'Not Found',
            message: 'Incident not found: xyz', path: '/api/v1/incidents/xyz', correlationId: null, fieldErrors: [],
        });
        (apiGet as ReturnType<typeof vi.fn>).mockRejectedValueOnce(realApiError);

        const result = await server.handlers.get('get_incident')!({ id: 'xyz' });

        expect(result.isError).toBe(true);
        expect(result.content[0].text).toBe('Backend returned 404: Incident not found: xyz');
    });
});
