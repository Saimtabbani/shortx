package com.shortx.exception;

import com.shortx.dto.response.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Single point of translation from "something went wrong" to "the correct
 * HTTP status and a useful, uniformly-shaped error body." Without this,
 * every controller method would need its own try/catch, and callers of
 * this API would see an inconsistent mix of Spring's default error pages,
 * raw stack traces, and ad hoc JSON shapes depending on which controller
 * happened to handle the request.
 *
 * <p>Every handler here does two things: log at the appropriate level
 * (client errors at WARN/INFO — they're expected traffic, not incidents;
 * unexpected exceptions at ERROR with the full stack trace) and return a
 * client-safe message. The catch-all handler in particular never echoes
 * the raw exception message to the client — internal exception text can
 * leak implementation details (table names, internal class names, stack
 * frames) that are a minor information-disclosure risk with no benefit to
 * a legitimate API consumer.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        log.info("Resource not found: {}", ex.getMessage());
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    @ExceptionHandler(AliasAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleAliasConflict(AliasAlreadyExistsException ex, HttpServletRequest request) {
        log.info("Alias conflict: {}", ex.getMessage());
        return build(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleEmailConflict(EmailAlreadyExistsException ex, HttpServletRequest request) {
        log.info("Registration conflict: {}", ex.getMessage());
        return build(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleInvalidCredentials(InvalidCredentialsException ex, HttpServletRequest request) {
        // INFO, not WARN: failed logins are routine, expected traffic
        // (typos, expired browser-saved passwords), not a signal of
        // anything wrong with the application itself.
        log.info("Failed login attempt on {}", request.getRequestURI());
        return build(HttpStatus.UNAUTHORIZED, ex.getMessage(), request);
    }

    @ExceptionHandler(InvalidRefreshTokenException.class)
    public ResponseEntity<ErrorResponse> handleInvalidRefreshToken(InvalidRefreshTokenException ex, HttpServletRequest request) {
        log.info("Invalid refresh token presented on {}", request.getRequestURI());
        return build(HttpStatus.UNAUTHORIZED, ex.getMessage(), request);
    }

    @ExceptionHandler(UrlUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleUnavailable(UrlUnavailableException ex, HttpServletRequest request) {
        log.info("URL unavailable: {}", ex.getMessage());
        return build(HttpStatus.GONE, ex.getMessage(), request);
    }

    @ExceptionHandler(InvalidUrlPasswordException.class)
    public ResponseEntity<ErrorResponse> handleInvalidPassword(InvalidUrlPasswordException ex, HttpServletRequest request) {
        return build(HttpStatus.UNAUTHORIZED, ex.getMessage(), request);
    }

    @ExceptionHandler(UnauthorizedOperationException.class)
    public ResponseEntity<ErrorResponse> handleUnauthorizedOperation(UnauthorizedOperationException ex, HttpServletRequest request) {
        log.warn("Unauthorized operation attempt: {}", ex.getMessage());
        return build(HttpStatus.FORBIDDEN, ex.getMessage(), request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        // Spring Security's endpoint-level denial (e.g. @PreAuthorize),
        // distinct from UnauthorizedOperationException's row-level
        // ownership check above, but both correctly map to 403.
        log.warn("Access denied: {}", ex.getMessage());
        return build(HttpStatus.FORBIDDEN, "You do not have permission to perform this action.", request);
    }

    @ExceptionHandler(ShortCodeGenerationException.class)
    public ResponseEntity<ErrorResponse> handleGenerationFailure(ShortCodeGenerationException ex, HttpServletRequest request) {
        // 503, not 500: this signals a transient/infra-level condition
        // (see the exception's own Javadoc) that a client-side retry is a
        // reasonable response to, which is exactly what 503 communicates.
        log.error("Short code generation exhausted retries: {}", ex.getMessage());
        return build(HttpStatus.SERVICE_UNAVAILABLE, "Unable to generate a short URL right now. Please try again.", request);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLock(OptimisticLockingFailureException ex, HttpServletRequest request) {
        log.info("Optimistic lock conflict: {}", ex.getMessage());
        return build(HttpStatus.CONFLICT,
            "This resource was modified by another request. Please refresh and try again.", request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(fieldError.getField(), fieldError.getDefaultMessage());
        }

        ErrorResponse body = ErrorResponse.builder()
                .timestamp(OffsetDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error(HttpStatus.BAD_REQUEST.getReasonPhrase())
                .message("Validation failed for one or more fields.")
                .path(request.getRequestURI())
                .fieldErrors(fieldErrors)
                .build();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest request) {
        // Covers @Validated violations on @PathVariable/@RequestParam,
        // as opposed to @Valid on a @RequestBody DTO (handled above).
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "Malformed request body.", request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        return build(HttpStatus.METHOD_NOT_ALLOWED, ex.getMessage(), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        // Deliberately generic client-facing message - see class Javadoc
        // on why ex.getMessage() is never echoed here. The real detail
        // goes to the log, correlated by the traceId already present in
        // the log pattern (see application.yml logging.pattern.console).
        log.error("Unhandled exception processing request {} {}", request.getMethod(), request.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred. Please try again later.", request);
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String message, HttpServletRequest request) {
        ErrorResponse body = ErrorResponse.builder()
                .timestamp(OffsetDateTime.now())
                .status(status.value())
                .error(status.getReasonPhrase())
                .message(message)
                .path(request.getRequestURI())
                .build();
        return ResponseEntity.status(status).headers(HttpHeaders.EMPTY).body(body);
    }
}
