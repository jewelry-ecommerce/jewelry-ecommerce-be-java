package com.pk.core.model.dto.response;

import com.pk.core.model.entity.SeasonEntity;

import java.util.Date;

public record SeasonResponseDto(Long id, String code, String name, String description, int startMonth, int endMonth,
                                int sortOrder, boolean isActive, Date createdDate, Date updatedDate) {
    public static SeasonResponseDto from(SeasonEntity s) {
        return new SeasonResponseDto(s.getId(), s.getCode(), s.getName(), s.getDescription(), s.getStartMonth(),
                s.getEndMonth(), s.getSortOrder(), s.isActive(), s.getCreatedDate(), s.getUpdatedDate());
    }
}
