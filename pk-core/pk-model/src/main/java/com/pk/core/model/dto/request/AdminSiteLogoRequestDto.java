package com.pk.core.model.dto.request;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Tạo (POST: bắt buộc name, logoUrl) và sửa một phần (PATCH: field null = giữ nguyên) logo. */
@Getter
@Setter
@NoArgsConstructor
public class AdminSiteLogoRequestDto {

    @Size(max = 150)
    private String name;

    @Pattern(regexp = "^(HEADER|FOOTER|AUTH|FAVICON)$", message = "type chỉ nhận HEADER, FOOTER, AUTH, FAVICON")
    private String type;

    @Pattern(regexp = "^(DRAFT|ACTIVE|DEACTIVE|SCHEDULED)$", message = "status không hợp lệ")
    private String status;

    @Size(max = 500)
    private String logoUrl;

    @Size(max = 30)
    private String logoTheme;

    @Size(max = 500)
    private String logoTargetUrl;
}
