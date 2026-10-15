package com.pk.core.business.service;

import com.pk.core.model.dto.request.CreateOrderRequestDto;
import com.pk.core.model.dto.request.GuestOrderRequestDto;
import com.pk.core.model.dto.response.OrderResponseDto;

/** Cửa vào đặt đơn của storefront: gọi {@link OrderService} (có Idempotency-Key) và tự thử lại khi hai request cùng khoá va nhau. */
public interface OrderPlacementService {

    OrderResponseDto place(Long userId, CreateOrderRequestDto req, String idempotencyKey);

    OrderResponseDto placeGuest(GuestOrderRequestDto req, String guestCartId, String idempotencyKey);
}
