package com.pk.core.api.rest;

import com.pk.core.business.payment.VnpayCheckoutService;
import com.pk.core.business.payment.VnpayDemoService;
import com.pk.core.business.payment.VnpayQuery;
import com.pk.core.business.payment.VnpayReceiptService;
import com.pk.core.business.web.AbstractRest;
import com.pk.core.common.web.BaseRes;
import com.pk.core.identity.security.model.SecurityUser;
import com.pk.core.model.constant.UrlConstant;
import com.pk.core.model.dto.request.VnpayDemoConfirmRequestDto;
import com.pk.core.model.dto.request.VnpayStartRequestDto;
import com.pk.core.model.dto.response.VnpayIpnResponse;
import com.pk.core.model.dto.response.VnpayReturnDto;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;

@RestController
@RequiredArgsConstructor
@RequestMapping(UrlConstant.Common.API + UrlConstant.Common.VERSION + UrlConstant.Payment.BASE)
public class VnpayRest extends AbstractRest {
    private final VnpayCheckoutService checkout;
    private final VnpayReceiptService receipts;
    private final VnpayDemoService demo;

    @Value("${app.cors.allowed-origins:}")
    private String origins;

    @PostMapping("/vnpay/start")
    public BaseRes start(@AuthenticationPrincipal SecurityUser user, @Valid @RequestBody VnpayStartRequestDto body,
                         HttpServletRequest request, HttpServletResponse response) {
        try {
            Long userId = user == null ? null : user.getId();
            return restSuccessHandle.handleSuccess(checkout.start(userId, body, VnpayQuery.clientIp(request)));
        } catch (Exception failure) {
            return restErrorHandle.handleException(failure, request, response);
        }
    }

    @PostMapping("/vnpay/demo/confirm")
    public BaseRes confirmDemo(@Valid @RequestBody VnpayDemoConfirmRequestDto body,
                               HttpServletRequest request, HttpServletResponse response) {
        try {
            return restSuccessHandle.handleSuccess(demo.confirm(body));
        } catch (Exception failure) {
            return restErrorHandle.handleException(failure, request, response);
        }
    }

    @GetMapping("/vnpay/return")
    public Object returned(HttpServletRequest request, HttpServletResponse response) {
        try {
            VnpayReturnDto result = receipts.handleReturn(VnpayQuery.from(request));
            String target = browserTarget(request, result);
            if (target == null) return restSuccessHandle.handleSuccess(result);
            response.sendRedirect(target);
            return null;
        } catch (Exception failure) {
            return restErrorHandle.handleException(failure, request, response);
        }
    }

    @GetMapping("/vnpay/ipn")
    public VnpayIpnResponse ipn(HttpServletRequest request) {
        return receipts.handleIpn(VnpayQuery.from(request));
    }

    private String browserTarget(HttpServletRequest request, VnpayReturnDto result) {
        String accept = request.getHeader("Accept");
        if (accept == null || !accept.toLowerCase(Locale.ROOT).startsWith("text/html") || result.redirect() == null) return null;
        String origin = firstOrigin();
        return origin == null ? null : origin + result.redirect();
    }

    private String firstOrigin() {
        if (origins == null || origins.isBlank()) return null;
        String origin = origins.split(",")[0].trim();
        boolean local = origin.startsWith("http://localhost") || origin.startsWith("http://127.0.0.1");
        if (!origin.startsWith("https://") && !local) return null;
        return origin.endsWith("/") ? origin.substring(0, origin.length() - 1) : origin;
    }
}
