package com.shortx.exception;

/**
 * Thrown when a password-protected short URL is accessed with a missing
 * or incorrect password. Maps to HTTP 401, distinct from 403/404 — the
 * resource exists and the caller knows it exists (they have the link),
 * they simply haven't authenticated against it yet.
 */
public class InvalidUrlPasswordException extends RuntimeException {
    public InvalidUrlPasswordException() {
        super("This link is password-protected. A valid password is required.");
    }
}
