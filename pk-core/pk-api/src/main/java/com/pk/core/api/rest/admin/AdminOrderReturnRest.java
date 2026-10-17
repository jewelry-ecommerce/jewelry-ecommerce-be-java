package com.pk.core.api.rest.admin;

import com.pk.core.business.service.OrderOmsService;
import com.pk.core.business.service.OrderReturnService;
import com.pk.core.business.web.AbstractRest;
import com.pk.core.common.web.BaseRes;
import com.pk.core.identity.security.model.SecurityUser;
import com.pk.core.model.constant.UrlConstant;
import com.pk.core.model.constant.admin.UrlAdminConstant;
import com.pk.core.model.dto.request.OmsRequestDtos;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.function.Supplier;

/**
 * API admin - yêu cầu đổi/trả hàng (FE: order/order-returns/*, order/admin/order-returns/{quote,commit}).
 * Đường dẫn Java: /admin/order-returns (list = POST .../list như FE), /admin/order-returns/{quote,commit},
 * /admin/order-returns/{id}[/approve|/reject]. Quyền ADMIN hoặc ORDER_MANAGER (SecurityConfig).
 */
@RestController
@RequestMapping(UrlConstant.Common.API + UrlConstant.Common.VERSION)
@RequiredArgsConstructor
public class AdminOrderReturnRest extends AbstractRest {

    private static final String B = UrlAdminConstant.OrderReturn.BASE;

    private final OrderReturnService service;

    private BaseRes run(Supplier<Object> action, HttpServletRequest rq, HttpServletResponse rs) {
        try {
            return restSuccessHandle.handleSuccess(action.get());
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, rq, rs);
        }
    }

    static OrderOmsService.Actor actor(SecurityUser u) {
        if (u == null) {
            return new OrderOmsService.Actor(null, null);
        }
        String role = u.getAuthorities().stream().map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_")).map(a -> a.substring(5)).findFirst().orElse(null);
        return new OrderOmsService.Actor(u.getId(), role);
    }

    @PostMapping(B + "/quote")
    public BaseRes quote(@RequestBody OmsRequestDtos.OrderReturn body, HttpServletRequest rq, HttpServletResponse rs) {
        return run(() -> service.quote(body), rq, rs);
    }

    @PostMapping(B + "/commit")
    @ResponseStatus(HttpStatus.CREATED)
    public BaseRes commit(@RequestBody OmsRequestDtos.OrderReturn body, @AuthenticationPrincipal SecurityUser u,
                          HttpServletRequest rq, HttpServletResponse rs) {
        return run(() -> service.commit(body, actor(u)), rq, rs);
    }

    @PostMapping(B)
    @ResponseStatus(HttpStatus.CREATED)
    public BaseRes create(@RequestBody OmsRequestDtos.OrderReturn body, @AuthenticationPrincipal SecurityUser u,
                          HttpServletRequest rq, HttpServletResponse rs) {
        return run(() -> service.create(body, actor(u)), rq, rs);
    }

    @PostMapping(B + "/list")
    public BaseRes list(@RequestBody(required = false) Map<String, Object> filter, HttpServletRequest rq, HttpServletResponse rs) {
        return run(() -> service.list(filter), rq, rs);
    }

    @GetMapping(B + "/{id}")
    public BaseRes detail(@PathVariable String id, HttpServletRequest rq, HttpServletResponse rs) {
        return run(() -> service.detail(id), rq, rs);
    }

    @PutMapping(B + "/{id}")
    public BaseRes update(@PathVariable String id, @RequestBody OmsRequestDtos.OrderReturn body,
                          @AuthenticationPrincipal SecurityUser u, HttpServletRequest rq, HttpServletResponse rs) {
        return run(() -> service.update(id, body, actor(u)), rq, rs);
    }

    @PutMapping(B + "/{id}/approve")
    public BaseRes approve(@PathVariable String id, @RequestBody(required = false) OmsRequestDtos.Resolve body,
                           @AuthenticationPrincipal SecurityUser u, HttpServletRequest rq, HttpServletResponse rs) {
        return run(() -> service.approve(id, body == null ? null : body.getNote(), actor(u)), rq, rs);
    }

    @PutMapping(B + "/{id}/reject")
    public BaseRes reject(@PathVariable String id, @RequestBody(required = false) OmsRequestDtos.Resolve body,
                          @AuthenticationPrincipal SecurityUser u, HttpServletRequest rq, HttpServletResponse rs) {
        return run(() -> service.reject(id, body == null ? null : body.getNote(), actor(u)), rq, rs);
    }
}
