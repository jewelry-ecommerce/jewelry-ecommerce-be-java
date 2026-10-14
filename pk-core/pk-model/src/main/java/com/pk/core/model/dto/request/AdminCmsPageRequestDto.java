package com.pk.core.model.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Date;
import java.util.List;

/** Body cho POST /admin/cms/pages (tạo, `blocks` seed nội dung ban đầu - tuỳ chọn) và PUT /admin/cms/pages/{pageId}
 * (cập nhật BẢN NHÁP + THAY TOÀN BỘ blocks nếu `blocks` != null). Theo spec FE: {name, slug, status, blocks}; `title`
 * vẫn được nhận như bí danh của `name`. `slug` bỏ trống thì tự sinh từ `name`.
 *
 * <p>`status`: bỏ trống hoặc DRAFT = chỉ lưu nháp, KHÔNG đụng bản đang public (muốn gỡ dùng POST .../unpublish);
 * PUBLISHED = lưu rồi xuất bản ngay (giữ cho client cũ; FE mới nên gọi POST .../publish riêng).
 * `targetDevice` ALL|DESKTOP|MOBILE; `scheduleStart`/`scheduleEnd` (ISO-8601): chỉ đổi khi request có gửi khoá đó
 * (gửi null = xoá), không gửi = giữ nguyên. Cả hai thuộc bản nháp, có hiệu lực khi xuất bản. */
@Getter
@Setter
@NoArgsConstructor
public class AdminCmsPageRequestDto {

    @NotBlank(message = "Tên trang không được để trống")
    @JsonAlias("title")
    private String name;

    private String slug;

    private String status;
    /** Mã ngôn ngữ (mặc định vi). */
    private String locale;
    /** Bật/tắt trang (bỏ trống: giữ nguyên / mặc định bật). Áp dụng ngay, không cần xuất bản lại. */
    @JsonAlias("isActive")
    private Boolean active;
    /** SEO: {title, description, keywords, canonicalUrl, imageUrl}. */
    private java.util.Map<String, Object> seo;

    @Valid
    private List<AdminCmsBlockRequestDto> blocks;

    private String targetDevice;

    private Date scheduleStart;
    private Date scheduleEnd;
    @JsonIgnore
    private boolean scheduleStartProvided;
    @JsonIgnore
    private boolean scheduleEndProvided;

    /** Giữ chữ ký cũ (7 tham số) cho các caller hiện có. */
    public AdminCmsPageRequestDto(String name, String slug, String status, String locale, Boolean active,
                                  java.util.Map<String, Object> seo, List<AdminCmsBlockRequestDto> blocks) {
        this.name = name;
        this.slug = slug;
        this.status = status;
        this.locale = locale;
        this.active = active;
        this.seo = seo;
        this.blocks = blocks;
    }

    public void setScheduleStart(Date scheduleStart) {
        this.scheduleStart = scheduleStart;
        this.scheduleStartProvided = true;
    }

    public void setScheduleEnd(Date scheduleEnd) {
        this.scheduleEnd = scheduleEnd;
        this.scheduleEndProvided = true;
    }
}
