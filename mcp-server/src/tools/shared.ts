import * as z from 'zod/v4';

/** Shared page/size fragment, spread into each list tool's inputSchema. */
export const paginationParams = {
    page: z.number().int().min(0).optional().describe('Zero-based page number, default 0'),
    size: z.number().int().min(1).max(200).optional().describe('Page size, default 20'),
};
