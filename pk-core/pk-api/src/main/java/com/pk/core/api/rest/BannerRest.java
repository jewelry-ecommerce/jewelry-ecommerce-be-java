package com.pk.core.api.rest;

import org.springframework.web.bind.annotation.RequestMapping;
import lombok.RequiredArgsConstructor;

import com.pk.core.business.web.AbstractRest;
import com.pk.core.common.web.BaseRes;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.pk.core.business.service.BannerService;
import com.pk.core.model.constant.UrlConstant;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Render banner theo mã vị trí (/storefront/banner/placements/code/{code}/render) - công khai (SecurityConfig). */
@RestController
@RequestMapping(UrlConstant.Common.API + UrlConstant.Common.VERSION)
@RequiredArgsConstructor
public class BannerRest extends AbstractRest {

    private final BannerService bannerService;

    @GetMapping(UrlConstant.Banner.PLACEMENTS_BY_CODE + "/{code}/render")
    public BaseRes render(@PathVariable String code, @RequestParam(required = false) String platform,
                          HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            return restSuccessHandle.handleSuccess(bannerService.renderByPlacementCode(code, platform));
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }
}
