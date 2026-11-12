package com.pk.core.model.entity;

import com.pk.core.model.constant.TableConstant;
import com.pk.core.common.entity.UpdateEntity;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import vn.com.unit.miragesql.miragesql.annotation.Column;
import vn.com.unit.miragesql.miragesql.annotation.PrimaryKey;
import vn.com.unit.miragesql.miragesql.annotation.PrimaryKey.GenerationType;
import vn.com.unit.miragesql.miragesql.annotation.Table;

import java.math.BigDecimal;

/** Giao dịch thanh toán online cho 1 đơn hàng (paymentMethod khác "COD" - xem OrderServiceImpl).
 * Bảng `payments` không có deleted_id/deleted_date nên extends UpdateEntity (giống OrderEntity). */
@Getter
@Setter
@NoArgsConstructor
@Table(name = TableConstant.PAYMENTS)
public class PaymentEntity extends UpdateEntity {

    public static final String PENDING = "PENDING";
    public static final String SUCCESS = "SUCCESS";
    public static final String FAILED = "FAILED";

    @Id
    @PrimaryKey(generationType = GenerationType.SEQUENCE, generator = "payments_id_seq")
    @Column(name = "id")
    private Long id;

    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "provider")
    private String provider;

    @Column(name = "transaction_id")
    private String transactionId;

    @Column(name = "amount")
    private BigDecimal amount;

    @Column(name = "status")
    private String status = PENDING;

    @Column(name = "signature")
    private String signature;

    public PaymentEntity(Long orderId, String provider, BigDecimal amount) {
        this.orderId = orderId;
        this.provider = provider;
        this.amount = amount;
    }
}
