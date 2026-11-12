package com.pk.core.api.rest;

import org.springframework.web.bind.annotation.RequestMapping;
import lombok.RequiredArgsConstructor;

import com.pk.core.business.service.AiStylistService;
import com.pk.core.business.web.AbstractRest;
import com.pk.core.common.exception.BusinessException;
import com.pk.core.common.exception.ErrorCode;
import com.pk.core.common.web.BaseRes;
import com.pk.core.identity.security.model.SecurityUser;
import com.pk.core.model.constant.UrlConstant;
import com.pk.core.model.dto.request.StylistFeedbackRequestDto;
import com.pk.core.model.dto.request.StylistRequestDto;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Gợi ý stylist / set-builder công khai. Feedback và lịch sử phiên bắt buộc đăng nhập. */
@RestController
@RequestMapping(UrlConstant.Common.API + UrlConstant.Common.VERSION)
@RequiredArgsConstructor
public class AiRest extends AbstractRest {

    private final AiStylistService aiStylistService;

    @PostMapping(UrlConstant.Ai.STYLIST_RECOMMENDATIONS)
    public BaseRes recommend(@AuthenticationPrincipal SecurityUser principal,
                              @Valid @RequestBody StylistRequestDto request,
                              HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            return restSuccessHandle.handleSuccess(aiStylistService.recommend(request, userId(principal)));
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }

    @PostMapping(UrlConstant.Ai.SET_BUILDER)
    public BaseRes setBuilder(@AuthenticationPrincipal SecurityUser principal,
                               @Valid @RequestBody StylistRequestDto request,
                               HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            return restSuccessHandle.handleSuccess(aiStylistService.buildSet(request, userId(principal)));
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }

    @PostMapping(UrlConstant.Ai.STYLIST_FEEDBACK)
    public BaseRes feedback(@AuthenticationPrincipal SecurityUser principal,
                             @Valid @RequestBody StylistFeedbackRequestDto request,
                             HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            return restSuccessHandle.handleSuccess(aiStylistService.feedback(request, requireUser(principal)));
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }

    @GetMapping(UrlConstant.Ai.STYLIST_SESSIONS)
    public BaseRes sessions(@AuthenticationPrincipal SecurityUser principal,
                             HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            return restSuccessHandle.handleSuccess(aiStylistService.history(requireUser(principal)));
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }

    private static Long userId(SecurityUser principal) {
        return principal == null ? null : principal.getId();
    }

    private static Long requireUser(SecurityUser principal) {
        if (principal == null || principal.getId() == null) {
            throw BusinessException.unauthorized(ErrorCode.UNAUTHORIZED, "Cần đăng nhập");
        }
        return principal.getId();
    }
}
