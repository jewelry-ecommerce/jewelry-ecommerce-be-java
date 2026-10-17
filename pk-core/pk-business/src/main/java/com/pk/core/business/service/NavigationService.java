package com.pk.core.business.service;

import com.pk.core.model.dto.request.AdminNavigationReorderRequestDto;
import com.pk.core.model.dto.request.AdminNavigationRequestDto;
import com.pk.core.model.dto.response.NavigationResponseDto;
import com.pk.core.model.dto.response.StorefrontNavigationDto;

import java.util.List;
import java.util.Map;

/**
 * Menu điều hướng website, 2 lớp: DRAFT (admin chỉnh) và PUBLISHED (storefront đọc). Xuất bản = sao chép toàn bộ nháp
 * (trừ nút đã lưu trữ) sang PUBLISHED. Tối đa 4 cấp; cấp 4 không hiện trong menu popup. Mỗi nút có thể có các nhãn (badge)
 * theo lịch; nhãn trùng lịch trong cùng nút: 409 NAVIGATION_BADGE_SCHEDULE_CONFLICT (kèm dependencies) trừ khi confirmReplaceSchedule=true.
 */
public interface NavigationService {

    /** Cây nháp (không gồm nút đã lưu trữ). */
    List<NavigationResponseDto> tree();

    NavigationResponseDto detail(Long id);

    NavigationResponseDto create(AdminNavigationRequestDto req, Long actorId);

    NavigationResponseDto update(Long id, AdminNavigationRequestDto req, Long actorId);

    /** Lưu trữ nút và toàn bộ nút con (không xoá dữ liệu); nút biến mất khỏi cây nháp và từ lần xuất bản sau. */
    void archive(Long id);

    void reorder(AdminNavigationReorderRequestDto req);

    boolean hasPendingPublish();

    /** Xuất bản; trả hasPendingPublish sau khi xuất bản (luôn false). */
    boolean publish(Long actorId);

    /** Xoá cache menu (hiện chưa có cache phía BE: không-op, trả tenantCode). */
    Map<String, Object> clearCache();

    StorefrontNavigationDto storefront();

    StorefrontNavigationDto plpRail(String categoryId, String rootTargetId, String rootUrl);
}
