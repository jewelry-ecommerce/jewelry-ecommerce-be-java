package com.pk.core.business.service.impl;

import com.pk.core.business.service.OrderPlacementService;
import com.pk.core.business.service.OrderService;
import com.pk.core.model.dto.request.CreateOrderRequestDto;
import com.pk.core.model.dto.request.GuestOrderRequestDto;
import com.pk.core.model.dto.response.OrderResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.util.function.Supplier;

/**
 * KHÔNG @Transactional (cố ý): mỗi lần gọi OrderService là một transaction riêng. Khi hai request cùng Idempotency-Key chạy đồng thời,
 * transaction đến sau bị UNIQUE (owner, idem_key) từ chối và rollback hết; ta chạy lại một lần để nhận đúng đơn của request thắng.
 */
@Service
@RequiredArgsConstructor
public class OrderPlacementServiceImpl implements OrderPlacementService {

    private final OrderService orderService;

    @Override
    public OrderResponseDto place(Long userId, CreateOrderRequestDto req, String idempotencyKey) {
        return withReplay(idempotencyKey, () -> orderService.create(userId, req, idempotencyKey));
    }

    @Override
    public OrderResponseDto placeGuest(GuestOrderRequestDto req, String guestCartId, String idempotencyKey) {
        return withReplay(idempotencyKey, () -> orderService.createGuest(req, guestCartId, idempotencyKey));
    }

    private static OrderResponseDto withReplay(String key, Supplier<OrderResponseDto> call) {
        if (key == null || key.isBlank()) {
            return call.get();
        }
        try {
            return call.get();
        } catch (DataAccessException conflict) {
            return call.get(); // lần này khoá đã được commit bởi request kia -> trả lại đơn đó
        }
    }
}
