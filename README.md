# AI Enterprise Operations Agent (AIOps) — Phases 1-7

Enterprise AI Operations Agent platform. This checkpoint covers **Phases 1-7**: project
foundation, log monitoring, anomaly detection, AI-driven root cause analysis, remediation
recommendations, human-in-the-loop approval, secure Kubernetes execution + verification, and the
React dashboard. CI/CD and the broader security/E2E test suite (Phase 8) are still to come.

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

## What's implemented vs. deferred

**Implemented (Phases 1-7):**
- Full database schema (Flyway `V1`-`V7`) and JPA entities for the entire platform
- Log ingestion (REST → Postgres + Kafka → OpenSearch, decoupled so OpenSearch outages never
  block ingestion); rule-based and statistical (z-score) anomaly detection backed by Redis
  sliding-window counters; incident correlation (new anomalies join an existing open incident
  instead of spawning duplicates)
- Investigation agent (gathers verifiable evidence, no LLM) + RCA agent (calls an LLM via a
  provider-agnostic `ChatClient`, structured output, explicit prompt-injection guardrails)
- Remediation agent constrained to a fixed, pre-approved action catalog — risk/approval level is
  always derived deterministically server-side, never trusted from LLM output
- Human-in-the-loop approval workflow (pending/approved/rejected/expired, with a scheduled expiry
  job) and secure, parameterized Kubernetes execution tools (health/logs/status/restart/scale/
  rollback) behind a kill switch, namespace allowlist, rate limit, and idempotency key — all
  live-verified against a real k3s cluster
- Post-remediation verification agent comparing error volume before/after, on a schedule
- React + TypeScript dashboard: Overview, Live Monitoring, Incidents (list + detail), Remediation
  Center, Audit Center — all wired to the real backend, verified live in a browser

**Deferred (Phase 8, not yet implemented):** GitHub Actions CI/CD, Kubernetes deployment
manifests/Helm charts, broader security/E2E test scenarios from the spec, OAuth2/JWT/RBAC
(current `SecurityConfig` is an intentionally permissive placeholder — see its class comment).
