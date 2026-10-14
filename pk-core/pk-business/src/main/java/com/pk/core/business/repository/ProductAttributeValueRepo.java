package com.pk.core.business.repository;

import com.pk.core.model.entity.ProductAttributeValueEntity;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProductAttributeValueRepo extends PkRepo<ProductAttributeValueEntity, Long> {

    /** Theo sort_order, rồi id. */
    List<ProductAttributeValueEntity> findByAttributeId(@Param("attributeId") Long attributeId);
}
