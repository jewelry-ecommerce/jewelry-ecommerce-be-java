package com.pk.core.api.rest;

import com.pk.core.business.payment.MomoCheckoutService;
import com.pk.core.business.payment.MomoProperties;
import com.pk.core.business.payment.MomoReceiptService;
import com.pk.core.business.web.AbstractRest;
import com.pk.core.common.web.BaseRes;
import com.pk.core.identity.security.model.SecurityUser;
import com.pk.core.model.constant.UrlConstant;
import com.pk.core.model.dto.request.MomoNotificationDto;
import com.pk.core.model.dto.request.MomoStartRequestDto;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping(UrlConstant.Common.API + UrlConstant.Common.VERSION + UrlConstant.Payment.BASE)
public class MomoRest extends AbstractRest {
    private final MomoProperties properties;
    private final MomoCheckoutService checkout;
    private final MomoReceiptService receipts;
    @GetMapping("/methods")
    public BaseRes methods() { return restSuccessHandle.handleSuccess(Map.of("momoEnabled", properties.ready(), "sandbox", true)); }
    @PostMapping("/momo/start")
    public BaseRes start(@AuthenticationPrincipal SecurityUser user, @Valid @RequestBody MomoStartRequestDto body,
                         HttpServletRequest request, HttpServletResponse response) {
        try { return restSuccessHandle.handleSuccess(checkout.start(user == null ? null : user.getId(), body)); }
        catch (Exception failure) { return restErrorHandle.handleException(failure, request, response); }
    }
    @PostMapping("/momo/ipn")
    public Object ipn(@Valid @RequestBody MomoNotificationDto body, HttpServletRequest request, HttpServletResponse response) {
        try { receipts.receive(body); response.setStatus(204); return null; }
        catch (Exception failure) { return restErrorHandle.handleException(failure, request, response); }
    }
}
