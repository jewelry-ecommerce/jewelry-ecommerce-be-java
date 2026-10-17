package com.pk.core.business.repository;

import com.pk.core.model.entity.OrderReturnItemEntity;
import org.springframework.data.repository.query.Param;

public interface OrderReturnItemRepo extends PkRepo<OrderReturnItemEntity, Long> {

    java.util.List<OrderReturnItemEntity> findByReturnId(@Param("returnId") Long returnId);
}
