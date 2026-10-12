package com.pk.core.model.dto.response;

import com.pk.core.model.entity.BannerPlacementEntity;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BannerPlacementResponseDto {

    private String id;
    private String code;
    private String name;
    private String displayType;
    private String platform;
    private String status;
    /** {columns, rows, gap, mobileOddFullWidthItem}; null nếu chưa cấu hình. */
    private Map<String, Object> settings;

    /** Cùng giá trị displayType, theo tên field FE (`type`). */
    public String getType() {
        return displayType;
    }

    public static BannerPlacementResponseDto from(BannerPlacementEntity e, Map<String, Object> settings) {
        return new BannerPlacementResponseDto(String.valueOf(e.getId()), e.getCode(), e.getName(), e.getDisplayType(),
                e.getPlatform(), e.getStatus(), settings);
    }
}
