import type { ApiErrorBody } from './types.js';

const BASE_URL = process.env.AIOPS_API_BASE_URL ?? 'http://localhost:8081/api/v1';

/**
 * Thrown for any non-2xx response from the backend. Carries the exact ApiErrorBody
 * GlobalExceptionHandler produced, so tool handlers can surface the real backend message
 * (e.g. "Incident not found: ...") instead of a generic HTTP failure.
 */
export class ApiError extends Error {
    readonly status: number;
    readonly fieldErrors: string[];

    constructor(body: ApiErrorBody) {
        super(body.message);
        this.name = 'ApiError';
        this.status = body.status;
        this.fieldErrors = body.fieldErrors ?? [];
    }
}

function buildUrl(path: string, params?: Record<string, string | number | boolean | undefined>): string {
    const url = new URL(BASE_URL.replace(/\/$/, '') + path);
    if (params) {
        for (const [key, value] of Object.entries(params)) {
            if (value !== undefined) {
                url.searchParams.set(key, String(value));
            }
        }
    }
    return url.toString();
}

async function handleResponse<T>(response: Response): Promise<T> {
    if (response.status === 204) {
        return undefined as T;
    }
    const text = await response.text();
    const body = text.length > 0 ? JSON.parse(text) : undefined;
    if (!response.ok) {
        if (body && typeof body === 'object' && 'status' in body && 'message' in body) {
            throw new ApiError(body as ApiErrorBody);
        }
        throw new ApiError({
            timestamp: new Date().toISOString(),
            status: response.status,
            error: response.statusText,
            message: text || `Request failed with status ${response.status}`,
            path: response.url,
            correlationId: null,
            fieldErrors: [],
        });
    }
    return body as T;
}

export async function apiGet<T>(path: string, params?: Record<string, string | number | boolean | undefined>): Promise<T> {
    const response = await fetch(buildUrl(path, params));
    return handleResponse<T>(response);
}

export async function apiPost<T>(path: string, body?: unknown): Promise<T> {
    const response = await fetch(buildUrl(path), {
        method: 'POST',
        headers: body !== undefined ? { 'Content-Type': 'application/json' } : {},
        body: body !== undefined ? JSON.stringify(body) : undefined,
    });
    return handleResponse<T>(response);
}
