package com.pk.core.model.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/** POST /admin/pre-orders (spec FE: {enabled (bắt buộc), message}). */
@Getter
@Setter
@NoArgsConstructor
public class PreOrderConfigRequestDto {

    @NotNull
    private Boolean enabled;

    @Size(max = 500)
    private String message;

    /** ALL (mặc định: mọi SKU hết hàng đặt trước được) | SKU | PRODUCT | CATEGORY | COLLECTION. */
    private String scopeType;

    /** Bắt buộc (không rỗng, tối đa 200) khi scopeType khác ALL; phải bỏ trống khi ALL. */
    private List<Long> scopeIds;

    public PreOrderConfigRequestDto(Boolean enabled, String message) {
        this.enabled = enabled;
        this.message = message;
    }
}
