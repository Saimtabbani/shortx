package com.shortx.exception;

/**
 * Thrown when an authenticated user attempts to modify or delete a short
 * URL they don't own and don't have admin privileges over. Maps to HTTP
 * 403. Deliberately distinct from Spring Security's own AccessDeniedException
 * (which guards endpoint-level access via @PreAuthorize) — this one guards
 * row-level ownership, a check that can only happen once the specific
 * resource has been loaded.
 */
public class UnauthorizedOperationException extends RuntimeException {
    public UnauthorizedOperationException(String message) {
        super(message);
    }
}
