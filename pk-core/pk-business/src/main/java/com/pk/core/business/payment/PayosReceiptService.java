package com.pk.core.business.payment;

import com.fasterxml.jackson.databind.JsonNode;
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
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PayosReceiptService {
    private final PayosProperties properties;
    private final PaymentRepo payments;
    private final OrderRepo orders;
    private final OrderTimelineRepo timelines;

    @Transactional
    public String handleReturn(Map<String, String> params) {
        return redirect(params, false);
    }

    @Transactional
    public String handleCancel(Map<String, String> params) {
        return redirect(params, true);
    }

    @Transactional
    public void handleWebhook(JsonNode body) {
        properties.requireReady();
        JsonNode data = body == null ? null : body.path("data");
        String signature = body == null ? null : body.path("signature").asText(null);
        if (!PayosSignature.matches(properties.getChecksumKey(), PayosSignature.webhookRaw(data), signature)) {
            throw BusinessException.badRequest(ErrorCode.PAYMENT_SIGNATURE_INVALID, "Chữ ký PayOS không hợp lệ.");
        }
        applyWebhook(data, body != null && body.path("success").asBoolean(false));
    }

    private void applyWebhook(JsonNode data, boolean successFlag) {
        PaymentEntity located = find(data.path("orderCode").asText(null));
        if (located == null) return;
        OrderEntity order = orders.findByIdForUpdate(located.getOrderId());
        PaymentEntity payment = payments.findOne(located.getId());
        if (order == null || payment == null) return;
        ensure(data, payment, order);
        boolean success = "00".equals(data.path("code").asText()) || successFlag;
        if (skip(payment, success)) return;
        settle(payment, order, data, success);
    }

    private String redirect(Map<String, String> params, boolean cancelled) {
        PaymentEntity payment = find(params.get("orderCode"));
        OrderEntity order = payment == null ? null : orders.findOne(payment.getOrderId());
        if (cancelled && payment != null && PaymentEntity.PENDING.equals(payment.getStatus())) {
            markFailed(payment);
        }
        boolean success = payment != null && PaymentEntity.SUCCESS.equals(payment.getStatus());
        if (!success && !cancelled && payment != null) success = paid(params);
        String code = order == null ? "" : URLEncoder.encode(order.getCode(), StandardCharsets.UTF_8);
        return "/trang-thai-thanh-toan?status=" + (success ? "success" : "failed") + "&orderCode=" + code;
    }

    private PaymentEntity find(String orderCode) {
        if (orderCode == null || orderCode.isBlank()) return null;
        return payments.findByGatewayOrderId(orderCode.trim());
    }

    private void ensure(JsonNode data, PaymentEntity payment, OrderEntity order) {
        boolean same = payment.getGatewayOrderId().equals(data.path("orderCode").asText())
                && String.valueOf(payment.getAmount().longValueExact()).equals(data.path("amount").asText())
                && PayosProperties.PROVIDER.equals(payment.getProvider())
                && PayosProperties.PROVIDER.equals(order.getPaymentMethod());
        if (same) return;
        throw BusinessException.badRequest(ErrorCode.PAYMENT_AMOUNT_MISMATCH, "Thông tin hoặc số tiền PayOS không khớp đơn hàng.");
    }

    private static boolean skip(PaymentEntity payment, boolean success) {
        if (PaymentEntity.SUCCESS.equals(payment.getStatus()) && !success) {
            throw BusinessException.conflict(ErrorCode.PAYMENT_ALREADY_PROCESSED, "Giao dịch PayOS đã xác nhận.");
        }
        if (PaymentEntity.SUCCESS.equals(payment.getStatus())) return true;
        return PaymentEntity.FAILED.equals(payment.getStatus()) && !success;
    }

    private void settle(PaymentEntity payment, OrderEntity order, JsonNode data, boolean success) {
        payment.setStatus(success ? PaymentEntity.SUCCESS : PaymentEntity.FAILED);
        payment.setTransactionId(text(data, "reference", "paymentLinkId"));
        payment.setSignature(data.path("paymentLinkId").asText(null));
        payment.touch();
        payments.update(payment);
        if (!success) return;
        order.setPaymentStatus(OrderEntity.PAID);
        order.touch();
        orders.update(order);
        timelines.create(new OrderTimelineEntity(order.getId(), order.getStatus(), "Thanh toán PayOS thành công", "SYSTEM"));
    }

    private void markFailed(PaymentEntity payment) {
        payment.setStatus(PaymentEntity.FAILED);
        payment.touch();
        payments.update(payment);
    }

    private static boolean paid(Map<String, String> params) {
        String status = value(params, "status").toUpperCase(Locale.ROOT);
        return "PAID".equals(status) || "00".equals(value(params, "code"));
    }

    private static String text(JsonNode data, String first, String second) {
        String value = data.path(first).asText("");
        return value.isBlank() ? data.path(second).asText(null) : value;
    }

    private static String value(Map<String, String> params, String key) {
        String value = params.get(key);
        return value == null ? "" : value;
    }
}
