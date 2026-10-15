package com.pk.core.api.rest.admin;

import com.pk.core.business.service.SiteLayoutService;
import com.pk.core.business.web.AbstractRest;
import com.pk.core.common.web.BaseRes;
import com.pk.core.model.constant.UrlConstant;
import com.pk.core.model.constant.admin.UrlAdminConstant;
import com.pk.core.model.dto.request.AdminSiteFooterRequestDto;
import com.pk.core.model.dto.request.AdminSiteHeaderRequestDto;
import com.pk.core.model.dto.request.AdminSiteLogoRequestDto;
import com.pk.core.model.dto.request.AdminSiteStatusRequestDto;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** API admin - header (thanh thông báo), footer, logo của site (V26). Quyền: ADMIN, CATALOG_MANAGER (nhóm /admin/cms/**). */
@RestController
@RequestMapping(UrlConstant.Common.API + UrlConstant.Common.VERSION)
@RequiredArgsConstructor
public class AdminSiteLayoutRest extends AbstractRest {

    private final SiteLayoutService siteLayout;

    // ---------------- header ----------------

    @GetMapping(UrlAdminConstant.Cms.HEADERS)
    public BaseRes listHeaders(@RequestParam(required = false) String search,
                               @RequestParam(required = false) String status,
                               @RequestParam(required = false, defaultValue = "1") int page,
                               @RequestParam(required = false, defaultValue = "20") int take,
                               HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            return restSuccessHandle.handleSuccess(siteLayout.listHeaders(search, status, page, take));
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }

    @GetMapping(UrlAdminConstant.Cms.HEADERS + "/{id}")
    public BaseRes headerDetail(@PathVariable Long id, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            return restSuccessHandle.handleSuccess(siteLayout.headerDetail(id));
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }

    @PostMapping(UrlAdminConstant.Cms.HEADERS)
    @ResponseStatus(HttpStatus.CREATED)
    public BaseRes createHeader(@Valid @RequestBody AdminSiteHeaderRequestDto request,
                                HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            return restSuccessHandle.handleSuccess(siteLayout.createHeader(request));
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }

    @PatchMapping(UrlAdminConstant.Cms.HEADERS + "/{id}")
    public BaseRes updateHeader(@PathVariable Long id, @Valid @RequestBody AdminSiteHeaderRequestDto request,
                                HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            return restSuccessHandle.handleSuccess(siteLayout.updateHeader(id, request));
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }

    @PatchMapping(UrlAdminConstant.Cms.HEADERS + "/{id}/status")
    public BaseRes updateHeaderStatus(@PathVariable Long id, @Valid @RequestBody AdminSiteStatusRequestDto request,
                                      HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            return restSuccessHandle.handleSuccess(siteLayout.updateHeaderStatus(id, request));
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }

    @DeleteMapping(UrlAdminConstant.Cms.HEADERS + "/{id}")
    public BaseRes deleteHeader(@PathVariable Long id, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            siteLayout.deleteHeader(id);
            return restSuccessHandle.handleSuccess(java.util.Map.of("success", true, "id", id));
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }

    // ---------------- footer ----------------

    @GetMapping(UrlAdminConstant.Cms.FOOTER)
    public BaseRes footer(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            return restSuccessHandle.handleSuccess(siteLayout.footer());
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }

    @PutMapping(UrlAdminConstant.Cms.FOOTER)
    public BaseRes saveFooter(@Valid @RequestBody AdminSiteFooterRequestDto request,
                              HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            return restSuccessHandle.handleSuccess(siteLayout.saveFooter(request));
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }

    // ---------------- logo ----------------

    @GetMapping(UrlAdminConstant.Cms.LOGOS)
    public BaseRes listLogos(@RequestParam(required = false) String search,
                             @RequestParam(required = false) String status,
                             @RequestParam(required = false) String type,
                             @RequestParam(required = false, defaultValue = "1") int page,
                             @RequestParam(required = false, defaultValue = "20") int take,
                             HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            return restSuccessHandle.handleSuccess(siteLayout.listLogos(search, status, type, page, take));
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }

    @GetMapping(UrlAdminConstant.Cms.LOGOS + "/{id}")
    public BaseRes logoDetail(@PathVariable Long id, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            return restSuccessHandle.handleSuccess(siteLayout.logoDetail(id));
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }

    @PostMapping(UrlAdminConstant.Cms.LOGOS)
    @ResponseStatus(HttpStatus.CREATED)
    public BaseRes createLogo(@Valid @RequestBody AdminSiteLogoRequestDto request,
                              HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            return restSuccessHandle.handleSuccess(siteLayout.createLogo(request));
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }

    @PatchMapping(UrlAdminConstant.Cms.LOGOS + "/{id}")
    public BaseRes updateLogo(@PathVariable Long id, @Valid @RequestBody AdminSiteLogoRequestDto request,
                              HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            return restSuccessHandle.handleSuccess(siteLayout.updateLogo(id, request));
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }

    @PatchMapping(UrlAdminConstant.Cms.LOGOS + "/{id}/status")
    public BaseRes updateLogoStatus(@PathVariable Long id, @Valid @RequestBody AdminSiteStatusRequestDto request,
                                    HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            return restSuccessHandle.handleSuccess(siteLayout.updateLogoStatus(id, request));
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }

    @PatchMapping(UrlAdminConstant.Cms.LOGOS + "/{id}/archive")
    public BaseRes archiveLogo(@PathVariable Long id, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            siteLayout.archiveLogo(id);
            return restSuccessHandle.handleSuccess(java.util.Map.of("success", true, "id", id));
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }
}
