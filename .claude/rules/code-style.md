# Code style & conventions

## Backend (`backend/src/main/java/com/ai/aiops/`)

**Entities** (`domain/`):
- Extend `common.BaseEntity` (mutable, has `@UpdateTimestamp updatedAt`) or
  `common.BaseCreatedEntity` (append-only, no `updated_at`) — never hand-roll `id`/`createdAt`.
- `@Entity @Table(name = "snake_case_plural") @Getter @Setter @NoArgsConstructor` — no `@Builder`,
  no all-args constructor, no `@Data`.
- Relationships: `@ManyToOne(fetch = FetchType.LAZY)` + `@JoinColumn(name = "snake_case_id")`.
- JSON columns: `@JdbcTypeCode(SqlTypes.JSON)` on a `Map<String, Object>` field.
- Column name only specified when it differs from the camelCase→snake_case default.

**DTOs** (`api/dto/`): plain Java `record`s with a static `from(entity)` factory method — see any
`*Response.java` for the pattern. Never expose a JPA entity directly from a controller.

**Repositories** (`repository/`): plain `interface XxxRepository extends JpaRepository<Entity, UUID>`.
Derived-query methods for simple lookups; `@Query` with JPQL for aggregates;
`@Query(nativeQuery = true)` for Postgres-specific SQL (e.g. `extract(epoch from ...)`, window
functions, or `INSERT ... ON CONFLICT`). Paginated methods return `Page<T>` with a `Pageable` param.

**ETL / aggregation queries** (`warehouse/`, any future batch job): push the transform down into
native SQL (`INSERT ... ON CONFLICT DO UPDATE`), not fetch-then-sum-in-Java — see
`repository/FactIncidentDailyRepository.java`. When a query's watermark filter (what counts as
"new since last run") and its grouping/bucket column (what calendar day/hour a row belongs to) are
different fields, keep them different on purpose — the watermark must track *when a row changed*
(`created_at`/`updated_at`), not *what business date it reports* (`opened_at`/`resolved_at`).
Conflating the two is a real bug class that was caught during manual testing of this exact pattern.

**Controllers** (`api/`): thin — delegate to a `*Service`/`*QueryService`, never touch a repository
directly. Mutating endpoints return the fresh state after the mutation (not 204/void).

**Config**: `aiops.*` namespace (kebab-case keys) in `application.yml`, bound via
`@Value("${aiops.foo.bar}")` — no separate `@ConfigurationProperties` classes in this codebase, stay
consistent with that. Matching env vars are `AIOPS_FOO_BAR` (Spring relaxed binding handles the
case/separator conversion automatically — don't add manual parsing).

**Errors**: throw `ResourceNotFoundException` (404), `ValidationException` (400), or
`LlmUnavailableException` (503) from `common.exception` — `GlobalExceptionHandler` converts these
(and bean-validation failures) into the uniform `ApiError` shape. Don't catch-and-wrap into a
generic `RuntimeException` if one of these fits.

## Frontend (`frontend/src/`)

- Pages in `pages/`, one per route, each composing `Card`/`StatCard` from `components/StatCard.tsx`
  and `SeverityBadge`/`StatusBadge`/`ClassificationBadge` from `components/Badges.tsx`.
- Data fetching exclusively through `api/client.ts`'s `api.<domain>.<action>()` namespace — never
  call `axios`/`fetch` directly from a page. Add new backend calls there, typed against
  `api/types.ts`.
- TanStack Query for all server state (`useQuery`/`useMutation`); no separate global store.
- Tailwind utility classes inline; no CSS modules/styled-components.

## MCP server (`mcp-server/src/`)

- One file per backend controller under `tools/`, each exporting `register<Domain>Tools(server)`.
- Every tool handler is `try { return toolResult(await apiGet/apiPost(...)) } catch (e) { return
  toolError(e) }` — don't let a tool handler throw past its own try/catch; the SDK would still catch
  it, but `toolError` preserves the backend's actual `ApiError` message instead of a generic one.
- `annotations.readOnlyHint`/`destructiveHint`/`idempotentHint` are not optional decoration — set
  them accurately per tool (see existing tools for the pattern) since MCP clients use them to decide
  how much confirmation to show before a destructive call.
