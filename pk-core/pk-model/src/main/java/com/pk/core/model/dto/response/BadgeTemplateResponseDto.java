package com.pk.core.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Map;

/** Mẫu nhãn theo spec FE (id là chuỗi). Dùng cho admin và cho phần `badges` của GET /storefront/badge. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BadgeTemplateResponseDto {

    private String id;
    private String name;
    private String code;
    private String description;
    private String type;
    private String badgeType;
    private String status;
    private String displayText;
    private String defaultPosition;
    private Map<String, Object> styleConfig;
    private String icon;
    private String image;
    private int defaultPriorityWeight;
    /** Ảnh/icon bản mobile và metadata asset - trước đây có lưu nhưng không trả về. */
    private String iconMobile;
    private String imageMobile;
    private Map<String, Object> assetMeta;
}
