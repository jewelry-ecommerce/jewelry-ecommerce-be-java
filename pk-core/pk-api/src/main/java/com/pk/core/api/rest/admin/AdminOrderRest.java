package com.pk.core.api.rest.admin;

import org.springframework.web.bind.annotation.RequestMapping;
import com.pk.core.model.constant.UrlConstant;
import lombok.RequiredArgsConstructor;

import com.pk.core.business.service.AdminOrderQuery;
import com.pk.core.business.service.OrderService;
import com.pk.core.business.web.AbstractRest;
import com.pk.core.common.web.BaseRes;
import com.pk.core.model.constant.admin.UrlAdminConstant;
import com.pk.core.model.dto.request.AdminOrderRefundRequestDto;
import com.pk.core.model.dto.request.AdminOrderReturnRequestDto;
import com.pk.core.model.dto.request.AdminOrderStatusRequestDto;
import com.pk.core.model.dto.request.CancelOrderRequestDto;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * API admin - Đơn hàng/OMS (document/09-tong-hop-api-fe.md mục L). Gói api.rest.admin (pk-api),
 * TÁCH RIÊNG khỏi Rest storefront (OrderRest, gói api.rest) - xem claude/RULE-CODE.md. Mọi route ở
 * đây đã bị SecurityConfig (pk-identity) bắt buộc hasRole("ADMIN") (rule chung cho cả
 * UrlAdminConstant.Common.BASE + "/**", trừ /admin/auth/login).
 *
 * <p>KHÔNG lọc theo userId (khác OrderRest storefront) - admin được xem/sửa mọi đơn, không có khái
 * niệm IDOR ở đây (đã có hasRole("ADMIN") chặn từ SecurityConfig).</p>
 */
@RestController
@RequestMapping(UrlConstant.Common.API + UrlConstant.Common.VERSION)
@RequiredArgsConstructor
public class AdminOrderRest extends AbstractRest {

    private final OrderService orderService;

    @GetMapping(UrlAdminConstant.Order.BASE)
    public BaseRes list(@RequestParam(required = false) String search,
                         @RequestParam(required = false) String status,
                         @RequestParam(required = false) String paymentStatus,
                         @RequestParam(required = false) String fromDate,
                         @RequestParam(required = false) String toDate,
                         @RequestParam(required = false) String dateField,
                         @RequestParam(required = false) String skuCode,
                         @RequestParam(required = false) String shippingMethod,
                         @RequestParam(required = false) String provinceName,
                         @RequestParam(required = false) String source,
                         @RequestParam(required = false) String orderBy,
                         @RequestParam(required = false) String orderType,
                         @RequestParam(required = false, defaultValue = "1") int page,
                         @RequestParam(required = false, defaultValue = "20") int take,
                         HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            return restSuccessHandle.handleSuccess(orderService.searchForAdmin(new AdminOrderQuery(search, status,
                    paymentStatus, fromDate, toDate, dateField, skuCode, shippingMethod, provinceName, source,
                    orderBy, orderType, page, take)));
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }

    /** Số đơn theo từng trạng thái, cùng bộ lọc với danh sách (trừ status/page/take) - thay cho N request đếm. */
    @GetMapping(UrlAdminConstant.Order.BASE + "/counts")
    public BaseRes counts(@RequestParam(required = false) String search,
                           @RequestParam(required = false) String paymentStatus,
                           @RequestParam(required = false) String fromDate,
                           @RequestParam(required = false) String toDate,
                           @RequestParam(required = false) String dateField,
                           @RequestParam(required = false) String skuCode,
                           @RequestParam(required = false) String shippingMethod,
                           @RequestParam(required = false) String provinceName,
                           @RequestParam(required = false) String source,
                           HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            return restSuccessHandle.handleSuccess(orderService.countsForAdmin(new AdminOrderQuery(search, null,
                    paymentStatus, fromDate, toDate, dateField, skuCode, shippingMethod, provinceName, source,
                    null, null, 1, 20)));
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }

    @GetMapping(UrlAdminConstant.Order.BASE + "/{orderCode}")
    public BaseRes detail(@PathVariable String orderCode,
                           HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            return restSuccessHandle.handleSuccess(orderService.findByCodeForAdmin(orderCode));
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }

    @PatchMapping(UrlAdminConstant.Order.BASE + "/{orderCode}/status")
    public BaseRes updateStatus(@PathVariable String orderCode,
                                 @Valid @RequestBody AdminOrderStatusRequestDto request,
                                 HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            return restSuccessHandle.handleSuccess(orderService.updateStatus(orderCode, request));
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }

    @PostMapping(UrlAdminConstant.Order.BASE + "/{orderCode}/cancel")
    public BaseRes cancel(@PathVariable String orderCode,
                           @Valid @RequestBody(required = false) CancelOrderRequestDto request,
                           HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            String reason = request != null ? request.getReason() : null;
            return restSuccessHandle.handleSuccess(orderService.cancelByAdmin(orderCode, reason));
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }

    @PostMapping(UrlAdminConstant.Order.BASE + "/{orderCode}/return")
    public BaseRes returnOrder(@PathVariable String orderCode,
                                @Valid @RequestBody AdminOrderReturnRequestDto request,
                                HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            return restSuccessHandle.handleSuccess(orderService.returnOrder(orderCode, request));
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }

    @PostMapping(UrlAdminConstant.Order.BASE + "/{orderCode}/refund")
    public BaseRes refund(@PathVariable String orderCode,
                           @Valid @RequestBody AdminOrderRefundRequestDto request,
                           HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            return restSuccessHandle.handleSuccess(
                    orderService.refund(orderCode, request.getAmount(), request.getReason()));
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }
}
