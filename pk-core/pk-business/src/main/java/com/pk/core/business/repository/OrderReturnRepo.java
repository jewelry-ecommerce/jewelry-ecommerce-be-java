package com.pk.core.business.repository;

import com.pk.core.model.entity.OrderReturnEntity;
import org.springframework.data.repository.query.Param;

public interface OrderReturnRepo extends PkRepo<OrderReturnEntity, Long> {

    java.util.List<OrderReturnEntity> findByOrderId(@Param("orderId") Long orderId);

    OrderReturnEntity findByCode(@Param("code") String code);
}
