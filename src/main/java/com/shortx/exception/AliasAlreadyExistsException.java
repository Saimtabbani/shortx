package com.shortx.exception;

/**
 * Thrown when a user-requested custom alias is already taken. Distinct
 * from a generic conflict exception because the handler needs to surface
 * a specific, actionable message ("this alias is taken, try another") —
 * unlike a generated code collision, which the service retries internally
 * and the caller should never see.
 */
public class AliasAlreadyExistsException extends RuntimeException {
    public AliasAlreadyExistsException(String alias) {
        super("The alias '" + alias + "' is already in use. Please choose a different one.");
    }
}
