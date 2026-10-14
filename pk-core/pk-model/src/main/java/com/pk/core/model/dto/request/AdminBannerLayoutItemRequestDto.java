package com.pk.core.model.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Map;

/** Một banner trong một slot của layout. Khi PATCH item mọi field đều tuỳ chọn. */
@Getter
@Setter
@NoArgsConstructor
public class AdminBannerLayoutItemRequestDto {

    private String slotKey;
    private Long bannerId;
    @JsonProperty("isActive")
    private Boolean isActive;
    /** Nhỏ hơn = hiển thị trước trong cùng slot (mặc định 0). */
    private Integer priority;
    /** {x,y,width,height,order,slotType SINGLE_IMAGE|MULTIPLE_IMAGE,layoutType GRID|SLIDER|DOUBLE_BANNER,childBannerIds[]};
     * key khác được giữ nguyên. Tên layout gửi nhầm vào slotType được tách sang layoutType. `slotKey` bỏ trống khi tạo = "main". */
    private Map<String, Object> displayMetadata;
}
