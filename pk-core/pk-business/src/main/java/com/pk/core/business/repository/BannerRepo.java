package com.pk.core.business.repository;

import com.pk.core.model.entity.BannerEntity;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface BannerRepo extends PkRepo<BannerEntity, Long> {

    /** Banner ACTIVE, chưa xoá, của một vị trí - theo sort_order rồi id (dùng cho render storefront). */
    List<BannerEntity> findActiveByPlacementCode(@Param("placementCode") String placementCode);

    /** Số banner chưa xoá (mọi trạng thái) đang dùng vị trí này. */
    Long countByPlacementCode(@Param("placementCode") String placementCode);
}
