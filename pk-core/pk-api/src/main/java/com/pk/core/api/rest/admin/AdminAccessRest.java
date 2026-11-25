package com.pk.core.api.rest.admin;

import org.springframework.web.bind.annotation.RequestMapping;
import com.pk.core.model.constant.UrlConstant;
import lombok.RequiredArgsConstructor;

import com.pk.core.business.service.AdminAccessService;
import com.pk.core.business.web.AbstractRest;
import com.pk.core.common.web.BaseRes;
import com.pk.core.identity.security.model.SecurityUser;
import com.pk.core.identity.service.AdminUserService;
import com.pk.core.model.constant.admin.UrlAdminConstant;
import com.pk.core.model.dto.request.AdminUserCreateRequestDto;
import com.pk.core.model.dto.request.AdminUserPagePermissionsRequestDto;
import com.pk.core.model.dto.request.AdminUserRolesRequestDto;
import com.pk.core.model.dto.request.AdminUserUpdateRequestDto;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** API admin - tài khoản, vai trò và quyền. GET /admin/users và GET /admin/roles theo spec FE; phần tạo/sửa/khoá tài
 * khoản và gán vai trò là mở rộng ngoài spec (chỉ role ADMIN gọi được - xem SecurityConfig). */
@RestController
@RequestMapping(UrlConstant.Common.API + UrlConstant.Common.VERSION)
@RequiredArgsConstructor
public class AdminAccessRest extends AbstractRest {

    private final AdminAccessService accessService;
    private final AdminUserService adminUserService;

    @GetMapping(UrlAdminConstant.Access.USERS)
    public BaseRes users(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            return restSuccessHandle.handleSuccess(accessService.listUsers());
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }

    @GetMapping(UrlAdminConstant.Access.ROLES)
    public BaseRes roles(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            return restSuccessHandle.handleSuccess(adminUserService.listRoles());
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }

    @PostMapping(UrlAdminConstant.Access.USERS)
    public BaseRes createUser(@Valid @RequestBody AdminUserCreateRequestDto request,
                               HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            return restSuccessHandle.handleSuccess(adminUserService.create(request));
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }

    @GetMapping(UrlAdminConstant.Access.USERS + "/{id}")
    public BaseRes userDetail(@PathVariable Long id, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            return restSuccessHandle.handleSuccess(adminUserService.findById(id));
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }

    @PatchMapping(UrlAdminConstant.Access.USERS + "/{id}")
    public BaseRes updateUser(@AuthenticationPrincipal SecurityUser principal, @PathVariable Long id,
                               @Valid @RequestBody AdminUserUpdateRequestDto request,
                               HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            return restSuccessHandle.handleSuccess(adminUserService.update(principal.getId(), id, request));
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }

    @PutMapping(UrlAdminConstant.Access.USERS + "/{id}/roles")
    public BaseRes assignRoles(@AuthenticationPrincipal SecurityUser principal, @PathVariable Long id,
                                @Valid @RequestBody AdminUserRolesRequestDto request,
                                HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            return restSuccessHandle.handleSuccess(adminUserService.assignRoles(principal.getId(), id, request));
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }

    @GetMapping(UrlAdminConstant.Access.PERMISSION_PAGES)
    public BaseRes permissionPages(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            return restSuccessHandle.handleSuccess(accessService.listPermissionPages());
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }

    @GetMapping(UrlAdminConstant.Access.USERS + "/{id}/page-permissions")
    public BaseRes getPagePermissions(@PathVariable Long id,
                                       HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            return restSuccessHandle.handleSuccess(accessService.getUserPagePermissions(id));
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }

    @PutMapping(UrlAdminConstant.Access.USERS + "/{id}/page-permissions")
    public BaseRes putPagePermissions(@AuthenticationPrincipal SecurityUser principal, @PathVariable Long id,
                                       @Valid @RequestBody AdminUserPagePermissionsRequestDto request,
                                       HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            return restSuccessHandle.handleSuccess(
                    accessService.replaceUserPagePermissions(principal.getId(), id, request));
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }
}
