package com.pk.core.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** PATCH .../{id}/status của header hoặc logo. {@code confirmReplaceActive} chỉ có nghĩa với header (đồng ý tắt header trùng lịch). */
@Getter
@Setter
@NoArgsConstructor
public class AdminSiteStatusRequestDto {

    @NotBlank
    @Pattern(regexp = "^(DRAFT|ACTIVE|DEACTIVE|SCHEDULED)$", message = "status không hợp lệ")
    private String status;

    private Boolean confirmReplaceActive;
}
