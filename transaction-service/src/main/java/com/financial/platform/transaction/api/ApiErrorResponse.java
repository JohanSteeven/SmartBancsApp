package com.financial.platform.transaction.api;

import java.time.OffsetDateTime;
import java.util.List;

public record ApiErrorResponse(
    int status,
    String error,
    String message,
    List<String> details,
    OffsetDateTime timestamp
) {
    public static ApiErrorResponse of(int status, String error, String message, List<String> details) {
        return new ApiErrorResponse(status, error, message, details, OffsetDateTime.now());
    }

    public static ApiErrorResponse of(int status, String error, String message) {
        return of(status, error, message, List.of());
    }
}
