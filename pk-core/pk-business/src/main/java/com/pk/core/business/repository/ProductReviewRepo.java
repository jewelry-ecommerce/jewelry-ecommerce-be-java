package com.pk.core.business.repository;

import com.pk.core.model.entity.ProductReviewEntity;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProductReviewRepo extends PkRepo<ProductReviewEntity, Long> {

    List<ProductReviewEntity> findByProductId(@Param("productId") Long productId);

    List<ProductReviewEntity> findByStatus(@Param("status") String status);
}
