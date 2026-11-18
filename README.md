# AI Enterprise Operations Agent (AIOps) — Phases 1-3

Enterprise AI Operations Agent platform. This checkpoint covers **Phases 1-3**: project
foundation, log monitoring, and anomaly detection. AI-driven root cause analysis, remediation,
approval workflows, secure Kubernetes execution, and the React dashboard are later phases.

## Stack
Java 21, Spring Boot 3.3.5, PostgreSQL, Redis, Kafka (KRaft), OpenSearch. See
`backend/pom.xml` for the full dependency list (Spring AI and Fabric8 Kubernetes client are
already present for Phases 4-6 but not wired up yet).

## Prerequisites
- Java 21 (this repo was built against Homebrew's `openjdk@21`)
- Docker (tested against Rancher Desktop in `moby`/dockerd mode)

## Running locally via Docker Compose

```bash
docker compose up -d postgres redis kafka opensearch   # wait for all to report healthy
docker compose build app
docker compose up -d app
```

Services and their **host-mapped** ports (remapped from Kafka/Postgres/Redis/OpenSearch
defaults to avoid clashing with other projects running on this machine):

| Service | Host port | Notes |
|---|---|---|
| App | 8081 | `SPRING_PROFILES_ACTIVE=local` |
| Postgres | 5433 | db `aiops`, user/pass `aiops`/`aiops` |
| Redis | 6380 | |
| Kafka | 9093 | KRaft mode, no Zookeeper |
| OpenSearch | 9201 | security plugin disabled for local dev |
| OpenSearch Dashboards | 5602 | |

Inside the Docker network, containers talk to each other on the standard internal ports
(`postgres:5432`, `redis:6379`, `kafka:9092`, `opensearch:9200`) — only the host-side mappings
are shifted.

If you run the app **outside** Docker (e.g. via your IDE) against the dockerized dependencies,
`application-local.yml` already points at the host-mapped ports above.

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
severity open an incident.

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

**Implemented (Phases 1-3):** full database schema (Flyway `V1`-`V5`) and JPA entities for the
entire platform; log ingestion (REST → Postgres + Kafka → OpenSearch, decoupled so OpenSearch
outages never block ingestion); rule-based and statistical (z-score) anomaly detection backed by
Redis sliding-window counters; incident correlation (new anomalies join an existing open incident
instead of spawning duplicates).

**Deferred (Phases 4-8, not yet implemented):** AI root-cause-analysis/remediation agents, human
approval workflow, Kubernetes execution tools, post-remediation verification, OAuth2/JWT/RBAC
(current `SecurityConfig` is an intentionally permissive placeholder), React dashboard, CI/CD,
Kubernetes manifests.
