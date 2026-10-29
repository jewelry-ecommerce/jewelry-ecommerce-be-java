package com.pk.core.model.dto.response;

import com.pk.core.model.entity.SearchHistoryEntity;

/** Một mục lịch sử tìm kiếm của tài khoản; searchedAt dạng ISO-8601 UTC. */
public record SearchHistoryItemResponseDto(String keyword, String searchedAt) {

    public static SearchHistoryItemResponseDto from(SearchHistoryEntity e) {
        return new SearchHistoryItemResponseDto(e.getKeyword(),
                e.getSearchedDate() == null ? null : java.time.Instant.ofEpochMilli(e.getSearchedDate().getTime()).toString());
    }
}
