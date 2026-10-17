package com.pk.core.model.dto.request;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Tạo/sửa sản phẩm ghim ở ô tìm kiếm. Sửa một phần (PATCH): trường không gửi thì giữ nguyên; để xoá {@code endDate}/{@code keywordPattern}
 * gửi {@code clearEndDate}/{@code clearKeywordPattern}=true hoặc gửi chuỗi rỗng. {@code productId} chấp nhận số hoặc chuỗi số.
 */
@Getter
@Setter
@NoArgsConstructor
public class AdminSearchPinnedRequestDto {

    private String keywordPattern;
    private Long productId;
    private Integer position;
    /** ISO-8601; để trống khi tạo = hiệu lực ngay. */
    private String startDate;
    private String endDate;
    private Boolean isActive;
    private Boolean clearKeywordPattern;
    private Boolean clearEndDate;
}
