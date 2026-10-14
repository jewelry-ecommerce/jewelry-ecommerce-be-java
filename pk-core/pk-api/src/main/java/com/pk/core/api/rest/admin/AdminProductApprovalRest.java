package com.pk.core.api.rest.admin;

import com.pk.core.business.service.ProductApprovalService;
import com.pk.core.business.web.AbstractRest;
import com.pk.core.common.web.BaseRes;
import com.pk.core.identity.security.model.SecurityUser;
import com.pk.core.model.constant.UrlConstant;
import com.pk.core.model.constant.admin.UrlAdminConstant;
import com.pk.core.model.dto.request.AdminProductApprovalReviewRequestDto;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Quy trình duyệt sản phẩm (V23): gửi duyệt, danh sách, duyệt/từ chối hàng loạt, huỷ. */
@RestController
@RequestMapping(UrlConstant.Common.API + UrlConstant.Common.VERSION)
@RequiredArgsConstructor
public class AdminProductApprovalRest extends AbstractRest {

    private final ProductApprovalService approvalService;

    @PostMapping(UrlAdminConstant.Catalog.PRODUCTS + "/{productId}/submit")
    public BaseRes submit(@PathVariable Long productId, @AuthenticationPrincipal SecurityUser principal,
                          HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            return restSuccessHandle.handleSuccess(approvalService.submit(productId, principal == null ? null : principal.getId()));
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }

    @GetMapping(UrlAdminConstant.Catalog.PRODUCT_APPROVALS)
    public BaseRes list(@RequestParam(required = false) String status, @RequestParam(required = false) String search,
                        @RequestParam(required = false, defaultValue = "1") int page,
                        @RequestParam(required = false, defaultValue = "20") int take,
                        HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            return restSuccessHandle.handleSuccess(approvalService.list(status, search, page, take));
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }

    @PostMapping(UrlAdminConstant.Catalog.PRODUCT_APPROVALS + "/review")
    public BaseRes review(@Valid @RequestBody AdminProductApprovalReviewRequestDto request,
                          @AuthenticationPrincipal SecurityUser principal,
                          HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            return restSuccessHandle.handleSuccess(approvalService.review(request, principal == null ? null : principal.getId()));
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }

    @PostMapping(UrlAdminConstant.Catalog.PRODUCT_APPROVALS + "/{requestId}/cancel")
    public BaseRes cancel(@PathVariable Long requestId,
                          HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            return restSuccessHandle.handleSuccess(approvalService.cancel(requestId));
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }
}
