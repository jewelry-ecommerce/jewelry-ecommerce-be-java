package com.pk.core.api.rest.admin;

import com.pk.core.business.service.SearchPinnedService;
import com.pk.core.business.web.AbstractRest;
import com.pk.core.common.web.BaseRes;
import com.pk.core.identity.security.model.SecurityUser;
import com.pk.core.model.constant.UrlConstant;
import com.pk.core.model.constant.admin.UrlAdminConstant;
import com.pk.core.model.dto.request.AdminSearchPinnedRequestDto;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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

/** API admin - sản phẩm ghim ở ô tìm kiếm. Quyền: nhóm /admin/catalog/**. */
@RestController
@RequestMapping(UrlConstant.Common.API + UrlConstant.Common.VERSION)
@RequiredArgsConstructor
public class AdminSearchPinnedRest extends AbstractRest {

    private final SearchPinnedService service;

    /** {total, list} theo hợp đồng FE. */
    @GetMapping(UrlAdminConstant.Catalog.SEARCH_PINNED)
    public BaseRes list(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            var list = service.list();
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("total", list.size());
            body.put("list", list);
            return restSuccessHandle.handleSuccess(body);
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }

    @PostMapping(UrlAdminConstant.Catalog.SEARCH_PINNED)
    @ResponseStatus(HttpStatus.CREATED)
    public BaseRes create(@RequestBody AdminSearchPinnedRequestDto request, @AuthenticationPrincipal SecurityUser principal,
                          HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            return restSuccessHandle.handleSuccess(service.create(request, principal == null ? null : principal.getId()));
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }

    @PatchMapping(UrlAdminConstant.Catalog.SEARCH_PINNED + "/{id}")
    public BaseRes update(@PathVariable Long id, @RequestBody AdminSearchPinnedRequestDto request,
                          @AuthenticationPrincipal SecurityUser principal,
                          HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            return restSuccessHandle.handleSuccess(service.update(id, request, principal == null ? null : principal.getId()));
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }

    @DeleteMapping(UrlAdminConstant.Catalog.SEARCH_PINNED + "/{id}")
    public BaseRes delete(@PathVariable Long id, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            service.delete(id);
            return restSuccessHandle.handleSuccess(Map.of("message", "Đã xoá sản phẩm ghim"));
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }
}
