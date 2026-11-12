package com.shortx.exception;

/**
 * Thrown for any login failure — wrong password, unknown email, or a
 * disabled account. Deliberately a SINGLE exception with a SINGLE generic
 * message ("Invalid email or password") for all three cases: returning a
 * different message for "no such account" versus "wrong password" would
 * let an attacker enumerate registered email addresses one guess at a
 * time.
 */
public class InvalidCredentialsException extends RuntimeException {
    public InvalidCredentialsException() {
        super("Invalid email or password.");
    }
}
