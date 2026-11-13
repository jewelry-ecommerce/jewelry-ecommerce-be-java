package com.pk.core.business.payment;

import com.pk.core.business.repository.OrderRepo;
import com.pk.core.business.repository.OrderTimelineRepo;
import com.pk.core.business.repository.PaymentRepo;
import com.pk.core.common.exception.BusinessException;
import com.pk.core.common.exception.ErrorCode;
import com.pk.core.model.dto.request.VnpayDemoConfirmRequestDto;
import com.pk.core.model.dto.response.VnpayReturnDto;
import com.pk.core.model.entity.OrderEntity;
import com.pk.core.model.entity.OrderTimelineEntity;
import com.pk.core.model.entity.PaymentEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Service
@RequiredArgsConstructor
public class VnpayDemoService {
    private final VnpayProperties properties;
    private final PaymentRepo payments;
    private final OrderRepo orders;
    private final OrderTimelineRepo timelines;

    @Transactional
    public VnpayReturnDto confirm(VnpayDemoConfirmRequestDto request) {
        requireDemo();
        String payload = VnpayGateway.demoPayload(request.orderCode(), request.txnRef(), request.amount());
        if (!VnpaySignature.matchesText(properties.getHashSecret(), payload, request.sig())) {
            throw BusinessException.badRequest(ErrorCode.PAYMENT_SIGNATURE_INVALID, "Chữ ký thanh toán thử không hợp lệ.");
        }
        return apply(request);
    }

    private void requireDemo() {
        if (properties.isDemoMode()) {
            properties.requireReady();
            return;
        }
        throw new BusinessException(503, ErrorCode.VNPAY_UNAVAILABLE,
                "Thanh toán thử đang tắt. Bật demo-mode hoặc dùng VNPay Sandbox đã đăng ký.");
    }

    private VnpayReturnDto apply(VnpayDemoConfirmRequestDto request) {
        PaymentEntity located = locate(request.txnRef());
        OrderEntity order = orders.findByIdForUpdate(located.getOrderId());
        PaymentEntity payment = payments.findOne(located.getId());
        if (order == null || payment == null) {
            throw BusinessException.badRequest(ErrorCode.NOT_FOUND, "Không tìm thấy giao dịch thanh toán thử.");
        }
        ensure(request, payment, order);
        if (skip(payment, request.success())) return view(order, request.success());
        settle(payment, order, request);
        return view(order, request.success());
    }

    private PaymentEntity locate(String txnRef) {
        PaymentEntity located = txnRef == null ? null : payments.findByGatewayOrderId(txnRef);
        if (located != null) return located;
        throw BusinessException.badRequest(ErrorCode.NOT_FOUND, "Không tìm thấy giao dịch thanh toán thử.");
    }

    private void ensure(VnpayDemoConfirmRequestDto request, PaymentEntity payment, OrderEntity order) {
        boolean sameMoney = sameAmount(request.amount(), payment.getAmount())
                && order.getGrandTotal() != null && order.getGrandTotal().compareTo(payment.getAmount()) == 0;
        boolean sameRef = request.orderCode().equals(order.getCode())
                && request.txnRef().equals(payment.getGatewayOrderId())
                && VnpayProperties.PROVIDER.equals(payment.getProvider())
                && VnpayProperties.PROVIDER.equals(order.getPaymentMethod());
        if (sameMoney && sameRef) return;
        throw BusinessException.badRequest(ErrorCode.PAYMENT_AMOUNT_MISMATCH, "Thông tin hoặc số tiền không khớp đơn hàng.");
    }

    private void settle(PaymentEntity payment, OrderEntity order, VnpayDemoConfirmRequestDto request) {
        boolean success = request.success();
        payment.setStatus(success ? PaymentEntity.SUCCESS : PaymentEntity.FAILED);
        payment.setTransactionId(success ? "DEMO" : null);
        payment.setSignature(request.sig());
        payment.touch();
        payments.update(payment);
        if (success) markPaid(order);
        timelines.create(new OrderTimelineEntity(order.getId(), order.getStatus(), note(success, order), "SYSTEM"));
    }

    private void markPaid(OrderEntity order) {
        order.setPaymentStatus(OrderEntity.PAID);
        order.touch();
        orders.update(order);
    }

    private static boolean skip(PaymentEntity payment, boolean success) {
        if (PaymentEntity.SUCCESS.equals(payment.getStatus()) && !success) {
            throw BusinessException.conflict(ErrorCode.PAYMENT_ALREADY_PROCESSED, "Giao dịch VNPay đã xác nhận.");
        }
        if (PaymentEntity.SUCCESS.equals(payment.getStatus())) return true;
        return PaymentEntity.FAILED.equals(payment.getStatus()) && !success;
    }

    private static boolean sameAmount(BigDecimal requested, BigDecimal stored) {
        if (requested == null || stored == null) return false;
        try {
            long left = requested.longValueExact();
            return left > 0 && left == stored.longValueExact();
        } catch (ArithmeticException failure) {
            return false;
        }
    }

    private static VnpayReturnDto view(OrderEntity order, boolean success) {
        String status = success ? "success" : "failed";
        String code = URLEncoder.encode(order.getCode(), StandardCharsets.UTF_8);
        String message = success ? "Thanh toán thử thành công" : "Thanh toán thử chưa thành công";
        return new VnpayReturnDto(order.getCode(), success, success ? "00" : "99", message,
                "/trang-thai-thanh-toan?status=" + status + "&orderCode=" + code);
    }

    private static String note(boolean success, OrderEntity order) {
        String text = success ? "Đã thanh toán thử VNPay" : "Thanh toán thử VNPay thất bại";
        if (success && OrderEntity.CANCELLED.equals(order.getStatus())) text += " — đơn đã hủy, cần kiểm tra";
        return text;
    }
}
