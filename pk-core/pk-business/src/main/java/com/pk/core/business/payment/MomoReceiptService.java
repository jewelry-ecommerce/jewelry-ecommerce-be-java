package com.pk.core.business.payment;

import com.pk.core.business.repository.OrderRepo;
import com.pk.core.business.repository.PaymentRepo;
import com.pk.core.business.repository.OrderTimelineRepo;
import com.pk.core.common.exception.BusinessException;
import com.pk.core.common.exception.ErrorCode;
import com.pk.core.model.dto.request.MomoNotificationDto;
import com.pk.core.model.entity.OrderEntity;
import com.pk.core.model.entity.OrderTimelineEntity;
import com.pk.core.model.entity.PaymentEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class MomoReceiptService {
    private final MomoProperties properties;
    private final PaymentRepo payments;
    private final OrderRepo orders;
    private final OrderTimelineRepo timelines;

    @Transactional
    public void receive(MomoNotificationDto notification) {
        properties.requireReady();
        if (!properties.getPartnerCode().equals(notification.partnerCode())
                || !MomoSignature.matches(properties.getSecretKey(), notification.signatureFields(properties.getAccessKey()), notification.signature())) {
            throw BusinessException.badRequest(ErrorCode.PAYMENT_SIGNATURE_INVALID, "Chữ ký MoMo không hợp lệ.");
        }
        PaymentEntity located = payments.findByGatewayOrderId(notification.orderId());
        if (located == null) throw BusinessException.badRequest(ErrorCode.PAYMENT_SIGNATURE_INVALID, "Không tìm thấy giao dịch MoMo.");
        OrderEntity order = orders.findByIdForUpdate(located.getOrderId());
        PaymentEntity payment = payments.findOne(located.getId());
        validateReference(notification, payment, order);
        if (PaymentEntity.SUCCESS.equals(payment.getStatus()) || notification.resultCode() == 9000) return;
        if (PaymentEntity.FAILED.equals(payment.getStatus()) && notification.resultCode() != 0) return;
        boolean success = notification.resultCode() == 0;
        payment.setStatus(success ? PaymentEntity.SUCCESS : PaymentEntity.FAILED);
        payment.setTransactionId(notification.transId().toString()); payment.setSignature(notification.signature());
        payment.touch(); payments.update(payment);
        if (success) { order.setPaymentStatus(OrderEntity.PAID); order.touch(); orders.update(order); }
        String note = success ? "Đã thanh toán MoMo Sandbox" : "Thanh toán MoMo Sandbox thất bại";
        if (success && OrderEntity.CANCELLED.equals(order.getStatus())) note += " — đơn đã hủy, cần kiểm tra hoàn tiền";
        timelines.create(new OrderTimelineEntity(order.getId(), order.getStatus(), note, "SYSTEM"));
    }
    private void validateReference(MomoNotificationDto notice, PaymentEntity payment, OrderEntity order) {
        if (!MomoProperties.PROVIDER.equals(payment.getProvider()) || !MomoProperties.PROVIDER.equals(order.getPaymentMethod())
                || !notice.orderId().equals(notice.requestId()) || !MomoGateway.orderInfo(order.getCode()).equals(notice.orderInfo())
                || payment.getAmount().compareTo(BigDecimal.valueOf(notice.amount())) != 0
                || order.getGrandTotal().compareTo(payment.getAmount()) != 0) {
            throw BusinessException.badRequest(ErrorCode.PAYMENT_AMOUNT_MISMATCH, "Thông tin hoặc số tiền MoMo không khớp đơn hàng.");
        }
    }
}
