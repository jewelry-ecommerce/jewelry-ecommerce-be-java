package com.pk.core.business.repository;

import com.pk.core.model.entity.OrderReturnHistoryEntity;
import org.springframework.data.repository.query.Param;

public interface OrderReturnHistoryRepo extends PkRepo<OrderReturnHistoryEntity, Long> {

    java.util.List<OrderReturnHistoryEntity> findByReturnId(@Param("returnId") Long returnId);
}
