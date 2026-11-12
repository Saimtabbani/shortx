package com.shortx.dto.request;

import com.shortx.common.validation.ValidUrl;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * Inbound payload for POST /api/v1/urls. All fields besides originalUrl
 * are optional — a bare "shorten this URL" request with no alias,
 * password, or expiry is the common case and should require nothing else.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateShortUrlRequest {

    @NotBlank(message = "originalUrl is required")
    @Size(max = 2048, message = "originalUrl must be at most 2048 characters")
    @ValidUrl
    private String originalUrl;

    // Optional. When absent, the service generates a Base62 code from a
    // Snowflake ID. When present, it must be unique (AliasAlreadyExistsException
    // otherwise) and is never silently altered — a user who chose an alias
    // is relying on that exact string.
    @Size(min = 3, max = 20, message = "customAlias must be between 3 and 20 characters")
    @Pattern(regexp = "^[a-zA-Z0-9_-]+$", message = "customAlias may only contain letters, digits, hyphens, and underscores")
    private String customAlias;

    // Optional plaintext password for link protection. Hashed by the
    // service before storage; never persisted or logged in plaintext.
    @Size(min = 4, max = 100, message = "password must be between 4 and 100 characters")
    private String password;

    @Future(message = "expiresAt must be in the future")
    private OffsetDateTime expiresAt;
}
