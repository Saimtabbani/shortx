package com.shortx.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@Builder
@AllArgsConstructor
public class DailyClickCountItem {
    private LocalDate day;
    private long count;
}
