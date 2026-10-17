package com.pk.core.model.dto.request;

import jakarta.validation.Valid;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Map;

/**
 * Tạo (POST) / sửa một phần (PATCH) nút menu. {@code parentId} chỉ dùng khi tạo (đổi cha không hỗ trợ). Số nguyên hay chuỗi số
 * đều nhận cho các id ({@code targetId} luôn là chuỗi). {@code clearBadge=true} xoá nhãn đang gắn của nút (bản nháp).
 */
@Getter
@Setter
@NoArgsConstructor
public class AdminNavigationRequestDto {

    private String label;
    /** NONE | CATEGORY | PAGE | LINK. */
    private String type;
    private Long parentId;
    private String targetId;
    private String url;
    private Map<String, Object> queryParams;
    private String icon;
    private String image;
    private Boolean openInNewTab;
    private Boolean showOnMenuPopup;
    private Boolean showOnPlp;
    private Integer sortOrder;
    @Valid
    private Badge badge;
    private Boolean clearBadge;

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Badge {
        /** Có id = sửa nhãn đó; không có id = thêm nhãn mới. */
        private Long id;
        /** TEXT | IMAGE. */
        private String designType;
        private String displayText;
        private String imageUrl;
        private Map<String, Object> styleConfig;
        /** ISO-8601. */
        private String startTime;
        private String endTime;
        /** true = đồng ý thay thế (xoá) các nhãn khác trùng lịch của cùng nút. */
        private Boolean confirmReplaceSchedule;
    }
}
