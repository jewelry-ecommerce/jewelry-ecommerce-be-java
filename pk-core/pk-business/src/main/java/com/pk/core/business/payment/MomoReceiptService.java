package com.pk.core.business.payment;

import com.pk.core.business.repository.OrderRepo;
import com.pk.core.business.repository.OrderTimelineRepo;
import com.pk.core.business.repository.PaymentRepo;
import com.pk.core.common.exception.BusinessException;
import com.pk.core.common.exception.ErrorCode;
import com.pk.core.model.entity.OrderEntity;
import com.pk.core.model.entity.OrderTimelineEntity;
import com.pk.core.model.entity.PaymentEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MomoReceiptService {
    private final MomoProperties properties;
    private final PaymentRepo payments;
    private final OrderRepo orders;
    private final OrderTimelineRepo timelines;

    @Transactional
    public String handleReturn(Map<String, String> params) {
        Outcome outcome = apply(params);
        String status = outcome.success() ? "success" : "failed";
        String code = URLEncoder.encode(outcome.order().getCode(), StandardCharsets.UTF_8);
        return "/trang-thai-thanh-toan?status=" + status + "&orderCode=" + code;
    }

    @Transactional
    public void handleIpn(Map<String, String> params) {
        apply(params);
    }

    private Outcome apply(Map<String, String> params) {
        properties.requireReady();
        verify(params);
        PaymentEntity located = locate(params.get("orderId"));
        OrderEntity order = orders.findByIdForUpdate(located.getOrderId());
        PaymentEntity payment = payments.findOne(located.getId());
        if (order == null || payment == null) {
            throw BusinessException.badRequest(ErrorCode.NOT_FOUND, "Không tìm thấy giao dịch MoMo.");
        }
        ensure(params, payment, order);
        if (skip(payment, params)) return new Outcome(order, PaymentEntity.SUCCESS.equals(payment.getStatus()));
        boolean success = "0".equals(params.get("resultCode"));
        settle(payment, order, params, success);
        return new Outcome(order, success);
    }

    private void verify(Map<String, String> params) {
        String raw = MomoSignature.noticeRaw(properties.getAccessKey(), params);
        if (MomoSignature.matches(properties.getSecretKey(), raw, params.get("signature"))) return;
        throw BusinessException.badRequest(ErrorCode.PAYMENT_SIGNATURE_INVALID, "Chữ ký MoMo không hợp lệ.");
    }

    private PaymentEntity locate(String orderId) {
        PaymentEntity located = orderId == null ? null : payments.findByGatewayOrderId(orderId);
        if (located != null) return located;
        throw BusinessException.badRequest(ErrorCode.NOT_FOUND, "Không tìm thấy giao dịch MoMo.");
    }

    private void ensure(Map<String, String> params, PaymentEntity payment, OrderEntity order) {
        boolean same = properties.getPartnerCode().equals(params.get("partnerCode"))
                && payment.getGatewayOrderId().equals(params.get("orderId"))
                && String.valueOf(payment.getAmount().longValueExact()).equals(params.get("amount"))
                && MomoProperties.PROVIDER.equals(payment.getProvider())
                && MomoProperties.PROVIDER.equals(order.getPaymentMethod());
        if (same) return;
        throw BusinessException.badRequest(ErrorCode.PAYMENT_AMOUNT_MISMATCH, "Thông tin hoặc số tiền MoMo không khớp đơn hàng.");
    }

    private static boolean skip(PaymentEntity payment, Map<String, String> params) {
        boolean success = "0".equals(params.get("resultCode"));
        if (PaymentEntity.SUCCESS.equals(payment.getStatus()) && !success) {
            throw BusinessException.conflict(ErrorCode.PAYMENT_ALREADY_PROCESSED, "Giao dịch MoMo đã xác nhận.");
        }
        if (PaymentEntity.SUCCESS.equals(payment.getStatus())) return true;
        return PaymentEntity.FAILED.equals(payment.getStatus()) && !success;
    }

    private void settle(PaymentEntity payment, OrderEntity order, Map<String, String> params, boolean success) {
        payment.setStatus(success ? PaymentEntity.SUCCESS : PaymentEntity.FAILED);
        payment.setTransactionId(params.get("transId"));
        payment.setSignature(params.get("signature"));
        payment.touch();
        payments.update(payment);
        if (!success) return;
        order.setPaymentStatus(OrderEntity.PAID);
        order.touch();
        orders.update(order);
        timelines.create(new OrderTimelineEntity(order.getId(), order.getStatus(), "Thanh toán MoMo thành công", "SYSTEM"));
    }

    private record Outcome(OrderEntity order, boolean success) {
    }
}
