import { ApiError } from './api-client.js';

/** Wraps a successful payload as the MCP content shape every tool handler here returns. */
export function toolResult(data: unknown) {
    return {
        content: [{ type: 'text' as const, text: JSON.stringify(data, null, 2) }],
        structuredContent: isPlainObject(data) ? (data as Record<string, unknown>) : undefined,
    };
}

/**
 * Maps a caught error to an isError:true tool result, preserving the backend's own message
 * (ApiError) rather than collapsing everything to a generic failure string.
 */
export function toolError(e: unknown) {
    const message = e instanceof ApiError
        ? `Backend returned ${e.status}: ${e.message}${e.fieldErrors.length ? ` (${e.fieldErrors.join('; ')})` : ''}`
        : e instanceof Error
            ? e.message
            : String(e);
    return {
        content: [{ type: 'text' as const, text: message }],
        isError: true,
    };
}

function isPlainObject(value: unknown): value is Record<string, unknown> {
    return typeof value === 'object' && value !== null && !Array.isArray(value);
}
