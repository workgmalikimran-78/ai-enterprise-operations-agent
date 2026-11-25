# Testing

## Backend

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
cd backend
mvn test                              # unit tests, no Docker needed
```

Testcontainers-backed tests (`AiopsApplicationTests`, `AnomalyDetectionIntegrationTest`,
`WarehouseEtlJobIntegrationTest`) additionally need, on a Rancher Desktop setup:
```bash
export DOCKER_HOST="unix://$HOME/.rd/docker.sock"
export TESTCONTAINERS_RYUK_DISABLED=true
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
```
The third one is easy to miss: any container that *itself* bind-mounts the docker socket (LocalStack
included, for its Lambda executor) fails with "operation not supported" unless this is set — Ryuk
needs only the first two, but LocalStack-backed tests need all three.

`mvn -o test` (offline) is faster for repeat local runs once dependencies are cached.

New integration tests that need object storage should use `LocalStackContainer` (`org.testcontainers
.containers.localstack.LocalStackContainer`, package `org.testcontainers:localstack`) — see
`WarehouseEtlJobIntegrationTest` for the full `@DynamicPropertySource` wiring pattern
(`aiops.lake.endpoint`/`access-key`/`secret-key` overridden to the container's values).

No shared Testcontainers base class exists — each integration test declares its own `@Container`
fields inline. Match that pattern rather than introducing a shared base class unless asked to
refactor it.

## Frontend

```bash
cd frontend
npx tsc --noEmit -p tsconfig.app.json   # typecheck
npm run build                            # full build (tsc -b && vite build)
```
No test runner is configured yet for the frontend — verify UI changes live (see
`CLAUDE.md`/this file's "Live verification" note) rather than assuming a test command exists.

## MCP server

```bash
cd mcp-server
npm run build       # tsc
npm test            # vitest run
```
Tests mock `fetch` directly (`src/api-client.test.ts`) and cover one representative tool per shape
(read tool, parameterless POST action, request-body-carrying mutation) rather than every tool —
all 29 tools follow one of those same three patterns, so don't add a mechanical per-tool test file
for a new tool unless it introduces a genuinely new shape.

When mocking `../api-client.js` with `vi.mock`, use the `importOriginal` partial-mock form to keep
the real `ApiError` class available for `instanceof` checks:
```ts
vi.mock(import('../api-client.js'), async (importOriginal) => {
    const actual = await importOriginal();
    return { ...actual, apiGet: vi.fn(), apiPost: vi.fn() };
});
```
A plain `vi.mock('../api-client.js', () => ({...}))` replaces the whole module, including the
`ApiError` export, and breaks any test that constructs or checks one.

## Live verification (required for anything user-facing)

Don't call a feature done from unit tests alone. Bring the stack up and hit it for real:
```bash
docker compose up -d postgres redis kafka opensearch lake   # wait for all healthy
docker compose up -d --build app frontend mcp-server
curl -sf http://localhost:8081/actuator/health
```
For frontend changes, actually load the page in the Browser tool and read its state back — don't
assume a component renders correctly just because it typechecks and builds.
