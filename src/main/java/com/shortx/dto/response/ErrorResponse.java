package com.shortx.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Uniform error shape for every non-2xx response this API returns. Every
 * exception handler in GlobalExceptionHandler builds one of these, so API
 * consumers parse exactly one error format regardless of what went wrong.
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {

    private OffsetDateTime timestamp;
    private int status;
    private String error;
    private String message;
    private String path;

    // Present only for validation failures (400s from @Valid violations) —
    // field name -> human-readable message. Omitted entirely (via
    // @JsonInclude above) for every other error type rather than
    // serialized as an empty/null object.
    private Map<String, String> fieldErrors;
}
