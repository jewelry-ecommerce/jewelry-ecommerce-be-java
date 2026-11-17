package com.pk.core.api.rest;

import com.pk.core.business.payment.MomoCheckoutService;
import com.pk.core.business.payment.MomoProperties;
import com.pk.core.business.payment.MomoReceiptService;
import com.pk.core.business.web.AbstractRest;
import com.pk.core.common.web.BaseRes;
import com.pk.core.identity.security.model.SecurityUser;
import com.pk.core.model.constant.UrlConstant;
import com.pk.core.model.dto.request.MomoStartRequestDto;
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
public class MomoRest extends AbstractRest {
    private final MomoCheckoutService checkout;
    private final MomoReceiptService receipts;
    private final MomoProperties properties;

    @PostMapping("/momo/start")
    public BaseRes start(@AuthenticationPrincipal SecurityUser user, @Valid @RequestBody MomoStartRequestDto body,
                         HttpServletRequest request, HttpServletResponse response) {
        try {
            Long userId = user == null ? null : user.getId();
            return restSuccessHandle.handleSuccess(checkout.start(userId, body));
        } catch (Exception failure) {
            return restErrorHandle.handleException(failure, request, response);
        }
    }

    @GetMapping("/momo/return")
    public Object returned(HttpServletRequest request, HttpServletResponse response) {
        try {
            String path = receipts.handleReturn(query(request));
            String target = browserTarget(request, path);
            if (target == null) return restSuccessHandle.handleSuccess(path);
            response.sendRedirect(target);
            return null;
        } catch (Exception failure) {
            return restErrorHandle.handleException(failure, request, response);
        }
    }

    @PostMapping("/momo/ipn")
    public ResponseEntity<Void> ipn(@RequestBody Map<String, Object> body) {
        receipts.handleIpn(strings(body));
        return ResponseEntity.noContent().build();
    }

    private static Map<String, String> query(HttpServletRequest request) {
        Map<String, String> params = new LinkedHashMap<>();
        request.getParameterMap().forEach((key, value) -> params.put(key, value.length == 0 ? "" : value[0]));
        return params;
    }

    private static Map<String, String> strings(Map<String, Object> body) {
        Map<String, String> params = new LinkedHashMap<>();
        body.forEach((key, value) -> params.put(key, value == null ? "" : String.valueOf(value)));
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
