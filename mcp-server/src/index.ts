import { createMcpExpressApp } from '@modelcontextprotocol/express';
import { NodeStreamableHTTPServerTransport } from '@modelcontextprotocol/node';
import { buildServer } from './server.js';
import { requireSharedSecret } from './auth.js';

const PORT = Number(process.env.PORT ?? 3000);
const allowedHosts = (process.env.MCP_ALLOWED_HOSTS ?? 'localhost,127.0.0.1')
    .split(',')
    .map((h) => h.trim())
    .filter(Boolean);

const app = createMcpExpressApp({ host: '0.0.0.0', allowedHosts });

app.get('/healthz', (_req, res) => {
    res.json({ status: 'UP' });
});

const apiKey = process.env.MCP_API_KEY;
if (apiKey) {
    app.use('/mcp', requireSharedSecret(apiKey));
}

app.post('/mcp', async (req, res) => {
    // Stateless: a fresh McpServer + transport per request, matching this server's role as a
    // thin, no-session proxy over the backend's own (also stateless) REST API.
    const server = buildServer();
    const transport = new NodeStreamableHTTPServerTransport({ sessionIdGenerator: undefined });
    await server.connect(transport);
    await transport.handleRequest(req, res, req.body);
});

app.listen(PORT, '0.0.0.0', () => {
    const authNote = apiKey ? 'bearer token required' : 'NO AUTH - MCP_API_KEY not set';
    console.log(`aiops-mcp-server listening on :${PORT}/mcp (${authNote})`);
    console.log(`Proxying to ${process.env.AIOPS_API_BASE_URL ?? 'http://localhost:8081/api/v1'}`);
});
