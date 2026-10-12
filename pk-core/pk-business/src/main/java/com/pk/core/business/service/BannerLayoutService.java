package com.pk.core.business.service;

import com.pk.core.model.dto.request.AdminBannerLayoutItemRequestDto;
import com.pk.core.model.dto.request.AdminBannerLayoutRequestDto;
import com.pk.core.model.dto.response.BannerLayoutItemResponseDto;
import com.pk.core.model.dto.response.BannerLayoutResponseDto;

import java.util.List;

/** Banner builder: layout (slot/toạ độ/thứ tự) của một vị trí banner. Thêm 2026-10-12. */
public interface BannerLayoutService {

    /** Mọi layout của vị trí (kèm items), mới nhất trước. */
    List<BannerLayoutResponseDto> list(Long placementId);

    BannerLayoutResponseDto detail(Long placementId, Long layoutId);

    /** Tạo layout kèm items. status ACTIVE sẽ chuyển layout ACTIVE khác của vị trí về INACTIVE. */
    BannerLayoutResponseDto create(Long placementId, AdminBannerLayoutRequestDto req);

    /** Sửa layout; req.items != null = thay toàn bộ danh sách item. */
    BannerLayoutResponseDto update(Long placementId, Long layoutId, AdminBannerLayoutRequestDto req);

    BannerLayoutItemResponseDto patchItem(Long placementId, Long layoutId, Long itemId, AdminBannerLayoutItemRequestDto req);

    /** Gỡ banner khỏi slot (xoá dòng item, KHÔNG xoá banner). */
    void deleteItem(Long placementId, Long layoutId, Long itemId);
}
