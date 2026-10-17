package com.pk.core.api.rest;

import com.pk.core.business.service.NavigationService;
import com.pk.core.business.web.AbstractRest;
import com.pk.core.common.web.BaseRes;
import com.pk.core.model.constant.UrlConstant;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Menu điều hướng đã xuất bản - công khai, chỉ GET. */
@RestController
@RequestMapping(UrlConstant.Common.API + UrlConstant.Common.VERSION)
@RequiredArgsConstructor
public class NavigationRest extends AbstractRest {

    private final NavigationService navigationService;

    @GetMapping(UrlConstant.Cms.NAVIGATION)
    public BaseRes navigation(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            return restSuccessHandle.handleSuccess(navigationService.storefront());
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }

    /** Thanh danh mục ở trang danh sách sản phẩm: con (showOnPlp) của nút khớp categoryId/rootTargetId/rootUrl; không khớp -> các nút gốc showOnPlp. */
    @GetMapping(UrlConstant.Cms.NAVIGATION + "/plp-rail")
    public BaseRes plpRail(@RequestParam(required = false) String categoryId,
                           @RequestParam(required = false) String rootTargetId,
                           @RequestParam(required = false) String rootUrl,
                           HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            return restSuccessHandle.handleSuccess(navigationService.plpRail(categoryId, rootTargetId, rootUrl));
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }
}
