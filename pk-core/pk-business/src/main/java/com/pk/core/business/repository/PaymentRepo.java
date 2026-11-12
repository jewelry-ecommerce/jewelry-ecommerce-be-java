package com.pk.core.business.repository;

import com.pk.core.model.entity.PaymentEntity;
import org.springframework.data.repository.query.Param;

public interface PaymentRepo extends PkRepo<PaymentEntity, Long> {

    /** Trả null nếu chưa có giao dịch nào cho đơn này (VD đơn thanh toán COD). Model đơn giản: mỗi
     * đơn tối đa 1 giao dịch thanh toán (chưa hỗ trợ thử lại nhiều lần/nhiều provider). */
    PaymentEntity findByOrderId(@Param("orderId") Long orderId);
}
