package it.zuperman.support_trainer.common.response;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Public, stable representation of an HTTP error. Details from exceptions,
 * request payloads and authentication headers are intentionally excluded.
 */
public record ErrorResponse(
        Instant timestamp,
        int status,
        String code,
        String message,
        String path,
        @JsonInclude(JsonInclude.Include.NON_EMPTY) List<FieldErrorResponse> fieldErrors
) {

    public ErrorResponse {
        fieldErrors = fieldErrors == null || fieldErrors.isEmpty() ? null : List.copyOf(fieldErrors);
    }
}
