package com.pk.core.business.repository;

import com.pk.core.model.entity.ProductApprovalRequestEntity;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProductApprovalRequestRepo extends PkRepo<ProductApprovalRequestEntity, Long> {

    /** Yêu cầu của 1 sản phẩm, mới nhất trước. */
    List<ProductApprovalRequestEntity> findByProductId(@Param("productId") Long productId);
}
