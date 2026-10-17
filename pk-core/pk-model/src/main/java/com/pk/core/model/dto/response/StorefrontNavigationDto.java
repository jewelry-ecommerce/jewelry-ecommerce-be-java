package com.pk.core.model.dto.response;

import java.util.List;
import java.util.Map;

/** Menu công khai (lớp PUBLISHED) theo hợp đồng client: {@code {items: [...]}}. id = id nút nháp (ổn định qua các lần xuất bản). */
public record StorefrontNavigationDto(List<Item> items) {

    /** {@code linkTarget.kind}: none | category | page | url | filter. */
    public record LinkTarget(String kind, String targetId, String url, Map<String, Object> queryParams) {
    }

    public record Badge(String id, String designType, String displayText, String imageUrl,
                        Map<String, Object> styleConfig, String startTime, String endTime) {
    }

    public record Item(String id, String label, String type, String targetId, String url,
                       Map<String, Object> queryParams, String icon, String image, boolean openInNewTab, int sortOrder,
                       String parentId, boolean showOnMenuPopup, boolean showOnPlp, LinkTarget linkTarget, Badge badge,
                       boolean hasChildren, boolean expandOnly, String viewAllLabel, boolean parentEffectivelyHidden,
                       List<Item> children) {
    }
}
