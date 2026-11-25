# AIOps MCP server

A standalone [Model Context Protocol](https://modelcontextprotocol.io) server that exposes this
platform's REST API (`backend/`) as MCP tools, so any MCP client (Claude Desktop, Claude Code, etc.)
can query or drive the AIOps platform directly — list incidents, investigate one, approve a
remediation, run the warehouse ETL job — without going through the React dashboard or raw curl.

It's a thin, stateless HTTP proxy: every tool calls one backend REST endpoint and returns its JSON
response. No database, no session state, no backend code changes. See
[ARCHITECTURE.md](../ARCHITECTURE.md) (MCP section) for the full tool list, the two backend
pagination shapes it has to handle, and the safety reasoning behind which tools are marked
`destructiveHint`.

## Running it

Via docker-compose (recommended - see the repo root README):
```bash
docker compose up -d --build mcp-server
```
Listens on `http://localhost:8090/mcp` (health check at `/healthz`).

Standalone, against a backend running elsewhere:
```bash
npm install
npm run build
AIOPS_API_BASE_URL=http://localhost:8081/api/v1 npm start
```

## Configuration

| Env var | Default | Purpose |
|---|---|---|
| `AIOPS_API_BASE_URL` | `http://localhost:8081/api/v1` | Backend REST API base URL |
| `PORT` | `3000` | Port to listen on |
| `MCP_ALLOWED_HOSTS` | `localhost,127.0.0.1` | Comma-separated hostnames allowed past the SDK's DNS-rebinding `Host` header check |
| `MCP_API_KEY` | unset (no auth) | Optional shared-secret bearer token required on `/mcp` - **not** OAuth, just a static string comparison. Several tools here execute real cluster mutations against a backend that itself has no auth yet; set this before exposing port 8090 beyond a trusted local network |

## Connecting an MCP client

**Claude Code:**
```bash
claude mcp add --transport http aiops http://localhost:8090/mcp
```
(add `--header "Authorization: Bearer <your MCP_API_KEY>"` if you've set one)

**Claude Desktop** (`claude_desktop_config.json`):
```json
{
  "mcpServers": {
    "aiops": {
      "url": "http://localhost:8090/mcp"
    }
  }
}
```

## Development

```bash
npm install
npm run dev     # tsc --watch
npm test        # vitest
```

Tests mock `fetch` directly (`src/api-client.test.ts`) and test a representative tool per pattern
(`src/tools/incidents.test.ts`, `src/tools/approvals.test.ts`) rather than mechanically re-testing
all 29 tools, which all follow one of those same shapes.

## Deliberately not wrapped

`POST /api/v1/logs/upload` (multipart file upload) has no MCP tool — binary input over a tool call
is disproportionate complexity for a case `ingest_log` already covers one event at a time. Called
out here as a deliberate scope decision, not an oversight.
