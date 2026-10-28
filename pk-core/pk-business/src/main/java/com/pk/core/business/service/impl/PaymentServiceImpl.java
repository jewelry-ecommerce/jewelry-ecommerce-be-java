package com.pk.core.business.service.impl;

import lombok.RequiredArgsConstructor;

import com.pk.core.business.repository.OrderRepo;
import com.pk.core.business.repository.OrderTimelineRepo;
import com.pk.core.business.repository.PaymentRepo;
import com.pk.core.business.service.PaymentService;
import com.pk.core.common.exception.BusinessException;
import com.pk.core.common.exception.ErrorCode;
import com.pk.core.common.exception.ResourceNotFoundException;
import com.pk.core.model.dto.request.PaymentCallbackRequestDto;
import com.pk.core.model.entity.OrderEntity;
import com.pk.core.model.entity.OrderTimelineEntity;
import com.pk.core.model.entity.PaymentEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * XỬ LÝ CALLBACK THANH TOÁN - GIỚI HẠN QUAN TRỌNG (đọc trước khi dùng thật): dự án chưa tích hợp
 * VNPay/MoMo thật (không có tài liệu API + secret key thật của cổng nào), nên {@link #verifySignature}
 * ở đây chỉ là HMAC-SHA256 TỰ QUY ƯỚC (canonical string tự đặt, secret đọc từ
 * `app.payment.webhook-secret`, KHÔNG có giá trị mặc định: để trống thì callback bị từ chối 503) - hoàn toàn KHÔNG PHẢI thuật
 * toán ký thật của VNPay (HMAC theo vnp_SecureHash) hay MoMo. Trước khi dùng ngoài môi trường đồ án,
 * PHẢI thay verifySignature() bằng đúng thuật toán + secret thật của cổng thanh toán được chọn.
 */
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepo payments;
    private final OrderRepo orders;
    private final OrderTimelineRepo orderTimelines;

    /** Rỗng = chưa cấu hình cổng thanh toán -> callback bị từ chối (fail-closed), không có secret mặc định. */
    @Value("${app.payment.webhook-secret:}")
    private String webhookSecret;

    @Transactional
    @Override
    public void handleCallback(Long paymentId, PaymentCallbackRequestDto req) {
        if (webhookSecret == null || webhookSecret.isBlank()) {
            throw new BusinessException(503, ErrorCode.PAYMENT_NOT_CONFIGURED,
                    "Cổng thanh toán online chưa được cấu hình");
        }
        PaymentEntity payment = payments.findOne(paymentId);
        if (payment == null) {
            throw new ResourceNotFoundException("Giao dịch thanh toán", "Payment", paymentId);
        }
        if (!PaymentEntity.PENDING.equals(payment.getStatus())) {
            throw BusinessException.badRequest(ErrorCode.PAYMENT_ALREADY_PROCESSED,
                    "Thanh toán cho đơn " + req.getOrderCode() + " đã được xử lý trước đó", req.getOrderCode());
        }

        OrderEntity order = orders.findByCode(req.getOrderCode());
        if (order == null || !order.getId().equals(payment.getOrderId())) {
            throw new ResourceNotFoundException("Đơn hàng", "Order", req.getOrderCode());
        }

        if (!verifySignature(req)) {
            throw BusinessException.badRequest(ErrorCode.PAYMENT_SIGNATURE_INVALID,
                    "Chữ ký xác thực callback thanh toán không hợp lệ");
        }

        if (req.getAmount().compareTo(order.getGrandTotal()) != 0) {
            throw BusinessException.badRequest(ErrorCode.PAYMENT_AMOUNT_MISMATCH,
                    "Số tiền thanh toán không khớp: yêu cầu " + order.getGrandTotal() + ", nhận được " + req.getAmount(),
                    order.getGrandTotal(), req.getAmount());
        }

        boolean success = "SUCCESS".equalsIgnoreCase(req.getStatus());
        payment.setStatus(success ? PaymentEntity.SUCCESS : PaymentEntity.FAILED);
        payment.setTransactionId(req.getTransactionId());
        payment.setSignature(req.getSignature());
        payment.touch();
        payments.update(payment);

        if (success) {
            order.setPaymentStatus(OrderEntity.PAID);
            order.touch();
            orders.update(order);
            orderTimelines.create(new OrderTimelineEntity(order.getId(), order.getStatus(),
                    "Thanh toán thành công qua " + req.getProvider(), "SYSTEM"));
        } else {
            orderTimelines.create(new OrderTimelineEntity(order.getId(), order.getStatus(),
                    "Thanh toán thất bại qua " + req.getProvider(), "SYSTEM"));
        }
    }

    /** Xem cảnh báo ở javadoc class - KHÔNG phải thuật toán ký thật của cổng thanh toán nào. */
    private boolean verifySignature(PaymentCallbackRequestDto req) {
        try {
            String canonical = req.getProvider() + "|" + req.getTransactionId() + "|" + req.getOrderCode() + "|"
                    + req.getStatus() + "|" + req.getAmount().toPlainString();
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] raw = mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8));
            String computed = HexFormat.of().formatHex(raw);
            return MessageDigest.isEqual(
                    computed.getBytes(StandardCharsets.UTF_8), req.getSignature().getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            return false;
        }
    }
}
