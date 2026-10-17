package com.pk.core.business.repository;

import com.pk.core.model.entity.OrderAuditLogEntity;
import org.springframework.data.repository.query.Param;

public interface OrderAuditLogRepo extends PkRepo<OrderAuditLogEntity, Long> {

    java.util.List<OrderAuditLogEntity> findByOrderId(@Param("orderId") Long orderId);
}
