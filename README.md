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

## Quick health check

```bash
curl http://localhost:8081/actuator/health
```

Swagger UI: http://localhost:8081/swagger-ui.html

## Application flow — end-to-end walkthrough

The platform is a pipeline: **logs → anomaly → incident → RCA → remediation recommendation →
human approval → execution → verification**. Every stage is a real REST call and shows up in the
dashboard (`npm run dev` in `frontend/`, see [Running the frontend](#running-the-frontend)).

### 1. Ingest logs

```bash
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

`service` doubles as the application identifier — the first time a given `service`/`environment`
pair is seen, its `Application`/`Environment`/default `Tenant` rows are auto-created (see
`ApplicationResolver`). Check it landed in OpenSearch via the search call above, or watch it
stream into the **Live Monitoring** page.

### 2. Anomaly detection

Sending ≥10 `ERROR` events for the *same* service + exception type within 5 minutes
(`aiops.anomaly.rule.threshold` / `window-minutes`) raises a rule-based anomaly:

```bash
for i in $(seq 1 10); do
  curl -s -o /dev/null -X POST http://localhost:8081/api/v1/logs/ingest -H "Content-Type: application/json" -d "{
    \"service\": \"order-service\", \"environment\": \"production\", \"level\": \"ERROR\",
    \"traceId\": \"trace-$i\", \"message\": \"Database connection timeout\",
    \"exception\": { \"type\": \"SQLTransientConnectionException\", \"message\": \"timed out\" }
  }"
done
curl "http://localhost:8081/api/v1/anomalies?status=OPEN"
```

**Note on severity:** the rule-based detector's dedup logic always fires exactly once, at the
moment the count first reaches the threshold — so under normal sequential traffic its anomalies
always land at `MEDIUM` severity (this is correct, documented behavior; see
`RuleBasedAnomalyDetector` and `AnomalyDetectionIntegrationTest`). Only `HIGH`/`CRITICAL` severity
opens an `Incident` (`IncidentService`), so a single burst like the one above will **not**
produce an incident by itself. The statistical (z-score) detector can reach `HIGH`/`CRITICAL`
organically, but needs ~15-20 minutes of sustained traffic to accumulate a baseline first — not
practical for a quick walkthrough. To exercise the rest of the pipeline immediately, skip ahead to
step 3a below instead of waiting.

### 3a. Fast-forward: seed a `HIGH`-severity incident directly

This is exactly how Phase 6's K8s execution tools were live-tested against a real cluster — it's
a legitimate way to drive the pipeline without waiting on the statistical detector:

```bash
docker compose exec -T postgres psql -U aiops -d aiops -c "
INSERT INTO incidents (tenant_id, application_id, title, status, severity, opened_at)
SELECT tenant_id, id, 'order-service: database connection exhaustion', 'OPEN', 'HIGH', now()
FROM applications WHERE name='order-service'
RETURNING id;"
```

Copy the returned `id` as `INCIDENT_ID` for the steps below. (`GET /api/v1/incidents` lists
everything, `GET /api/v1/incidents/{id}` fetches one — both also drive the **Incidents** page.)

### 3b. Investigate — AI root cause analysis

```bash
curl -X POST http://localhost:8081/api/v1/incidents/$INCIDENT_ID/investigate
curl http://localhost:8081/api/v1/incidents/$INCIDENT_ID/analysis
```

Needs `OPENAI_API_KEY` set on the `app` container/process — without it, this returns a 503
(`LlmUnavailableException`) wrapping the underlying auth failure rather than crashing the app.
The `InvestigationAgent` gathers evidence
first (linked anomalies, recent error logs, historical incidents, matching runbooks — no LLM call
yet), then `RootCauseAnalysisAgent` sends that evidence to the LLM with an explicit guardrail:
log/exception content is labeled as untrusted DATA, never instructions (see
`RootCauseAnalysisAgentTest` for a test that captures the real prompt and asserts this). The
response includes a confidence score and distinguishes confirmed facts from hypotheses. Shows up
on the incident detail page's "AI Root Cause Analysis" card.

### 4. Generate a remediation recommendation

```bash
curl -X POST http://localhost:8081/api/v1/incidents/$INCIDENT_ID/recommendations
curl http://localhost:8081/api/v1/incidents/$INCIDENT_ID/recommendations
```

The LLM picks one action from a **fixed catalog** (`RemediationActionType`) — it cannot invent an
arbitrary command. Risk level and required approval level are derived deterministically from
whichever action was chosen, never trusted from the LLM's own output. Read-only/diagnostic actions
(`CHECK_APPLICATION_HEALTH`, `RETRIEVE_RECENT_LOGS`, `CHECK_SERVICE_STATUS`,
`INVESTIGATE_FURTHER`) need no approval; anything that mutates a deployment
(`RESTART_DEPLOYMENT`, `SCALE_DEPLOYMENT`, `ROLLBACK_DEPLOYMENT`) always requires one. Copy the
returned recommendation `id` as `REC_ID`.

### 5. Approve or reject

If the recommendation needed approval, `GET /api/v1/approvals?status=PENDING` will show it:

```bash
curl "http://localhost:8081/api/v1/approvals?status=PENDING"
curl -X POST http://localhost:8081/api/v1/approvals/{approvalId}/approve \
  -H "Content-Type: application/json" -d '{"approverEmail":"ops@example.com"}'
# or: .../reject -d '{"approverEmail":"ops@example.com","reason":"too risky right now"}'
```

Approvals expire on a schedule (`aiops.approval.ttl-hours`, default 24) and can only be decided
once — a second approve/reject on the same request is rejected with a 400. All of this is the
**Remediation Center** page (approve/reject buttons, execute button once approved).

### 6. Execute

```bash
curl -X POST http://localhost:8081/api/v1/remediations/$REC_ID/execute
curl http://localhost:8081/api/v1/remediations/$REC_ID/status
```

Runs the real, predefined Kubernetes tool (`KubernetesOperationalTools`) for the chosen action
against `aiops.execution.default-namespace` (default `default`), targeting a deployment named
after the `service`. Every mutating call passes through `ExecutionGuard` first: the emergency kill
switch, a namespace allowlist, and a per-target rate limit. Calling `/execute` again with the same
`REC_ID` is safe — it returns the already-recorded result instead of re-running (idempotency key =
recommendation ID).

Emergency kill switch for **all** mutating K8s actions, cluster-wide:
```bash
curl -X POST http://localhost:8081/api/v1/admin/execution/disable
curl -X POST http://localhost:8081/api/v1/admin/execution/enable
curl http://localhost:8081/api/v1/admin/execution/status
```

### 7. Verification (automatic)

A scheduled job (`aiops.verification.check-interval-minutes`, default 5) picks up executions
completed ≥5 minutes ago that haven't been verified yet, compares error volume before/after via
`VerificationAgent`, and updates the incident's status: `RESOLVED` (zero errors since), `MITIGATED`
(meaningful drop but not zero), or left as-is (`UNRESOLVED` — no automatic change, needs more
action). Nothing to call manually; just re-check `GET /api/v1/incidents/$INCIDENT_ID` a few minutes
later, or watch its status badge update on the **Incidents** page.

### Everything, at a glance

```bash
curl http://localhost:8081/api/v1/dashboard/overview    # counts, MTTD/MTTR, AI investigation stats
curl http://localhost:8081/api/v1/dashboard/metrics      # breakdowns by severity/status/type
curl http://localhost:8081/api/v1/audit                  # who/what/when audit trail
curl http://localhost:8081/api/v1/audit/agent-executions # every agent run, success or failure
```

Same data as the **Overview** and **Audit Center** pages.

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
