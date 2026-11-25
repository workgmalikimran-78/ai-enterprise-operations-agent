---
name: deploy
description: Use when deploying, running, or bringing up the AIOps platform - locally via docker-compose, or to a Kubernetes cluster via the k8s/ manifests. Covers the exact startup sequence and ordering, image builds, and the specific gotchas already hit once in this repo (Rancher Desktop's Docker socket, LocalStack's health-check status transition, k8s secret/RBAC setup). Trigger on requests like "deploy this", "run the stack", "set up k8s", "get this running", not just the literal word "deploy".
---

# Deploying the AIOps platform

Three deployables, always together: `backend/` (Spring Boot `app`), `frontend/` (React, served by
nginx), `mcp-server/` (Node/TypeScript). Two deploy targets below - pick based on what the user
asked for (local iteration → docker-compose; "cluster"/"k8s"/"production-like" → Kubernetes).

## Target 1: docker-compose (local dev/testing)

`app` has no healthcheck of its own, so infra must be confirmed healthy *before* starting it -
`depends_on: condition: service_healthy` isn't available for a dependency on `app` itself:

```bash
export DOCKER_HOST="unix://$HOME/.rd/docker.sock"      # Rancher Desktop, not the default socket
docker compose up -d postgres redis kafka opensearch lake
# wait for ALL FIVE to report "healthy", not just "running" - check with:
docker compose ps
docker compose up -d --build app frontend mcp-server
```

Verify (don't just trust exit codes):
```bash
curl -sf http://localhost:8081/actuator/health       # app
curl -sf http://localhost:8090/healthz               # mcp-server
curl -sf http://localhost:3001                       # frontend
```

**Known gotcha**: the `lake` service (LocalStack, standing in for MinIO - see `CLAUDE.md`) reports
`"s3": "available"` right after startup and `"s3": "running"` after its first real request; the
healthcheck already matches both. Don't "simplify" it back to matching only `available`.

**First-time setup**: `cp .env.example .env` and fill in an LLM key (`OPENAI_API_KEY` or
`ANTHROPIC_API_KEY`, matching `AIOPS_LLM_PROVIDER`) - the app starts fine without one, but
investigation/recommendation agents 503 until it's set.

## Target 2: Kubernetes (`k8s/`)

Plain YAML, no Helm chart. Mirrors docker-compose's local setup (not production-hardened - see
`k8s/README.md`). Full checklist: @.claude/skills/deploy/k8s-checklist.md

## After deploying either target

Point out the right next step based on what the user actually asked for - don't just report "it's
up": if they wanted to test a specific change, exercise it (see `README.md`'s numbered walkthrough,
`/smoke-test`, or `/backend-test`); if they just wanted it running, give them the three URLs
(dashboard `:3001`, API `:8081`, MCP `:8090/mcp`).
