package com.pk.core.business.payment;

import com.pk.core.business.repository.OrderRepo;
import com.pk.core.business.repository.PaymentRepo;
import com.pk.core.business.service.OrderService;
import com.pk.core.common.exception.BusinessException;
import com.pk.core.common.exception.ErrorCode;
import com.pk.core.model.dto.request.MomoStartRequestDto;
import com.pk.core.model.dto.response.MomoStartResponseDto;
import com.pk.core.model.dto.response.OrderResponseDto;
import com.pk.core.model.entity.OrderEntity;
import com.pk.core.model.entity.PaymentEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
@RequiredArgsConstructor
public class MomoCheckoutService {
    private static final Set<String> PAYABLE = Set.of(OrderEntity.PENDING, OrderEntity.CONFIRMED);
    private final OrderService orderService;
    private final OrderRepo orders;
    private final PaymentRepo payments;
    private final MomoGateway gateway;

    @Transactional
    public MomoStartResponseDto start(Long userId, MomoStartRequestDto request) {
        OrderResponseDto authorized = userId == null
                ? orderService.findGuestOrder(request.orderCode(), request.phone())
                : orderService.findByCodeForUser(userId, request.orderCode());
        return checkout(orders.findByIdForUpdate(authorized.getId()));
    }

    private MomoStartResponseDto checkout(OrderEntity order) {
        PaymentEntity payment = order == null ? null : payments.findByOrderId(order.getId());
        if (order == null || !payable(order, payment)) {
            throw BusinessException.conflict(ErrorCode.PAYMENT_ALREADY_PROCESSED, "Đơn không còn chờ thanh toán MoMo.");
        }
        String payUrl = gateway.create(order, payment);
        payment.setCheckoutUrl(payUrl);
        payment.touch();
        payments.update(payment);
        return new MomoStartResponseDto(order.getCode(), payUrl, true);
    }

    private static boolean payable(OrderEntity order, PaymentEntity payment) {
        if (payment == null || payment.getGatewayOrderId() == null) return false;
        if (!MomoProperties.PROVIDER.equals(order.getPaymentMethod()) || !MomoProperties.PROVIDER.equals(payment.getProvider())) return false;
        if (!PaymentEntity.PENDING.equals(payment.getStatus()) || !OrderEntity.UNPAID.equals(order.getPaymentStatus())) return false;
        return PAYABLE.contains(order.getStatus());
    }
}
