package com.pk.core.business.payment;

import com.pk.core.business.repository.OrderRepo;
import com.pk.core.business.repository.OrderTimelineRepo;
import com.pk.core.business.repository.PaymentRepo;
import com.pk.core.common.exception.BusinessException;
import com.pk.core.common.exception.ErrorCode;
import com.pk.core.model.dto.response.VnpayIpnResponse;
import com.pk.core.model.dto.response.VnpayReturnDto;
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
public class VnpayReceiptService {
    private final VnpayProperties properties;
    private final PaymentRepo payments;
    private final OrderRepo orders;
    private final OrderTimelineRepo timelines;

    @Transactional
    public VnpayReturnDto handleReturn(Map<String, String> params) {
        Outcome outcome = apply(params);
        return view(outcome);
    }

    @Transactional
    public VnpayIpnResponse handleIpn(Map<String, String> params) {
        try {
            apply(params);
            return VnpayIpnResponse.confirmed();
        } catch (BusinessException error) {
            return VnpayIpnResponse.from(error);
        } catch (RuntimeException failure) {
            return VnpayIpnResponse.unknown();
        }
    }

    private Outcome apply(Map<String, String> params) {
        properties.requireReady();
        verify(params);
        PaymentEntity located = locate(params.get("vnp_TxnRef"));
        OrderEntity order = orders.findByIdForUpdate(located.getOrderId());
        PaymentEntity payment = payments.findOne(located.getId());
        if (order == null || payment == null) {
            throw BusinessException.badRequest(ErrorCode.NOT_FOUND, "Không tìm thấy giao dịch VNPay.");
        }
        ensureReference(params, payment, order);
        if (skip(payment, params)) return new Outcome(order, PaymentEntity.SUCCESS.equals(payment.getStatus()));
        boolean success = paid(params);
        settle(payment, order, params, success);
        return new Outcome(order, success);
    }

    private void verify(Map<String, String> params) {
        if (VnpaySignature.matches(properties.getHashSecret(), params, params.get("vnp_SecureHash"))) return;
        throw BusinessException.badRequest(ErrorCode.PAYMENT_SIGNATURE_INVALID, "Chữ ký VNPay không hợp lệ.");
    }

    private PaymentEntity locate(String txnRef) {
        PaymentEntity located = txnRef == null ? null : payments.findByGatewayOrderId(txnRef);
        if (located != null) return located;
        throw BusinessException.badRequest(ErrorCode.NOT_FOUND, "Không tìm thấy giao dịch VNPay.");
    }

    private void ensureReference(Map<String, String> params, PaymentEntity payment, OrderEntity order) {
        boolean sameMoney = String.valueOf(minor(payment)).equals(params.get("vnp_Amount"))
                && order.getGrandTotal().compareTo(payment.getAmount()) == 0;
        boolean sameRef = properties.getTmnCode().equals(params.get("vnp_TmnCode"))
                && payment.getGatewayOrderId().equals(params.get("vnp_TxnRef"))
                && VnpayProperties.PROVIDER.equals(payment.getProvider())
                && VnpayProperties.PROVIDER.equals(order.getPaymentMethod())
                && VnpayGateway.orderInfo(order.getCode()).equals(params.get("vnp_OrderInfo"));
        if (sameMoney && sameRef) return;
        throw BusinessException.badRequest(ErrorCode.PAYMENT_AMOUNT_MISMATCH, "Thông tin hoặc số tiền VNPay không khớp đơn hàng.");
    }

    private static boolean skip(PaymentEntity payment, Map<String, String> params) {
        if (PaymentEntity.SUCCESS.equals(payment.getStatus()) && !paid(params)) {
            throw BusinessException.conflict(ErrorCode.PAYMENT_ALREADY_PROCESSED, "Giao dịch VNPay đã xác nhận.");
        }
        if (PaymentEntity.SUCCESS.equals(payment.getStatus())) return true;
        return PaymentEntity.FAILED.equals(payment.getStatus()) && !paid(params);
    }

    private void settle(PaymentEntity payment, OrderEntity order, Map<String, String> params, boolean success) {
        payment.setStatus(success ? PaymentEntity.SUCCESS : PaymentEntity.FAILED);
        payment.setTransactionId(params.get("vnp_TransactionNo"));
        payment.setSignature(params.get("vnp_SecureHash"));
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

    private static VnpayReturnDto view(Outcome outcome) {
        String status = outcome.success() ? "success" : "failed";
        String code = URLEncoder.encode(outcome.order().getCode(), StandardCharsets.UTF_8);
        String message = outcome.success() ? "Thanh toán VNPay Sandbox thành công" : "Thanh toán VNPay Sandbox chưa thành công";
        return new VnpayReturnDto(outcome.order().getCode(), outcome.success(), outcome.success() ? "00" : "99", message,
                "/trang-thai-thanh-toan?status=" + status + "&orderCode=" + code);
    }

    private static long minor(PaymentEntity payment) {
        try {
            return payment.getAmount().movePointRight(2).longValueExact();
        } catch (ArithmeticException | NullPointerException failure) {
            throw BusinessException.badRequest(ErrorCode.PAYMENT_AMOUNT_MISMATCH, "Số tiền VNPay không hợp lệ.");
        }
    }

    private static boolean paid(Map<String, String> params) {
        String status = params.get("vnp_TransactionStatus");
        return "00".equals(params.get("vnp_ResponseCode")) && (status == null || status.isBlank() || "00".equals(status));
    }

    private static String note(boolean success, OrderEntity order) {
        String note = success ? "Đã thanh toán VNPay Sandbox" : "Thanh toán VNPay Sandbox thất bại";
        if (success && OrderEntity.CANCELLED.equals(order.getStatus())) note += " — đơn đã hủy, cần kiểm tra hoàn tiền";
        return note;
    }

    private record Outcome(OrderEntity order, boolean success) {
    }
}
