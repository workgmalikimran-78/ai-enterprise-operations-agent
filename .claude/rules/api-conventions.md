# REST API conventions

All endpoints under `/api/v1/...`. `SecurityConfig` is `permitAll()` everywhere — no auth yet
(intentional, deferred; see `CLAUDE.md`). Don't bolt on partial auth to one endpoint without doing
the real OAuth2/RBAC work platform-wide.

## Pagination — two different shapes exist, confirmed against live responses (not assumed)

Most list endpoints return Spring Data's `Page<T>`, which this app's pinned Boot/
spring-data-commons version serializes as the **classic full shape**:
```json
{
  "content": [...],
  "pageable": { "pageNumber": 0, "pageSize": 20, "offset": 0, "paged": true, "unpaged": false, "sort": {...} },
  "totalElements": 137, "totalPages": 7,
  "size": 20, "number": 0, "numberOfElements": 20,
  "first": true, "last": false, "empty": false,
  "sort": {...}
}
```
`LogController.search` alone returns a different, hand-written `PagedResponse<T>` record:
```json
{ "content": [...], "page": 0, "size": 20, "totalElements": 137 }
```
Note `page` is the page **number** (an int) in this second shape, not a nested object — don't
conflate the two when writing a new client (frontend `api/types.ts`, or an MCP tool) against a
paginated endpoint. Always check a live response before assuming which shape applies; an earlier
pass of this analysis assumed the newer compact `{content, page: {...}}` shape from documentation
that turned out not to match this app's actual resolved dependency versions.

## Error shape

Every 4xx/5xx is `ApiError` (`backend/.../api/dto/ApiError.java`):
```json
{
  "timestamp": "...", "status": 404, "error": "Not Found",
  "message": "Incident not found: ...", "path": "/api/v1/incidents/...",
  "correlationId": "...", "fieldErrors": []
}
```
`fieldErrors` (each `"field: message"`) is only populated for bean-validation (`@Valid`)/constraint
violations. Exception → status mapping: `ResourceNotFoundException`→404,
`ValidationException`→400, `LlmUnavailableException`→503 (LLM key missing/call failed),
`MaxUploadSizeExceededException`→413, anything else→500 with a generic message (don't leak internal
exception details for unhandled cases).

## Adding a new endpoint

1. Controller method delegates to a service — see any existing controller for the pattern.
2. DTO response is a record with a static `from(entity)` (`api/dto/`).
3. If paginated, decide explicitly whether it returns Spring's `Page<T>` (classic shape above) or a
   hand-written response — don't introduce a third shape without a specific reason.
4. If it should be reachable via the MCP server too, add a corresponding tool in
   `mcp-server/src/tools/<domain>.ts` in the same change (see `.claude/rules/code-style.md`'s MCP
   section) — don't let the two API surfaces drift.
5. Update `frontend/src/api/client.ts` + `api/types.ts` if the dashboard needs it.
