package com.pk.core.model.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Date;
import java.util.List;

/**
 * Tạo layout (POST) hoặc sửa layout (PATCH). Khi PATCH: field null = giữ nguyên; {@code items} khác null = THAY TOÀN BỘ
 * danh sách banner-trong-slot của layout (banner bị gỡ khỏi layout vẫn còn nguyên trong /admin/banner/banners).
 */
@Getter
@Setter
@NoArgsConstructor
public class AdminBannerLayoutRequestDto {

    @Size(max = 150)
    private String name;
    /** DRAFT | SCHEDULED | ACTIVE | INACTIVE | ARCHIVED (mặc định DRAFT). */
    private String status;
    private Date activeFrom;
    private Date activeTo;
    @Size(max = 500)
    private String note;
    private List<AdminBannerLayoutItemRequestDto> items;
}
