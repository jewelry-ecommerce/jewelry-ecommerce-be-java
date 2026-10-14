package com.pk.core.business.repository;

import com.pk.core.model.entity.CmsPageEntity;
import org.springframework.data.repository.query.Param;

public interface CmsPageRepo extends PkRepo<CmsPageEntity, Long> {

    /** Trả null nếu không có - dùng kiểm tra trùng slug lúc tạo/sửa trang (409 DUPLICATE_SLUG,
     * cùng quy ước Category/Collection/Product). */
    CmsPageEntity findBySlug(@Param("slug") String slug);

    /** Trang đang public theo slug của bản xuất bản (khác slug nháp nếu admin đã đổi nhưng chưa xuất bản lại). */
    CmsPageEntity findByPublishedSlug(@Param("slug") String slug);
}
