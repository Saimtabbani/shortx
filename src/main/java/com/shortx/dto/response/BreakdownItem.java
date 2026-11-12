package com.shortx.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class BreakdownItem {
    private String key;
    private long count;
}
