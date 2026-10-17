package com.pk.core.model.dto.response;

/** Một ô ghim cho admin. id/productId là số (FE chuẩn hoá thành chuỗi). */
public record SearchPinnedResponseDto(Long id, String keywordPattern, Long productId, int position, String startDate,
                                      String endDate, boolean isActive, String createdAt, String updatedAt) {
}
