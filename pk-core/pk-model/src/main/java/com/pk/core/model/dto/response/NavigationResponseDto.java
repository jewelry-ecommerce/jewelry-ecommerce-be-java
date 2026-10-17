package com.pk.core.model.dto.response;

import java.util.List;
import java.util.Map;

/** Nút menu cho admin (lớp DRAFT). id là số; các trường id khác (parentId, targetId, navigationId) FE tự chuẩn hoá thành chuỗi. */
public record NavigationResponseDto(Long id, String tenantCode, Long parentId, String label, String type,
                                    String targetId, String url, Map<String, Object> queryParams, String icon,
                                    String image, boolean openInNewTab, int sortOrder, boolean showOnMenuPopup,
                                    boolean showOnPlp, boolean isArchived, String layer, List<Badge> badges,
                                    List<NavigationResponseDto> children, String createdAt, String updatedAt) {

    public record Badge(Long id, Long navigationId, String designType, String displayText, String imageUrl,
                        Map<String, Object> styleConfig, String startTime, String endTime, String layer,
                        String createdAt, String updatedAt) {
    }
}
