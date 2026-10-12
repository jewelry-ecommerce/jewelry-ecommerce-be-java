package com.pk.core.business.repository;

import com.pk.core.model.entity.OrderItemEntity;
import org.springframework.data.repository.query.Param;
import vn.com.unit.springframework.data.mirage.repository.query.Modifying;

import java.util.List;

public interface OrderItemRepo extends PkRepo<OrderItemEntity, Long> {

    List<OrderItemEntity> findByOrderId(@Param("orderId") Long orderId);

    /** Cộng `qty` vào số đã trả NGUYÊN TỬ, chỉ khi không vượt số lượng đã mua. Trả 0 nếu vượt (trả trùng / đồng thời). */
    @Modifying
    int addReturned(@Param("id") Long id, @Param("qty") int qty);
}
