# CLAUDE.md

Guidance for Claude Code (or any agent) working in this repo. Read `README.md` for setup/usage and
`ARCHITECTURE.md` for the full design — this file is the short list of things that aren't obvious
from reading the code and cost real debugging time to discover. Detailed, modular conventions are
imported below rather than duplicated here.

@.claude/rules/code-style.md
@.claude/rules/testing.md
@.claude/rules/api-conventions.md

## What this is

An AIOps platform: logs → anomaly detection → incident → LLM root-cause analysis → LLM remediation
proposal → human approval → Kubernetes execution → verification. Plus a data warehouse/governance
layer (`com.ai.aiops.warehouse`) and a standalone MCP server (`mcp-server/`) exposing the whole
REST API as tools. Three deployables: `backend/` (Spring Boot), `frontend/` (React), `mcp-server/`
(Node/TypeScript) — all three in `docker-compose.yml` and `k8s/`.

## Before touching anything

Check whether the stack is already running before starting it:
```bash
curl -sf http://localhost:8081/actuator/health
```
If you bring it up yourself, bring up infra first and wait for health, *then* the app — `app` has
no healthcheck of its own, so `depends_on: condition: service_healthy` isn't available for it:
```bash
docker compose up -d postgres redis kafka opensearch lake   # wait for all `healthy`
docker compose up -d --build app frontend mcp-server
```

## Environment quirks that will otherwise waste time

- **Java 21 is required**, but the default `java`/`mvn` on this machine resolve to 17. Always:
  ```bash
  export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
  ```
- **Docker runs via Rancher Desktop**, not the default socket path:
  ```bash
  export DOCKER_HOST="unix://$HOME/.rd/docker.sock"
  ```
  (full Testcontainers env var list in `.claude/rules/testing.md`)

## The data lake is LocalStack, not MinIO — on purpose

`minio/minio` stopped being pullable anonymously from Docker Hub/Quay/GHCR (a 2024-2025 licensing
change) partway through building the warehouse feature. The `lake` service in `docker-compose.yml`
and `k8s/14-lake.yaml` run LocalStack's S3 emulation instead — same generic AWS S3 SDK either way,
so swapping to real S3/GCS/a self-hosted MinIO later needs zero Java/TS code changes, only an
`AIOPS_LAKE_ENDPOINT` change. Don't "fix" this back to `minio/minio` without checking whether that
image is pullable again first.

Its healthcheck matches LocalStack's `"s3": "available"` *or* `"running"` status — it starts as
`available` and flips to `running` after the first real request. Don't narrow that regex back to
just `available` (it was broken that way once already — see git history).

## No auth, anywhere, yet

`SecurityConfig` is `permitAll()` on every endpoint — intentional, documented placeholder (OAuth2/
JWT is explicitly deferred, see README's "What's implemented vs. deferred"). The MCP server
inherits this; its only protection is the optional `MCP_API_KEY` shared-secret check, off by
default. Don't add partial/inconsistent auth to one piece without addressing this properly — either
leave it as-is or do the real OAuth2/RBAC work across the whole platform.

## This repo's own MCP server is wired into Claude Code

`.mcp.json` points this project at `mcp-server/` (`http://localhost:8090/mcp`) — once the stack is
up, tools like `list_incidents`/`get_dashboard_overview`/`run_warehouse_etl` are available directly
instead of hand-rolled curl. If `MCP_API_KEY` is set (it's unset/no-auth by default locally), add an
`Authorization: Bearer <key>` header to `.mcp.json`'s `aiops` entry, or the tool calls will 401.

## Custom commands, agents & skills available in this repo

- `deploy` skill (`.claude/skills/deploy/`) — auto-triggers on "deploy this"/"run the stack"/"set up
  k8s"; covers both docker-compose and Kubernetes, with the k8s secrets/RBAC/image checklist in a
  companion file.
- `/smoke-test` — bring the whole stack up and verify it end-to-end (see `.claude/commands/`).
- `/backend-test` — run backend tests with the right env vars pre-set.
- `aiops-reviewer` subagent — project-aware code review (conventions above + the prompt-injection
  invariant below); use it instead of/alongside the generic `code-review` skill for backend changes.

## The one invariant that must never regress

`RootCauseAnalysisAgent` and `RemediationRecommendationAgent` label all log/exception content as
untrusted **DATA** in their system prompts — never instructions — verified by
`RootCauseAnalysisAgentTest` capturing the actual prompt sent. If you touch either agent's prompt
construction, re-run that test and don't let log content move into a position the model could read
as a directive.

## Where to look for more

- `README.md` — setup, the end-to-end walkthrough (logs → incident → remediation), test commands.
- `ARCHITECTURE.md` — full design, section by section (§13 data warehouse, §15 MCP server).
- `mcp-server/README.md` — MCP server config and how to connect a client to it.
