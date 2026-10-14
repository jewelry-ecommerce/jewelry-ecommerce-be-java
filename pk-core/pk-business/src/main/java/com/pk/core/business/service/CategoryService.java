package com.pk.core.business.service;

import com.pk.core.model.dto.request.AdminCategoryRequestDto;
import com.pk.core.model.dto.response.CategoryResponseDto;

import java.util.List;

public interface CategoryService {

    /** Danh mục đang active, dùng cho menu/filter storefront (GET /storefront/product/categories). */
    List<CategoryResponseDto> findAllActive();

    /** Toàn bộ danh mục (kể cả không active) - Admin Catalog mục J: GET /admin/catalog/categories. */
    List<CategoryResponseDto> findAllForAdmin();

    /** Tạo danh mục - Admin Catalog mục J: POST /admin/catalog/categories. 409 DUPLICATE_SLUG nếu
     * trùng slug (tự sinh từ name nếu request không truyền). */
    CategoryResponseDto create(AdminCategoryRequestDto req);

    /** Sửa danh mục - Admin Catalog mục J: PUT /admin/catalog/categories/{categoryId}. */
    CategoryResponseDto update(Long categoryId, AdminCategoryRequestDto req);

    /** Xoá danh mục - DELETE /admin/catalog/categories/{categoryId}. 409 CATEGORY_IN_USE nếu còn danh mục con hoặc sản phẩm. */
    void delete(Long categoryId);
}
