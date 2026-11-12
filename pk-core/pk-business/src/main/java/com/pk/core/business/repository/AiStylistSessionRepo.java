package com.pk.core.business.repository;

import com.pk.core.model.entity.AiStylistSessionEntity;
import org.springframework.data.repository.query.Param;
import vn.com.unit.springframework.data.mirage.repository.query.Modifying;

import java.util.List;

public interface AiStylistSessionRepo extends PkRepo<AiStylistSessionEntity, Long> {

    /** Mới nhất trước, tối đa pageSize dòng. */
    List<AiStylistSessionEntity> recent(@Param("userId") Long userId, @Param("pageSize") int pageSize);

    /** Giữ `keep` phiên gần nhất của user, xoá phần cũ hơn. */
    @Modifying
    int trimTo(@Param("userId") Long userId, @Param("keep") int keep);
}
