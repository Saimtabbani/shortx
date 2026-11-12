package com.shortx.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * Backing implementation for {@link ValidUrl}. Registered as a Spring
 * bean (rather than instantiated by Bean Validation's default no-arg
 * constructor) specifically so it can have {@code shortx.base-url}
 * injected — Spring's LocalValidatorFactoryBean is wired to use the
 * Spring context to construct ConstraintValidators, so this works without
 * any extra configuration.
 */
@Component
public class UrlValidator implements ConstraintValidator<ValidUrl, String> {

    private final String baseUrlHost;

    public UrlValidator(@Value("${shortx.base-url}") String baseUrl) {
        this.baseUrlHost = extractHost(baseUrl);
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            // Presence is @NotBlank's job on the DTO field; this validator
            // only judges well-formedness of a non-blank value so the two
            // constraints don't produce confusingly overlapping messages.
            return true;
        }

        URI uri;
        try {
            uri = new URI(value);
        } catch (URISyntaxException e) {
            return false;
        }

        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            return false;
        }

        if (uri.getHost() == null || uri.getHost().isBlank()) {
            return false;
        }

        // Prevent shortening a link that points back at this very service
        // — left unchecked, that's a redirect loop (or at minimum, a
        // pointless chain) waiting to happen.
        return baseUrlHost == null || !uri.getHost().equalsIgnoreCase(baseUrlHost);
    }

    private String extractHost(String baseUrl) {
        try {
            return new URI(baseUrl).getHost();
        } catch (URISyntaxException e) {
            return null;
        }
    }
}
