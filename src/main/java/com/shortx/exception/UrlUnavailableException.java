package com.shortx.exception;

/**
 * Thrown when a short URL exists but is not currently usable for
 * redirection — either it was deliberately deactivated or its expiry date
 * has passed. Kept separate from ResourceNotFoundException: a 410 Gone (or
 * a dedicated "this link has expired" landing page) is a meaningfully
 * different user experience from a plain 404, and merging the two would
 * lose that distinction at the controller layer.
 */
public class UrlUnavailableException extends RuntimeException {
    public UrlUnavailableException(String message) {
        super(message);
    }
}
