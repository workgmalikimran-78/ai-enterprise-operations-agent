---
description: Bring the full docker-compose stack up and verify it end-to-end
---

Bring up the AIOps stack and verify it end-to-end. Don't assume success from exit codes alone —
confirm each stage with real data.

1. Check if it's already running first: `curl -sf http://localhost:8081/actuator/health`. If that
   succeeds, skip straight to step 4.
2. Bring up infra and wait for all of them to report `healthy` (not just "started" — `app` depends
   on this but has no healthcheck of its own to gate on):
   ```bash
   export DOCKER_HOST="unix://$HOME/.rd/docker.sock"
   docker compose up -d postgres redis kafka opensearch lake
   ```
3. Bring up the app, frontend, and mcp-server:
   ```bash
   docker compose up -d --build app frontend mcp-server
   ```
4. Verify, in order, reporting pass/fail for each rather than just the last one:
   - `curl -sf http://localhost:8081/actuator/health` → expect `"status":"UP"`
   - `curl -s http://localhost:8081/api/v1/dashboard/overview` → expect real JSON, not an error
   - `curl -s http://localhost:8081/api/v1/warehouse/catalog | python3 -c "import json,sys; print(len(json.load(sys.stdin)))"` → expect `15`
   - `curl -sf http://localhost:8090/healthz` → expect `{"status":"UP"}`
   - One real MCP tool call through `http://localhost:8090/mcp` (`tools/call` for
     `get_dashboard_overview`) → expect the same data as the dashboard curl above, not an error
   - `curl -sf http://localhost:3001` → expect the React app's HTML shell

Report a short pass/fail summary per stage at the end, not just "it's up."
