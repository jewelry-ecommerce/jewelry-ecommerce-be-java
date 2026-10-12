package com.pk.core.business.repository;

import com.pk.core.model.entity.ProductMixMatchEntity;
import org.springframework.data.repository.query.Param;
import vn.com.unit.springframework.data.mirage.repository.query.Modifying;

import java.util.List;

public interface ProductMixMatchRepo extends PkRepo<ProductMixMatchEntity, Long> {

    /** Theo sort_order, rồi id. */
    List<ProductMixMatchEntity> findByProductId(@Param("productId") Long productId);

    @Modifying
    int deleteByProductId(@Param("productId") Long productId);
}
