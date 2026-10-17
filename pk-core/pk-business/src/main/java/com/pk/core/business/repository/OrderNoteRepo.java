package com.pk.core.business.repository;

import com.pk.core.model.entity.OrderNoteEntity;
import org.springframework.data.repository.query.Param;

public interface OrderNoteRepo extends PkRepo<OrderNoteEntity, Long> {

    java.util.List<OrderNoteEntity> findByOrderId(@Param("orderId") Long orderId);
}
