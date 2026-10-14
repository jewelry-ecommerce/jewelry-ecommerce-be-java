package com.pk.core.api.rest;

import org.springframework.web.bind.annotation.RequestMapping;
import lombok.RequiredArgsConstructor;

import com.pk.core.business.web.AbstractRest;
import com.pk.core.common.web.BaseRes;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.pk.core.business.service.CmsService;
import com.pk.core.model.constant.UrlConstant;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** CMS storefront (/storefront/cms/pages/{slug}) - công khai, chỉ trang PUBLISHED (xem SecurityConfig). */
@RestController
@RequestMapping(UrlConstant.Common.API + UrlConstant.Common.VERSION)
@RequiredArgsConstructor
public class CmsRest extends AbstractRest {

    private final CmsService cmsService;

    @GetMapping(UrlConstant.Cms.PAGES + "/{slug}")
    public BaseRes bySlug(@PathVariable String slug, @RequestParam(required = false) String device,
                          HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            return restSuccessHandle.handleSuccess(cmsService.findPublishedBySlug(slug, device));
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }
}
