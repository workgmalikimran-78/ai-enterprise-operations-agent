# AI Enterprise Operations Agent (AIOps) — Phases 1-8

Enterprise AI Operations Agent platform. All 8 phases from the original spec are implemented:
project foundation, log monitoring, anomaly detection, AI-driven root cause analysis, remediation
recommendations, human-in-the-loop approval, secure Kubernetes execution + verification, the React
dashboard, and CI/CD + Kubernetes deployment manifests. See "What's implemented" below for the
honest scope of each phase - some things (OAuth2/JWT, a Helm chart, the full E2E scenario matrix)
are deliberately deferred and called out explicitly rather than silently skipped.

## Stack
**Backend:** Java 21, Spring Boot 3.3.5, PostgreSQL, Redis, Kafka (KRaft), OpenSearch, Spring AI
(OpenAI), Fabric8 Kubernetes client. See `backend/pom.xml` for the full dependency list.
**Frontend:** Vite, React 19, TypeScript, Tailwind CSS v4, React Router, TanStack Query, Recharts.

## Prerequisites
- Java 21 (this repo was built against Homebrew's `openjdk@21`)
- Node 20+ (tested against Node 26)
- Docker (tested against Rancher Desktop in `moby`/dockerd mode)
- A local Kubernetes cluster for Phase 6's execution tools (tested against Rancher Desktop's
  bundled k3s) — optional for Phases 1-5/7, required to actually run restart/scale/rollback
- An `OPENAI_API_KEY` for Phases 4-5's AI agents — optional otherwise; the app starts fine without
  one, agents just fail with a clear error when invoked

## Running the backend locally via Docker Compose

```bash
docker compose up -d postgres redis kafka opensearch   # wait for all to report healthy
OPENAI_API_KEY=sk-... docker compose build app
OPENAI_API_KEY=sk-... docker compose up -d app
```

Services and their **host-mapped** ports (remapped from Kafka/Postgres/Redis/OpenSearch
defaults to avoid clashing with other projects running on this machine):

| Service | Host port | Notes |
|---|---|---|
| App | 8081 | `SPRING_PROFILES_ACTIVE=local` |
| Postgres | 5433 | db `aiops`, user/pass `aiops`/`aiops` |
| Redis | 6380 | |
| Kafka | 9093 (external), 9092 (internal) | KRaft mode, no Zookeeper. Dual listeners so both containers and host-based clients can connect |
| OpenSearch | 9201 | security plugin disabled for local dev |
| OpenSearch Dashboards | 5602 | |

Inside the Docker network, containers talk to each other on the standard internal ports
(`postgres:5432`, `redis:6379`, `kafka:9092`, `opensearch:9200`) — only the host-side mappings
are shifted.

If you run the app **outside** Docker (e.g. via your IDE or `mvn spring-boot:run`) against the
dockerized dependencies, `application-local.yml` already points at the host-mapped ports above.
This is also how Phase 6's K8s tools were live-tested — the app needs a real `~/.kube/config`,
which a container doesn't have by default.

## Running the frontend

```bash
cd frontend
npm install
npm run dev     # http://localhost:5173, proxies /api to localhost:8081
```

## Verifying it works

```bash
curl http://localhost:8081/actuator/health
curl -X POST http://localhost:8081/api/v1/logs/ingest -H "Content-Type: application/json" -d '{
  "service": "order-service",
  "environment": "production",
  "level": "ERROR",
  "traceId": "abc-123",
  "message": "Database connection timeout",
  "exception": { "type": "SQLTransientConnectionException", "message": "timed out" }
}'
curl "http://localhost:8081/api/v1/logs/search?service=order-service"
```

Swagger UI: http://localhost:8081/swagger-ui.html

Sending ≥10 `ERROR` events for the same service + exception type within 5 minutes will raise a
rule-based anomaly (`aiops.anomaly.rule.threshold`, default 10); anomalies at `HIGH`/`CRITICAL`
severity open an incident. From there:

