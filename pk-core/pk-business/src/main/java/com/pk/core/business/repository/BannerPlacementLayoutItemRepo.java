package com.pk.core.business.repository;

import com.pk.core.model.entity.BannerPlacementLayoutItemEntity;
import org.springframework.data.repository.query.Param;
import vn.com.unit.springframework.data.mirage.repository.query.Modifying;

import java.util.List;

public interface BannerPlacementLayoutItemRepo extends PkRepo<BannerPlacementLayoutItemEntity, Long> {

    /** Theo slot_key, priority, id. */
    List<BannerPlacementLayoutItemEntity> findByLayoutId(@Param("layoutId") Long layoutId);

    /** Chỉ xoá các dòng gắn banner-vào-slot, không đụng bảng banners. */
    @Modifying
    int deleteByLayoutId(@Param("layoutId") Long layoutId);
}
