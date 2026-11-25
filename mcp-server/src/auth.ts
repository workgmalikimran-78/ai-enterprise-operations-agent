import type { NextFunction, Request, Response } from 'express';

/**
 * A plain shared-secret bearer check - NOT OAuth (the SDK's own requireBearerAuth expects a real
 * OAuthTokenVerifier returning clientId/scopes/expiresAt, which doesn't fit a single shared secret).
 * Off entirely when MCP_API_KEY is unset, matching this project's existing pattern of tolerating a
 * missing secret rather than crashing (see backend LlmConfig) - here that means "no protection",
 * which is fine for local/trusted-network use and should be set before exposing this anywhere else.
 */
export function requireSharedSecret(apiKey: string) {
    return (req: Request, res: Response, next: NextFunction) => {
        const header = req.header('authorization') ?? '';
        const token = header.startsWith('Bearer ') ? header.slice('Bearer '.length) : undefined;
        if (token !== apiKey) {
            res.status(401).json({ error: 'Unauthorized: missing or invalid bearer token' });
            return;
        }
        next();
    };
}
