package com.pk.core.model.entity;

import com.pk.core.common.entity.UpdateEntity;
import com.pk.core.model.constant.TableConstant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import vn.com.unit.miragesql.miragesql.annotation.Column;
import vn.com.unit.miragesql.miragesql.annotation.PrimaryKey;
import vn.com.unit.miragesql.miragesql.annotation.PrimaryKey.GenerationType;
import vn.com.unit.miragesql.miragesql.annotation.Table;

import java.math.BigDecimal;
import java.util.Date;

/** Yêu cầu đổi/trả hàng. */
@Getter
@Setter
@NoArgsConstructor
@Table(name = TableConstant.ORDER_RETURNS)
public class OrderReturnEntity extends UpdateEntity {

    @Id
    @PrimaryKey(generationType = GenerationType.SEQUENCE, generator = "order_returns_id_seq")
    @Column(name = "id")
    private Long id;

    @Column(name = "code")
    private String code;

    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "type")
    private String type;

    @Column(name = "reason")
    private String reason;

    @Column(name = "reason_category")
    private String reasonCategory;

    @Column(name = "status")
    private String status = "Pending";

    @Column(name = "note")
    private String note;

    @Column(name = "return_total")
    private BigDecimal returnTotal = BigDecimal.ZERO;

    @Column(name = "exchange_total")
    private BigDecimal exchangeTotal = BigDecimal.ZERO;

    @Column(name = "price_difference")
    private BigDecimal priceDifference = BigDecimal.ZERO;

    @Column(name = "total_refund_amount")
    private BigDecimal totalRefundAmount = BigDecimal.ZERO;

    @Column(name = "return_fault_party")
    private String returnFaultParty;

    @Column(name = "payment_method")
    private String paymentMethod;

    @Column(name = "tracking_code")
    private String trackingCode;

    @Column(name = "evidence_urls")
    private String evidenceUrls;

    @Column(name = "shipping_address")
    private String shippingAddress;

    @Column(name = "refund_bank_holder")
    private String refundBankHolder;

    @Column(name = "refund_bank_number")
    private String refundBankNumber;

    @Column(name = "refund_bank_name")
    private String refundBankName;

    @Column(name = "refund_bank_branch")
    private String refundBankBranch;

    @Column(name = "exchange_order_id")
    private Long exchangeOrderId;

    @Column(name = "resolved_by_id")
    private Long resolvedById;

    @Column(name = "resolved_by_name")
    private String resolvedByName;

    @Column(name = "resolved_at")
    private Date resolvedAt;

    @Column(name = "resolved_note")
    private String resolvedNote;

    @Column(name = "reject_reason")
    private String rejectReason;

    @Column(name = "source")
    private String source = "ADMIN";
}
