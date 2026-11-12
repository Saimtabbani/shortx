package com.shortx.exception;

/**
 * Thrown when a requested resource (short URL, user, etc.) doesn't exist.
 * The global exception handler (added in a later milestone) maps this to
 * HTTP 404. Kept as a single generic type rather than one subclass per
 * entity — the handler only ever needs "this maps to 404", and the
 * message string carries the specifics.
 */
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
