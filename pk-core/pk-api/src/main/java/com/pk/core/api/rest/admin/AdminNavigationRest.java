package com.pk.core.api.rest.admin;

import com.pk.core.business.service.NavigationService;
import com.pk.core.business.web.AbstractRest;
import com.pk.core.common.web.BaseRes;
import com.pk.core.identity.security.model.SecurityUser;
import com.pk.core.model.constant.UrlConstant;
import com.pk.core.model.constant.admin.UrlAdminConstant;
import com.pk.core.model.dto.request.AdminNavigationReorderRequestDto;
import com.pk.core.model.dto.request.AdminNavigationRequestDto;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * API admin - menu điều hướng website (2 lớp DRAFT/PUBLISHED). Quyền: nhóm /admin/cms/**.
 * Các route cố định (publish-status, publish, cache, reorder) đứng trước {id}.
 */
@RestController
@RequestMapping(UrlConstant.Common.API + UrlConstant.Common.VERSION)
@RequiredArgsConstructor
public class AdminNavigationRest extends AbstractRest {

    private final NavigationService navigationService;

    /** Cây nháp: {items: [...]} (FE đọc response.items). */
    @GetMapping(UrlAdminConstant.Cms.NAVIGATIONS)
    public BaseRes tree(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            return restSuccessHandle.handleSuccess(Map.of("items", navigationService.tree()));
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }

    @GetMapping(UrlAdminConstant.Cms.NAVIGATIONS + "/publish-status")
    public BaseRes publishStatus(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            return restSuccessHandle.handleSuccess(Map.of("hasPendingPublish", navigationService.hasPendingPublish()));
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }

    @PostMapping(UrlAdminConstant.Cms.NAVIGATIONS + "/publish")
    public BaseRes publish(@AuthenticationPrincipal SecurityUser principal,
                           HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            boolean pending = navigationService.publish(principal == null ? null : principal.getId());
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("success", true);
            body.put("hasPendingPublish", pending);
            return restSuccessHandle.handleSuccess(body);
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }

    @DeleteMapping(UrlAdminConstant.Cms.NAVIGATIONS + "/cache")
    public BaseRes clearCache(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            return restSuccessHandle.handleSuccess(navigationService.clearCache());
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }

    @PatchMapping(UrlAdminConstant.Cms.NAVIGATIONS + "/reorder")
    public BaseRes reorder(@RequestBody AdminNavigationReorderRequestDto request,
                           HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            navigationService.reorder(request);
            return restSuccessHandle.handleSuccess(Map.of("success", true));
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }

    @PostMapping(UrlAdminConstant.Cms.NAVIGATIONS)
    @ResponseStatus(HttpStatus.CREATED)
    public BaseRes create(@Valid @RequestBody AdminNavigationRequestDto request,
                          @AuthenticationPrincipal SecurityUser principal,
                          HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            return restSuccessHandle.handleSuccess(navigationService.create(request, principal == null ? null : principal.getId()));
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }

    @GetMapping(UrlAdminConstant.Cms.NAVIGATIONS + "/{id}")
    public BaseRes detail(@PathVariable Long id, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            return restSuccessHandle.handleSuccess(navigationService.detail(id));
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }

    @PatchMapping(UrlAdminConstant.Cms.NAVIGATIONS + "/{id}")
    public BaseRes update(@PathVariable Long id, @Valid @RequestBody AdminNavigationRequestDto request,
                          @AuthenticationPrincipal SecurityUser principal,
                          HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            return restSuccessHandle.handleSuccess(navigationService.update(id, request, principal == null ? null : principal.getId()));
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }

    @PatchMapping(UrlAdminConstant.Cms.NAVIGATIONS + "/{id}/archive")
    public BaseRes archive(@PathVariable Long id, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            navigationService.archive(id);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("success", true);
            body.put("id", String.valueOf(id));
            return restSuccessHandle.handleSuccess(body);
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }
}
