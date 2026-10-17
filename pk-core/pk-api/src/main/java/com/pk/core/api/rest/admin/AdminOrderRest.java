package com.pk.core.api.rest.admin;

import org.springframework.web.bind.annotation.RequestMapping;
import com.pk.core.model.constant.UrlConstant;
import lombok.RequiredArgsConstructor;

import com.pk.core.business.service.AdminOrderQuery;
import com.pk.core.business.service.OrderOmsService;
import com.pk.core.business.service.OrderService;
import com.pk.core.business.web.AbstractRest;
import com.pk.core.common.web.BaseRes;
import com.pk.core.model.constant.admin.UrlAdminConstant;
import com.pk.core.model.dto.request.AdminOrderRefundRequestDto;
import com.pk.core.model.dto.request.AdminOrderReturnRequestDto;
import com.pk.core.model.dto.request.AdminOrderStatusRequestDto;
import com.pk.core.model.dto.request.CancelOrderRequestDto;

import com.pk.core.identity.security.model.SecurityUser;
import com.pk.core.model.dto.request.OmsRequestDtos;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
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
    private final OrderOmsService oms;

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
                         @RequestParam(required = false) Long createdById,
                         @RequestParam(required = false, defaultValue = "1") int page,
                         @RequestParam(required = false, defaultValue = "20") int take,
                         HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            return restSuccessHandle.handleSuccess(orderService.searchForAdmin(new AdminOrderQuery(search, status,
                    paymentStatus, fromDate, toDate, dateField, skuCode, shippingMethod, provinceName, source,
                    orderBy, orderType, page, take, createdById)));
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
                           @RequestParam(required = false) Long createdById,
                           HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            return restSuccessHandle.handleSuccess(orderService.countsForAdmin(new AdminOrderQuery(search, null,
                    paymentStatus, fromDate, toDate, dateField, skuCode, shippingMethod, provinceName, source,
                    null, null, 1, 20, createdById)));
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
                                 @AuthenticationPrincipal SecurityUser user,
                                 HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            return restSuccessHandle.handleSuccess(oms.updateStatus(orderCode, request, AdminOrderReturnRest.actor(user)));
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }

    @PostMapping(UrlAdminConstant.Order.BASE + "/{orderCode}/cancel")
    public BaseRes cancel(@PathVariable String orderCode,
                           @Valid @RequestBody(required = false) CancelOrderRequestDto request,
                           @AuthenticationPrincipal SecurityUser user,
                           HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            String reason = request != null ? request.getReason() : null;
            return restSuccessHandle.handleSuccess(oms.cancel(orderCode, reason, AdminOrderReturnRest.actor(user)));
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }

    @PostMapping(UrlAdminConstant.Order.BASE + "/{orderCode}/return")
    public BaseRes returnOrder(@PathVariable String orderCode,
                                @Valid @RequestBody AdminOrderReturnRequestDto request,
                                @AuthenticationPrincipal SecurityUser user,
                                HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            return restSuccessHandle.handleSuccess(oms.returnLines(orderCode, request, AdminOrderReturnRest.actor(user)));
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }

    @PostMapping(UrlAdminConstant.Order.BASE + "/{orderCode}/refund")
    public BaseRes refund(@PathVariable String orderCode,
                           @Valid @RequestBody AdminOrderRefundRequestDto request,
                           @AuthenticationPrincipal SecurityUser user,
                           HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            return restSuccessHandle.handleSuccess(
                    oms.refund(orderCode, request.getAmount(), request.getReason(), AdminOrderReturnRest.actor(user)));
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }

    // ---------------------------------------------------------------- OMS nâng cao (FE order/orders/*)

    /** FE: GET order/orders/audit-logs?entityName&entityId&page&take. */
    @GetMapping(UrlAdminConstant.Order.BASE + "/audit-logs")
    public BaseRes auditLogs(@RequestParam(required = false) String entityName, @RequestParam String entityId,
                             @RequestParam(required = false, defaultValue = "1") int page,
                             @RequestParam(required = false, defaultValue = "20") int take,
                             HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            return restSuccessHandle.handleSuccess(oms.auditLogs(entityName, entityId, page, take));
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }

    /** FE: POST order/orders/pricing-context/quote - báo giá tạm tính (không giữ tồn). */
    @PostMapping(UrlAdminConstant.Order.BASE + "/pricing-context/quote")
    public BaseRes quote(@RequestBody OmsRequestDtos.OrderUpsert request,
                         HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            return restSuccessHandle.handleSuccess(oms.quote(request));
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }

    /** FE: POST order/orders - nhân viên tạo đơn hộ khách (COD). */
    @PostMapping(UrlAdminConstant.Order.BASE)
    @ResponseStatus(HttpStatus.CREATED)
    public BaseRes create(@RequestBody OmsRequestDtos.OrderUpsert request, @AuthenticationPrincipal SecurityUser user,
                          HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            return restSuccessHandle.handleSuccess(oms.create(request, AdminOrderReturnRest.actor(user)));
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }

    /** FE: POST order/orders/duplicate. */
    @PostMapping(UrlAdminConstant.Order.BASE + "/duplicate")
    @ResponseStatus(HttpStatus.CREATED)
    public BaseRes duplicate(@RequestBody OmsRequestDtos.Duplicate request, @AuthenticationPrincipal SecurityUser user,
                             HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            return restSuccessHandle.handleSuccess(oms.duplicate(request, AdminOrderReturnRest.actor(user)));
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }

    /** FE: PUT order/orders/{id} - sửa đơn PENDING/CONFIRMED. */
    @PutMapping(UrlAdminConstant.Order.BASE + "/{orderCode}")
    public BaseRes update(@PathVariable String orderCode, @RequestBody OmsRequestDtos.OrderUpsert request,
                          @AuthenticationPrincipal SecurityUser user,
                          HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            return restSuccessHandle.handleSuccess(oms.update(orderCode, request, AdminOrderReturnRest.actor(user)));
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }

    /** FE: PUT order/orders/{id}/confirm-payment. */
    @PutMapping(UrlAdminConstant.Order.BASE + "/{orderCode}/confirm-payment")
    public BaseRes confirmPayment(@PathVariable String orderCode,
                                  @RequestBody(required = false) OmsRequestDtos.ConfirmPayment request,
                                  @AuthenticationPrincipal SecurityUser user,
                                  HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            return restSuccessHandle.handleSuccess(oms.confirmPayment(orderCode,
                    request == null ? null : request.getNote(), AdminOrderReturnRest.actor(user)));
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }

    /** FE: GET order/orders/{id}/notes?type=order|internal. */
    @GetMapping(UrlAdminConstant.Order.BASE + "/{orderCode}/notes")
    public BaseRes notes(@PathVariable String orderCode, @RequestParam(required = false) String type,
                         HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            return restSuccessHandle.handleSuccess(oms.notes(orderCode, type));
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }

    /** FE: POST order/orders/{id}/notes - chỉ thêm, không sửa/xoá. */
    @PostMapping(UrlAdminConstant.Order.BASE + "/{orderCode}/notes")
    @ResponseStatus(HttpStatus.CREATED)
    public BaseRes addNote(@PathVariable String orderCode, @RequestBody OmsRequestDtos.Note request,
                           @AuthenticationPrincipal SecurityUser user,
                           HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            return restSuccessHandle.handleSuccess(oms.addNote(orderCode, request, AdminOrderReturnRest.actor(user)));
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }
}
