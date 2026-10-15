package com.pk.core.model.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Tạo (POST) và sửa một phần (PATCH) header. Khi tạo bắt buộc bannerName, bannerDisplayStyle, bannerStartTime (service kiểm tra);
 * PATCH: field null = giữ nguyên. Thời gian ISO-8601 (vd 2026-10-20T00:00:00Z). {@code bannerEndTime} rỗng/null = không hết hạn
 * (PATCH muốn xoá hạn thì gửi chuỗi rỗng). {@code confirmReplaceActive}: đồng ý tắt các header trùng lịch (chỉ dùng khi tạo).
 */
@Getter
@Setter
@NoArgsConstructor
public class AdminSiteHeaderRequestDto {

    @Size(max = 150)
    private String bannerName;

    @Pattern(regexp = "^(TEXT|MEDIA)$", message = "bannerDisplayStyle chỉ nhận TEXT hoặc MEDIA")
    private String bannerDisplayStyle;

    @Size(max = 1000)
    private String bannerContent;

    @Size(max = 30)
    private String bannerBgColor;

    @Size(max = 30)
    private String bannerTextColor;

    @Size(max = 500)
    private String bannerTargetUrl;

    @Size(max = 30)
    private String bannerAnimationStyle;

    @Min(1)
    @Max(3600)
    private Integer bannerLoopIntervalSec;

    private String bannerStartTime;

    private String bannerEndTime;

    @Pattern(regexp = "^(DRAFT|ACTIVE|DEACTIVE|SCHEDULED)$", message = "status không hợp lệ")
    private String status;

    private Boolean confirmReplaceActive;
}
