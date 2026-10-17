package com.pk.core.business.service;

import com.pk.core.model.dto.request.AdminProductListBannerConfigRequestDto;
import com.pk.core.model.dto.response.ProductListBannerConfigResponseDto;

import java.util.List;

/**
 * Banner chèn vào danh sách sản phẩm theo điều kiện: CATEGORY (targetSlug = slug danh mục) hoặc CMS_STOREFRONT (trang/khối CMS,
 * targetSlug sinh từ tên). Mỗi (conditionType, targetSlug) có tối đa một cấu hình; bản ghi gồm danh sách banner có lịch + hành động
 * LINK_URL (actionUrl) hoặc MIX_MATCH (danh sách SKU, phải tồn tại).
 */
public interface ProductListBannerService {

    List<ProductListBannerConfigResponseDto> list(String conditionType);

    ProductListBannerConfigResponseDto create(AdminProductListBannerConfigRequestDto req, Long actorId);

    ProductListBannerConfigResponseDto update(Long id, AdminProductListBannerConfigRequestDto req, Long actorId);

    void delete(Long id);

    /** Công khai: cấu hình đang bật của đích; chỉ gồm banner enabled và đang trong lịch. null nếu không có / không còn banner. */
    ProductListBannerConfigResponseDto publicConfig(String conditionType, String targetSlug);
}
