package com.ai.aiops.api.dto;

import java.util.List;

public record LogFileUploadResponse(
        int entriesParsed,
        int entriesIngested,
        int entriesFailed,
        List<String> errors
) {
}
