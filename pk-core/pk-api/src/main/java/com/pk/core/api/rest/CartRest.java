package com.pk.core.api.rest;

import org.springframework.web.bind.annotation.RequestMapping;
import lombok.RequiredArgsConstructor;

import com.pk.core.business.service.CartService;
import com.pk.core.business.web.AbstractRest;
import com.pk.core.common.web.BaseRes;
import com.pk.core.identity.security.model.SecurityUser;
import com.pk.core.model.constant.UrlConstant;
import com.pk.core.model.dto.request.CartItemQuantityRequestDto;
import com.pk.core.model.dto.request.CartSyncRequestDto;
import com.pk.core.model.dto.request.CartTotalRequestDto;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Giỏ hàng (/storefront/cart/cart, theo spec FE). Công khai: khách vãng lai gửi header X-Guest-Cart-Id (lần đầu
 * chưa có thì bỏ trống, server cấp và trả guestId trong response); khách đăng nhập (Bearer) dùng giỏ theo tài khoản.
 * Riêng /merge bắt buộc đăng nhập (SecurityConfig). @AuthenticationPrincipal là null với khách vãng lai.
 */
@RestController
@RequestMapping(UrlConstant.Common.API + UrlConstant.Common.VERSION)
@RequiredArgsConstructor
public class CartRest extends AbstractRest {

    private static final String GUEST_HEADER = "X-Guest-Cart-Id";
    /** Tên header FE đang gửi (FE hiện dùng x-guest-id); chấp nhận cả hai, ưu tiên X-Guest-Cart-Id. */
    private static final String GUEST_HEADER_ALIAS = "x-guest-id";

    private final CartService cartService;

    @GetMapping(UrlConstant.Cart.BASE)
    public BaseRes get(@AuthenticationPrincipal SecurityUser principal,
                       @RequestHeader(value = GUEST_HEADER, required = false) String guestId,
                       HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            return restSuccessHandle.handleSuccess(cartService.get(userId(principal), guest(guestId, httpRequest)));
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }

    @PostMapping(UrlConstant.Cart.BASE)
    public BaseRes addOrSync(@AuthenticationPrincipal SecurityUser principal,
                             @RequestHeader(value = GUEST_HEADER, required = false) String guestId,
                             @Valid @RequestBody CartSyncRequestDto request,
                             HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            return restSuccessHandle.handleSuccess(cartService.addOrSync(userId(principal), guest(guestId, httpRequest), request));
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }

    /** Thêm nhiều SKU một lần, giữ hàng cũ. Một dòng lỗi thì không thêm dòng nào. */
    @PostMapping(UrlConstant.Cart.BATCH_ADD)
    public BaseRes batchAdd(@AuthenticationPrincipal SecurityUser principal,
                            @RequestHeader(value = GUEST_HEADER, required = false) String guestId,
                            @Valid @RequestBody CartSyncRequestDto request,
                            HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            return restSuccessHandle.handleSuccess(
                    cartService.batchAddStrict(userId(principal), guest(guestId, httpRequest), request));
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }

    /** Đặt SỐ LƯỢNG CUỐI của một dòng (quantity = 0 là xoá dòng) - dùng khi FE sửa số lượng thay vì cộng dồn như POST. */
    @PutMapping(UrlConstant.Cart.BASE)
    public BaseRes setQuantity(@AuthenticationPrincipal SecurityUser principal,
                               @RequestHeader(value = GUEST_HEADER, required = false) String guestId,
                               @Valid @RequestBody CartItemQuantityRequestDto request,
                               HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            return restSuccessHandle.handleSuccess(
                    cartService.setQuantity(userId(principal), guest(guestId, httpRequest), request));
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }

    @DeleteMapping(UrlConstant.Cart.BASE + "/{variationId}")
    public BaseRes removeItem(@AuthenticationPrincipal SecurityUser principal,
                              @RequestHeader(value = GUEST_HEADER, required = false) String guestId,
                              @PathVariable String variationId,
                              HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            return restSuccessHandle.handleSuccess(
                    cartService.removeItem(userId(principal), guest(guestId, httpRequest), variationId));
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }

    @PostMapping(UrlConstant.Cart.BASE + "/merge")
    public BaseRes merge(@AuthenticationPrincipal SecurityUser principal,
                         @RequestHeader(value = GUEST_HEADER, required = false) String guestId,
                         HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            return restSuccessHandle.handleSuccess(cartService.merge(principal.getId(), guest(guestId, httpRequest)));
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }

    @PostMapping(UrlConstant.Cart.BASE + "/calculate-total")
    public BaseRes calculateTotal(@Valid @RequestBody CartTotalRequestDto request,
                                  HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            return restSuccessHandle.handleSuccess(cartService.calculateTotal(request));
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }

    @GetMapping(UrlConstant.Cart.BASE + "/recommendation")
    public BaseRes recommendation(@AuthenticationPrincipal SecurityUser principal,
                                  @RequestHeader(value = GUEST_HEADER, required = false) String guestId,
                                  @RequestParam(defaultValue = "1") int page,
                                  @RequestParam(defaultValue = "20") int take,
                                  HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            return restSuccessHandle.handleSuccess(cartService.recommend(userId(principal), guest(guestId, httpRequest), page, take));
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }

    private static Long userId(SecurityUser principal) {
        return principal == null ? null : principal.getId();
    }

    private static String guest(String primary, HttpServletRequest request) {
        return primary != null && !primary.isBlank() ? primary : request.getHeader(GUEST_HEADER_ALIAS);
    }
}
