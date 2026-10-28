package com.pk.core.model.dto.response;

/** Một gợi ý tìm kiếm: type = "keyword" (từ khoá phổ biến) hoặc "category" (kèm slug để FE chuyển trang danh mục). */
public record SearchSuggestionResponseDto(String text, String type, String slug) {
}
