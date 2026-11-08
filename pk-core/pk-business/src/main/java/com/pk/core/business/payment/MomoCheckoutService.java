package com.pk.core.business.payment;

import com.pk.core.business.repository.OrderRepo;
import com.pk.core.business.repository.PaymentRepo;
import com.pk.core.business.service.OrderService;
import com.pk.core.common.exception.BusinessException;
import com.pk.core.common.exception.ErrorCode;
import com.pk.core.model.dto.request.MomoStartRequestDto;
import com.pk.core.model.dto.response.MomoStartResponseDto;
import com.pk.core.model.entity.OrderEntity;
import com.pk.core.model.entity.PaymentEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class MomoCheckoutService {
    private final OrderService orderService;
    private final OrderRepo orders;
    private final PaymentRepo payments;
    private final MomoGateway gateway;
    private final MomoProperties properties;
    private static final Set<String> PAYABLE = Set.of(OrderEntity.PENDING, OrderEntity.CONFIRMED);

    @Transactional
    public MomoStartResponseDto start(Long userId, MomoStartRequestDto request) {
        properties.requireReady();
        var authorized = userId == null ? orderService.findGuestOrder(request.orderCode(), request.phone())
                : orderService.findByCodeForUser(userId, request.orderCode());
        OrderEntity order = orders.findByIdForUpdate(authorized.getId());
        PaymentEntity payment = payments.findByOrderId(order.getId());
        if (!PAYABLE.contains(order.getStatus()) || !OrderEntity.UNPAID.equals(order.getPaymentStatus())
                || !MomoProperties.PROVIDER.equals(order.getPaymentMethod()) || payment == null
                || !PaymentEntity.PENDING.equals(payment.getStatus()) || payment.getGatewayOrderId() == null) {
            throw BusinessException.conflict(ErrorCode.PAYMENT_ALREADY_PROCESSED, "Đơn không còn chờ thanh toán MoMo.");
        }
        if (payment.getCheckoutUrl() == null) {
            payment.setCheckoutUrl(gateway.create(order, payment)); payment.touch(); payments.update(payment);
        }
        return new MomoStartResponseDto(order.getCode(), MomoGateway.validatePayUrl(payment.getCheckoutUrl()), true);
    }
}
