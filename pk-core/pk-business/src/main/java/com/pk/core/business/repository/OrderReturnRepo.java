package com.pk.core.business.repository;

import com.pk.core.model.entity.OrderReturnEntity;
import org.springframework.data.repository.query.Param;
import vn.com.unit.springframework.data.mirage.repository.query.Modifying;

public interface OrderReturnRepo extends PkRepo<OrderReturnEntity, Long> {

    java.util.List<OrderReturnEntity> findByOrderId(@Param("orderId") Long orderId);

    OrderReturnEntity findByCode(@Param("code") String code);

    /** Chiếm yêu cầu NGUYÊN TỬ: chỉ đổi trạng thái khi vẫn đang `fromStatus`. Trả 0 nếu request khác đã xử lý trước
     * (duyệt/từ chối/huỷ song song) - nơi gọi phải dừng TRƯỚC mọi tác dụng phụ (kho, hoàn tiền, đơn thay thế, audit).
     * Trên PostgreSQL câu UPDATE khoá dòng nên request thứ hai chờ commit rồi thấy status khác -> 0. */
    @Modifying
    int updateStatusIfCurrent(@Param("id") Long id, @Param("fromStatus") String fromStatus,
                              @Param("toStatus") String toStatus);
}
