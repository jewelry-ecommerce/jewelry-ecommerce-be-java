package com.pk.core.model.dto.request;

import lombok.Getter;
import lombok.Setter;

/** Tạo/sửa thương hiệu. POST: name + code bắt buộc. PATCH: trường null = giữ nguyên. status: ACTIVE | INACTIVE (không phân biệt hoa/thường). */
@Getter
@Setter
public class AdminBrandRequestDto {
    private String name;
    private String code;
    private String image;
    private String websiteUrl;
    private String status;
}
