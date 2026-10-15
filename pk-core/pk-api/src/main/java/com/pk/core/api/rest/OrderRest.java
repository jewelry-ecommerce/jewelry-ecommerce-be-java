package com.pk.core.api.rest;

import org.springframework.web.bind.annotation.RequestMapping;
import lombok.RequiredArgsConstructor;

import com.pk.core.business.service.OrderPlacementService;
import com.pk.core.business.service.OrderService;
import com.pk.core.business.web.AbstractRest;
import com.pk.core.common.web.BaseRes;
import com.pk.core.identity.security.model.SecurityUser;
import com.pk.core.model.constant.UrlConstant;
import com.pk.core.model.dto.request.CancelOrderRequestDto;
import com.pk.core.model.dto.request.CreateOrderRequestDto;
import com.pk.core.model.dto.request.GuestOrderRequestDto;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Đặt hàng/xem đơn/huỷ đơn của khách đang đăng nhập (/storefront/order) - yêu cầu Bearer token
 * (mặc định anyRequest().authenticated() ở SecurityConfig). */
@RestController
@RequestMapping(UrlConstant.Common.API + UrlConstant.Common.VERSION)
@RequiredArgsConstructor
public class OrderRest extends AbstractRest {

    private final OrderService orderService;
    private final OrderPlacementService orderPlacement;

    @PostMapping(UrlConstant.Order.BASE)
    @ResponseStatus(HttpStatus.CREATED)
    public BaseRes create(@AuthenticationPrincipal SecurityUser principal,
                           @Valid @RequestBody CreateOrderRequestDto request,
                           @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                           HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            return restSuccessHandle.handleSuccess(orderPlacement.place(principal.getId(), request, idempotencyKey));
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }

    /** Đặt hàng không cần đăng nhập: địa chỉ gửi kèm trong body; header X-Guest-Cart-Id / x-guest-id (nếu có) để dọn giỏ khách. */
    @PostMapping(UrlConstant.Order.GUEST)
    @ResponseStatus(HttpStatus.CREATED)
    public BaseRes createGuest(@Valid @RequestBody GuestOrderRequestDto request,
                               @RequestHeader(value = "X-Guest-Cart-Id", required = false) String guestId,
                               @RequestHeader(value = "x-guest-id", required = false) String guestIdAlias,
                               @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                               HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            return restSuccessHandle.handleSuccess(orderPlacement.placeGuest(request,
                    guestId != null && !guestId.isBlank() ? guestId : guestIdAlias, idempotencyKey));
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }

    @GetMapping(UrlConstant.Order.GUEST + "/{orderCode}")
    public BaseRes guestDetail(@PathVariable String orderCode, @RequestParam String phone,
                               HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            return restSuccessHandle.handleSuccess(orderService.findGuestOrder(orderCode, phone));
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }

    @GetMapping(UrlConstant.Order.BASE)
    public BaseRes list(@AuthenticationPrincipal SecurityUser principal,
                         @RequestParam(required = false, defaultValue = "1") int page,
                         @RequestParam(required = false, defaultValue = "20") int take,
                         HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            return restSuccessHandle.handleSuccess(orderService.listMine(principal.getId(), page, take));
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }

    @GetMapping(UrlConstant.Order.BASE + "/{orderCode}")
    public BaseRes detail(@AuthenticationPrincipal SecurityUser principal, @PathVariable String orderCode,
                           HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            return restSuccessHandle.handleSuccess(orderService.findByCodeForUser(principal.getId(), orderCode));
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }

    @PostMapping(UrlConstant.Order.BASE + "/{orderCode}/cancel")
    public BaseRes cancel(@AuthenticationPrincipal SecurityUser principal, @PathVariable String orderCode,
                           @Valid @RequestBody(required = false) CancelOrderRequestDto request,
                           HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            String reason = request != null ? request.getReason() : null;
            return restSuccessHandle.handleSuccess(orderService.cancel(principal.getId(), orderCode, reason));
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }
}
