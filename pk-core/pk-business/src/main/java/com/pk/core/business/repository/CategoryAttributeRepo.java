package com.pk.core.business.repository;

import com.pk.core.model.entity.CategoryAttributeEntity;
import org.springframework.data.repository.query.Param;
import vn.com.unit.springframework.data.mirage.repository.query.Modifying;

import java.util.List;

public interface CategoryAttributeRepo extends PkRepo<CategoryAttributeEntity, Long> {

    List<CategoryAttributeEntity> findByCategoryId(@Param("categoryId") Long categoryId);

    @Modifying
    int deleteByCategoryId(@Param("categoryId") Long categoryId);

    /** Số danh mục đang gán thuộc tính này (chặn xoá thuộc tính). */
    Long countByAttributeId(@Param("attributeId") Long attributeId);
}
