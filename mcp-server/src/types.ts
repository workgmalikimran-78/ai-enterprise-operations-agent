/**
 * Spring Data's Page<T> as this app's pinned Boot/spring-data-commons version actually serializes
 * it over the wire - the classic full shape (content + pageable + totalElements/totalPages + last/
 * first/empty/numberOfElements + top-level size/number/sort), confirmed by hitting the live
 * endpoints directly. Every paginated tool here forwards the raw JSON through unchanged (no
 * transformation), so this type is documentation only - nothing in api-client.ts enforces it.
 */
export interface SpringPage<T> {
    content: T[];
    pageable: {
        pageNumber: number;
        pageSize: number;
        offset: number;
        paged: boolean;
        unpaged: boolean;
        sort: { empty: boolean; sorted: boolean; unsorted: boolean };
    };
    totalElements: number;
    totalPages: number;
    size: number;
    number: number;
    numberOfElements: number;
    first: boolean;
    last: boolean;
    empty: boolean;
    sort: { empty: boolean; sorted: boolean; unsorted: boolean };
}

/**
 * LogController.search alone returns this hand-written shape instead of a Spring Page -
 * note "page" is the page NUMBER here, not a nested object. Do not conflate with SpringPage.
 */
export interface LogSearchPagedResponse<T> {
    content: T[];
    page: number;
    size: number;
    totalElements: number;
}

/** The uniform error body GlobalExceptionHandler produces for every 4xx/5xx response. */
export interface ApiErrorBody {
    timestamp: string;
    status: number;
    error: string;
    message: string;
    path: string;
    correlationId: string | null;
    fieldErrors: string[];
}
