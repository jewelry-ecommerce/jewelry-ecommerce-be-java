package com.pk.core.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Đếm luồng theo trạng thái (không phụ thuộc bộ lọc status): {all, active, inactive, draft}. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BadgeFlowCountsDto {

    private long all;
    private long active;
    private long inactive;
    private long draft;
    /** Đếm theo displayStatus: DRAFT | PAUSED | SCHEDULED | RUNNING | ENDED (cùng tập với `search`, không phụ thuộc status/displayStatus). */
    private java.util.Map<String, Long> displayStatus = new java.util.LinkedHashMap<>();

    public BadgeFlowCountsDto(long all, long active, long inactive, long draft) {
        this.all = all;
        this.active = active;
        this.inactive = inactive;
        this.draft = draft;
    }
}
