import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { apiGet, apiPost, ApiError } from './api-client.js';

describe('api-client', () => {
    const originalFetch = global.fetch;

    beforeEach(() => {
        global.fetch = vi.fn();
    });

    afterEach(() => {
        global.fetch = originalFetch;
        vi.restoreAllMocks();
    });

    it('apiGet builds the URL with query params and parses a successful JSON response', async () => {
        const mockFetch = global.fetch as ReturnType<typeof vi.fn>;
        mockFetch.mockResolvedValueOnce(
            new Response(JSON.stringify({ hello: 'world' }), { status: 200 }),
        );

        const result = await apiGet('/incidents', { status: 'OPEN', page: 0, size: 20 });

        expect(result).toEqual({ hello: 'world' });
        const calledUrl = new URL(mockFetch.mock.calls[0][0] as string);
        expect(calledUrl.pathname).toBe('/api/v1/incidents');
        expect(calledUrl.searchParams.get('status')).toBe('OPEN');
        expect(calledUrl.searchParams.get('page')).toBe('0');
        expect(calledUrl.searchParams.get('size')).toBe('20');
    });

    it('apiGet omits undefined params from the query string', async () => {
        const mockFetch = global.fetch as ReturnType<typeof vi.fn>;
        mockFetch.mockResolvedValueOnce(new Response(JSON.stringify([]), { status: 200 }));

        await apiGet('/anomalies', { status: undefined, page: 0 });

        const calledUrl = new URL(mockFetch.mock.calls[0][0] as string);
        expect(calledUrl.searchParams.has('status')).toBe(false);
        expect(calledUrl.searchParams.get('page')).toBe('0');
    });

    it('apiPost sends a JSON body with the right method and content-type', async () => {
        const mockFetch = global.fetch as ReturnType<typeof vi.fn>;
        mockFetch.mockResolvedValueOnce(
            new Response(JSON.stringify({ id: 'abc' }), { status: 200 }),
        );

        await apiPost('/approvals/abc/approve', { approverEmail: 'ops@example.com' });

        const [url, init] = mockFetch.mock.calls[0];
        expect(new URL(url as string).pathname).toBe('/api/v1/approvals/abc/approve');
        expect(init?.method).toBe('POST');
        expect(init?.headers).toMatchObject({ 'Content-Type': 'application/json' });
        expect(JSON.parse(init?.body as string)).toEqual({ approverEmail: 'ops@example.com' });
    });

    it('apiPost with no body sends no content-type/body', async () => {
        const mockFetch = global.fetch as ReturnType<typeof vi.fn>;
        mockFetch.mockResolvedValueOnce(new Response(JSON.stringify({ ok: true }), { status: 200 }));

        await apiPost('/warehouse/etl/run');

        const [, init] = mockFetch.mock.calls[0];
        expect(init?.body).toBeUndefined();
    });

    it('throws ApiError with the backend ApiError body fields on a 404', async () => {
        const mockFetch = global.fetch as ReturnType<typeof vi.fn>;
        const body = {
            timestamp: '2026-11-25T00:00:00Z',
            status: 404,
            error: 'Not Found',
            message: 'Incident not found: xyz',
            path: '/api/v1/incidents/xyz',
            correlationId: null,
            fieldErrors: [],
        };
        mockFetch.mockResolvedValueOnce(new Response(JSON.stringify(body), { status: 404 }));

        await expect(apiGet('/incidents/xyz')).rejects.toMatchObject({
            status: 404,
            message: 'Incident not found: xyz',
        });
    });

    it('throws ApiError with fieldErrors on a 400 validation failure', async () => {
        const mockFetch = global.fetch as ReturnType<typeof vi.fn>;
        const body = {
            timestamp: '2026-11-25T00:00:00Z',
            status: 400,
            error: 'Bad Request',
            message: 'Validation failed',
            path: '/api/v1/approvals/abc/approve',
            correlationId: 'corr-1',
            fieldErrors: ['approverEmail: must not be blank'],
        };
        mockFetch.mockResolvedValueOnce(new Response(JSON.stringify(body), { status: 400 }));

        try {
            await apiPost('/approvals/abc/approve', { approverEmail: '' });
            expect.unreachable('should have thrown');
        } catch (e) {
            expect(e).toBeInstanceOf(ApiError);
            expect((e as ApiError).fieldErrors).toEqual(['approverEmail: must not be blank']);
        }
    });

    it('throws ApiError on a 503 (LlmUnavailableException) response', async () => {
        const mockFetch = global.fetch as ReturnType<typeof vi.fn>;
        const body = {
            timestamp: '2026-11-25T00:00:00Z',
            status: 503,
            error: 'Service Unavailable',
            message: 'LLM provider unavailable: missing API key',
            path: '/api/v1/incidents/abc/investigate',
            correlationId: null,
            fieldErrors: [],
        };
        mockFetch.mockResolvedValueOnce(new Response(JSON.stringify(body), { status: 503 }));

        await expect(apiPost('/incidents/abc/investigate')).rejects.toMatchObject({ status: 503 });
    });
});
