package com.pk.core.api.rest;

import com.pk.core.business.service.SiteLayoutService;
import com.pk.core.business.web.AbstractRest;
import com.pk.core.common.web.BaseRes;
import com.pk.core.model.constant.UrlConstant;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Storefront: header đang hiển thị + footer + logo ACTIVE theo loại (công khai, chỉ GET). */
@RestController
@RequestMapping(UrlConstant.Common.API + UrlConstant.Common.VERSION)
@RequiredArgsConstructor
public class SiteLayoutRest extends AbstractRest {

    private final SiteLayoutService siteLayout;

    @GetMapping(UrlConstant.Cms.SITE_LAYOUT)
    public BaseRes siteLayout(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            return restSuccessHandle.handleSuccess(siteLayout.publicLayout());
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }
}