```bash
curl -X POST http://localhost:8081/api/v1/incidents/{id}/investigate   # RCA agent (needs OPENAI_API_KEY)
curl -X POST http://localhost:8081/api/v1/incidents/{id}/recommendations  # remediation agent
curl -X POST http://localhost:8081/api/v1/approvals/{id}/approve -d '{"approverEmail":"ops@example.com"}'
curl -X POST http://localhost:8081/api/v1/remediations/{id}/execute   # runs the real K8s tool
```

Emergency kill switch for all mutating K8s actions: `POST /api/v1/admin/execution/disable` /
`/enable`.

## Running the test suite

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
cd backend
mvn test
```

Unit tests run without Docker. The Testcontainers-backed tests (`AiopsApplicationTests`,
`AnomalyDetectionIntegrationTest`) need a running Docker daemon — if using Rancher Desktop, set:

```bash
export DOCKER_HOST="unix://$HOME/.rd/docker.sock"
export TESTCONTAINERS_RYUK_DISABLED=true   # Ryuk's socket bind-mount fails against non-standard socket paths
```

## Kubernetes deployment

Plain-YAML manifests in `k8s/` (Postgres/Redis/Kafka/OpenSearch + the app, with RBAC scoped to
exactly what the execution tools need). See `k8s/README.md` for how to apply them. No Helm chart
yet — packaging these as one is a natural next step, not done here.

## CI

`.github/workflows/ci.yml` runs on every push/PR to `main`: backend tests (`mvn test`, including
the Testcontainers-backed ones — GitHub's `ubuntu-latest` runners have Docker available natively,
unlike this local macOS/Rancher Desktop setup which needed the workarounds above), a backend
package build, a frontend type-check + build, and a Docker image build sanity check.

## What's implemented vs. deferred

**Implemented (Phases 1-8):**
- Full database schema (Flyway `V1`-`V7`) and JPA entities for the entire platform
- Log ingestion (REST → Postgres + Kafka → OpenSearch, decoupled so OpenSearch outages never
  block ingestion); rule-based and statistical (z-score) anomaly detection backed by Redis
  sliding-window counters; incident correlation (new anomalies join an existing open incident
  instead of spawning duplicates)
- Investigation agent (gathers verifiable evidence, no LLM) + RCA agent (calls an LLM via a
  provider-agnostic `ChatClient`, structured output, explicit prompt-injection guardrails —
  verified by a test that captures the actual prompts sent and asserts injected log content never
  reaches the system prompt)
- Remediation agent constrained to a fixed, pre-approved action catalog — risk/approval level is
  always derived deterministically server-side, never trusted from LLM output
- Human-in-the-loop approval workflow (pending/approved/rejected/expired, with a scheduled expiry
  job) and secure, parameterized Kubernetes execution tools (health/logs/status/restart/scale/
  rollback) behind a kill switch, namespace allowlist, rate limit, and idempotency key — all
  live-verified against a real k3s cluster
- Post-remediation verification agent comparing error volume before/after, on a schedule
- React + TypeScript dashboard: Overview, Live Monitoring, Incidents (list + detail), Remediation
  Center, Audit Center — all wired to the real backend, verified live in a browser
- GitHub Actions CI (backend tests, frontend build, Docker build check) and Kubernetes manifests

**Deferred (explicitly out of scope for this pass):**
- **OAuth2/JWT/RBAC** — `SecurityConfig` is an intentionally permissive placeholder (see its class
  comment for why). Every endpoint is open. Not safe to expose beyond local development as-is.
- **Helm chart** — the `k8s/` manifests are plain YAML; templating them into a chart with
  configurable values is a reasonable next step for multi-environment deployment.
- **The spec's full 8-scenario E2E matrix** as standalone automated tests — scenarios 1-3 and 6-8
  (DB connection failure, error spike, high CPU proxy via error rate, deployment regression,
  unauthorized remediation, approved-restart-then-verify) were exercised live end-to-end during
  development against the real cluster rather than re-encoded as separate automated E2E tests;
  scenario 4 (Kafka consumer lag) and 5 (memory exhaustion) have no corresponding anomaly-detector
  signal yet (the detector only watches error-rate/exception-count, not consumer lag or memory) -
  extending detection to those signals is the real prerequisite, not just writing more tests.
- **Role escalation / multi-tenant access-control tests** — not meaningful yet without real auth;
  will make sense once OAuth2/RBAC lands.
