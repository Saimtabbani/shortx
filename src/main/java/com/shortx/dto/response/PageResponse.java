package com.shortx.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import org.springframework.data.domain.Page;

import java.util.List;

/**
 * A stable, minimal pagination envelope for API responses. Deliberately
 * NOT serializing Spring Data's {@code Page} directly — its default JSON
 * shape includes internal fields (pageable, sort details as nested
 * objects, etc.) that couple the public API contract to a Spring Data
 * implementation detail. This wrapper is the one shape every paginated
 * endpoint returns, so API consumers write pagination-handling code once.
 */
@Getter
@Builder
@AllArgsConstructor
public class PageResponse<T> {

    private List<T> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
    private boolean last;

    public static <T> PageResponse<T> from(Page<T> page) {
        return PageResponse.<T>builder()
                .content(page.getContent())
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .last(page.isLast())
                .build();
    }
}
