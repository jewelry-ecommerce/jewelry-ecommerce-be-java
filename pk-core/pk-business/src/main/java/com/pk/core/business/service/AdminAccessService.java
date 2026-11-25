package com.pk.core.business.service;

import com.pk.core.model.dto.request.AdminUserPagePermissionsRequestDto;
import com.pk.core.model.dto.response.AdminUserResponseDto;
import com.pk.core.model.dto.response.PagePermissionItemDto;
import com.pk.core.model.dto.response.PermissionPageDto;
import com.pk.core.model.dto.response.RoleResponseDto;

import java.util.List;

/** Tài khoản và phân quyền trang theo user (ROOT quản lý grant). */
public interface AdminAccessService {

    List<AdminUserResponseDto> listUsers();

    List<RoleResponseDto> listRoles();

    List<PermissionPageDto> listPermissionPages();

    List<PagePermissionItemDto> getUserPagePermissions(Long userId);

    List<PagePermissionItemDto> replaceUserPagePermissions(Long actorId, Long userId,
                                                            AdminUserPagePermissionsRequestDto request);
}
