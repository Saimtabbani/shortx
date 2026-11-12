package com.shortx.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.OffsetDateTime;

/**
 * Bound from query parameters on GET /api/v1/urls (e.g.
 * ?active=true&createdAfter=2026-01-01T00:00:00Z). Every field is an
 * optional filter — an all-null instance means "no filtering, return
 * everything the user owns." Consumed by
 * service.specification.ShortUrlSpecifications to build a dynamic JPA
 * Specification, so adding a new filter here later means adding one
 * clause to that builder, not writing a new derived-query method.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShortUrlFilterRequest {

    private Boolean active;

    private Boolean customAliasOnly;

    // @DateTimeFormat is required here specifically because this DTO is
    // bound from query parameters (@ModelAttribute), not a JSON request
    // body — Jackson's ISO-8601 handling only applies to @RequestBody
    // deserialization, so query-param binding needs Spring's own
    // conversion-service annotation to parse the same format correctly.
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private OffsetDateTime createdAfter;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private OffsetDateTime createdBefore;

    // Case-insensitive substring match against originalUrl or shortCode.
    private String search;
}
