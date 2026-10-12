package com.pk.core.business.repository;

import com.pk.core.model.entity.ProductInfoEntity;
import org.springframework.data.repository.query.Param;
import vn.com.unit.springframework.data.mirage.repository.query.Modifying;

import java.util.List;

public interface ProductInfoRepo extends PkRepo<ProductInfoEntity, Long> {

    /** Theo sort_order, rồi id. */
    List<ProductInfoEntity> findByProductId(@Param("productId") Long productId);

    @Modifying
    int deleteByProductId(@Param("productId") Long productId);
}
