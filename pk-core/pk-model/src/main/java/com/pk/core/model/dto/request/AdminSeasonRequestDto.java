package com.pk.core.model.dto.request;

import lombok.Getter;
import lombok.Setter;

/** Tạo/sửa mùa. POST: name + code + startMonth + endMonth bắt buộc (tháng 1..12, được phép vắt năm). PATCH: null = giữ nguyên. */
@Getter
@Setter
public class AdminSeasonRequestDto {
    private String name;
    private String code;
    private String description;
    private Integer startMonth;
    private Integer endMonth;
    private Integer sortOrder;
    private Boolean isActive;
}
