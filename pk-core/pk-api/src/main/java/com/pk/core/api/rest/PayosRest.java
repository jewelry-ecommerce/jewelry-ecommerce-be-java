package com.pk.core.api.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.pk.core.business.payment.PayosCheckoutService;
import com.pk.core.business.payment.PayosProperties;
import com.pk.core.business.payment.PayosReceiptService;
import com.pk.core.business.web.AbstractRest;
import com.pk.core.common.web.BaseRes;
import com.pk.core.identity.security.model.SecurityUser;
import com.pk.core.model.constant.UrlConstant;
import com.pk.core.model.dto.request.PayosStartRequestDto;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping(UrlConstant.Common.API + UrlConstant.Common.VERSION + UrlConstant.Payment.BASE)
public class PayosRest extends AbstractRest {
    private final PayosCheckoutService checkout;
    private final PayosReceiptService receipts;
    private final PayosProperties properties;

    @PostMapping("/payos/start")
    public BaseRes start(@AuthenticationPrincipal SecurityUser user, @Valid @RequestBody PayosStartRequestDto body,
                         HttpServletRequest request, HttpServletResponse response) {
        try {
            Long userId = user == null ? null : user.getId();
            return restSuccessHandle.handleSuccess(checkout.start(userId, body));
        } catch (Exception failure) {
            return restErrorHandle.handleException(failure, request, response);
        }
    }

    @GetMapping("/payos/return")
    public Object returned(HttpServletRequest request, HttpServletResponse response) {
        try {
            return browserOrJson(request, response, receipts.handleReturn(query(request)));
        } catch (Exception failure) {
            return restErrorHandle.handleException(failure, request, response);
        }
    }

    @GetMapping("/payos/cancel")
    public Object cancelled(HttpServletRequest request, HttpServletResponse response) {
        try {
            return browserOrJson(request, response, receipts.handleCancel(query(request)));
        } catch (Exception failure) {
            return restErrorHandle.handleException(failure, request, response);
        }
    }

    @PostMapping("/payos/webhook")
    public ResponseEntity<Void> webhook(@RequestBody JsonNode body) {
        receipts.handleWebhook(body);
        return ResponseEntity.noContent().build();
    }

    private Object browserOrJson(HttpServletRequest request, HttpServletResponse response, String path) throws Exception {
        String target = browserTarget(request, path);
        if (target == null) return restSuccessHandle.handleSuccess(path);
        response.sendRedirect(target);
        return null;
    }

    private static Map<String, String> query(HttpServletRequest request) {
        Map<String, String> params = new LinkedHashMap<>();
        request.getParameterMap().forEach((key, value) -> params.put(key, value.length == 0 ? "" : value[0]));
        return params;
    }

    private String browserTarget(HttpServletRequest request, String path) {
        String accept = request.getHeader("Accept");
        if (accept != null && !accept.toLowerCase(Locale.ROOT).contains("text/html")) return null;
        String origin = storefront();
        return origin == null ? null : origin + path;
    }

    private String storefront() {
        String origin = properties.getClientBaseUrl();
        if (origin == null) return null;
        String trimmed = origin.endsWith("/") ? origin.substring(0, origin.length() - 1) : origin;
        boolean local = trimmed.startsWith("http://localhost") || trimmed.startsWith("http://127.0.0.1");
        if (!trimmed.startsWith("https://") && !local) return null;
        return trimmed;
    }
}
