package com.shortx.exception;

/**
 * Thrown only if short-code generation exhausts its retry budget (see
 * ShortUrlServiceImpl.GENERATION_MAX_ATTEMPTS). Given the Snowflake ID
 * keyspace, a collision on a generated (non-custom-alias) code should be
 * astronomically unlikely — if this is ever actually thrown in production
 * it points to a real problem (e.g. two app instances misconfigured with
 * the same worker ID), not bad luck, so it's modeled as a distinct
 * exception rather than silently reusing a generic 500 error.
 */
public class ShortCodeGenerationException extends RuntimeException {
    public ShortCodeGenerationException(String message) {
        super(message);
    }
}
