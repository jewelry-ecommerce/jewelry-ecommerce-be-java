package com.pk.core.business.repository;

import com.pk.core.model.entity.CategoryEntity;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CategoryRepo extends PkRepo<CategoryEntity, Long> {

    /** Trả null nếu không có. */
    CategoryEntity findBySlug(@Param("slug") String slug);

    /** Trả null nếu không có - kiểm tra trùng `code` (409 DUPLICATE_CODE). */
    CategoryEntity findByCode(@Param("code") String code);

    List<CategoryEntity> findAllActive();

    /** Số danh mục con trực tiếp (kể cả đã ẩn) - chặn xoá khi còn con. */
    Long countChildren(@Param("categoryId") Long categoryId);
}
