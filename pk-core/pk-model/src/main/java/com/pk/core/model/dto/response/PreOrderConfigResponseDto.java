package com.pk.core.model.dto.response;

import com.pk.core.model.entity.PreOrderConfigEntity;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/** Một cấu hình đặt trước (id là chuỗi như các DTO spec FE khác). */
@Getter
@Setter
@NoArgsConstructor
public class PreOrderConfigResponseDto {

    private String id;
    private boolean enabled;
    private String message;
    private Date createdAt;
    /** ALL | SKU | PRODUCT | CATEGORY | COLLECTION. */
    private String scopeType = "ALL";
    /** Id trong phạm vi; rỗng khi scopeType = ALL. */
    private List<Long> scopeIds = new ArrayList<>();

    public PreOrderConfigResponseDto(String id, boolean enabled, String message, Date createdAt) {
        this.id = id;
        this.enabled = enabled;
        this.message = message;
        this.createdAt = createdAt;
    }

    public static PreOrderConfigResponseDto from(PreOrderConfigEntity e) {
        PreOrderConfigResponseDto d = new PreOrderConfigResponseDto(String.valueOf(e.getId()), e.isEnabled(),
                e.getMessage(), e.getCreatedDate());
        d.setScopeType(e.getScopeType() == null ? "ALL" : e.getScopeType());
        d.setScopeIds(parseIds(e.getScopeIds()));
        return d;
    }

    /** "1,2,3" -> [1,2,3]; null/rỗng -> []. */
    public static List<Long> parseIds(String csv) {
        List<Long> out = new ArrayList<>();
        if (csv == null || csv.isBlank()) {
            return out;
        }
        for (String part : csv.split(",")) {
            try {
                out.add(Long.parseLong(part.trim()));
            } catch (NumberFormatException ex) {
                // bỏ qua mảnh hỏng, không làm hỏng cả cấu hình
            }
        }
        return out;
    }
}
