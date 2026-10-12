package com.pk.core.business.repository;

import com.pk.core.model.entity.BannerPlacementLayoutEntity;
import org.springframework.data.repository.query.Param;
import vn.com.unit.springframework.data.mirage.repository.query.Modifying;

import java.util.List;

public interface BannerPlacementLayoutRepo extends PkRepo<BannerPlacementLayoutEntity, Long> {

    /** Mới nhất trước (id giảm dần). */
    List<BannerPlacementLayoutEntity> findByPlacementId(@Param("placementId") Long placementId);

    /** Chuyển các layout ACTIVE khác của vị trí về INACTIVE (mỗi vị trí chỉ một layout ACTIVE). */
    @Modifying
    int deactivateOthers(@Param("placementId") Long placementId, @Param("keepId") Long keepId);
}
