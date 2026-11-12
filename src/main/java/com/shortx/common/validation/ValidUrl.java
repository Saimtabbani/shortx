package com.shortx.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Validates that a destination URL is well-formed, restricted to http/https
 * (rejects javascript:, data:, file: schemes — an open-redirect and
 * XSS-adjacent risk for a service whose entire purpose is redirecting
 * users), and doesn't point back at ShortX's own base URL (rejects
 * shortening a shortx.dev link, which would create redirect loops/chains).
 *
 * <p>Implemented as a Bean Validation constraint rather than an ad hoc
 * "if" check inside the service method so the rule is declared once on
 * the DTO field, applies automatically wherever that DTO is validated
 * (including future endpoints reusing the same request type), and is
 * unit-testable in isolation from the service.
 */
@Documented
@Constraint(validatedBy = UrlValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidUrl {

    String message() default "must be a valid http(s) URL and not a ShortX link";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
