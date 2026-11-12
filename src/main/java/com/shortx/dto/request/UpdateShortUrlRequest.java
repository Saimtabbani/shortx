package com.shortx.dto.request;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * Inbound payload for PATCH /api/v1/urls/{id}. Every field is optional and
 * nullable BY DESIGN — this is a partial update. A null field means
 * "leave unchanged," not "clear this value." Clearing a value (e.g.
 * removing password protection, removing an expiry date) is expressed
 * with the corresponding boolean clear-flag rather than overloading null,
 * since "not provided" and "explicitly cleared" are different intents
 * that a plain nullable field can't distinguish once deserialized from
 * JSON.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateShortUrlRequest {

    private Boolean active;

    @Future(message = "expiresAt must be in the future")
    private OffsetDateTime expiresAt;

    private boolean clearExpiry;

    @Size(min = 4, max = 100, message = "password must be between 4 and 100 characters")
    private String password;

    private boolean clearPassword;
}
