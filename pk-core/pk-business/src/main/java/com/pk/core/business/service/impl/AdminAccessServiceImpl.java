package com.pk.core.business.service.impl;

import com.pk.core.business.repository.RoleRepo;
import com.pk.core.business.repository.UserPagePermissionRepo;
import com.pk.core.business.repository.UserRepo;
import com.pk.core.business.service.AdminAccessService;
import com.pk.core.common.exception.BusinessException;
import com.pk.core.common.exception.ErrorCode;
import com.pk.core.common.exception.ResourceNotFoundException;
import com.pk.core.model.constant.PermissionPageCatalog;
import com.pk.core.model.dto.request.AdminUserPagePermissionsRequestDto;
import com.pk.core.model.dto.response.AdminUserResponseDto;
import com.pk.core.model.dto.response.PagePermissionItemDto;
import com.pk.core.model.dto.response.PermissionPageDto;
import com.pk.core.model.dto.response.RoleResponseDto;
import com.pk.core.model.entity.RoleEntity;
import com.pk.core.model.entity.UserEntity;
import com.pk.core.model.entity.UserPagePermissionEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AdminAccessServiceImpl implements AdminAccessService {

    private final UserRepo users;
    private final RoleRepo roles;
    private final UserPagePermissionRepo pagePermissions;

    @Transactional(readOnly = true)
    @Override
    public List<AdminUserResponseDto> listUsers() {
        List<UserEntity> all = new ArrayList<>();
        for (UserEntity u : users.findAll(Sort.unsorted())) {
            if (u.getDeletedDate() == null) {
                all.add(u);
            }
        }
        all.sort(Comparator.comparing(UserEntity::getId));
        List<AdminUserResponseDto> result = new ArrayList<>();
        for (UserEntity u : all) {
            List<String> roleNames = roles.findByUserId(u.getId()).stream().map(RoleEntity::getName).sorted().toList();
            result.add(AdminUserResponseDto.from(u, roleNames));
        }
        return result;
    }

    @Transactional(readOnly = true)
    @Override
    public List<RoleResponseDto> listRoles() {
        List<RoleEntity> all = new ArrayList<>();
        for (RoleEntity r : roles.findAll(Sort.unsorted())) {
            all.add(r);
        }
        all.sort(Comparator.comparing(RoleEntity::getId));
        return all.stream().map(RoleResponseDto::from).toList();
    }

    @Transactional(readOnly = true)
    @Override
    public List<PermissionPageDto> listPermissionPages() {
        return PermissionPageCatalog.PAGES.stream().map(PermissionPageDto::from).toList();
    }

    @Transactional(readOnly = true)
    @Override
    public List<PagePermissionItemDto> getUserPagePermissions(Long userId) {
        requireUser(userId);
        return pagePermissions.findByUserId(userId).stream().map(PagePermissionItemDto::from).toList();
    }

    @Transactional
    @Override
    public List<PagePermissionItemDto> replaceUserPagePermissions(Long actorId, Long userId,
                                                                   AdminUserPagePermissionsRequestDto request) {
        requireUser(userId);
        List<String> targetRoles = roles.findByUserId(userId).stream().map(RoleEntity::getName).toList();
        if (targetRoles.contains(RoleEntity.ROOT)) {
            throw BusinessException.badRequest(ErrorCode.VALIDATION_FAILED,
                    "Không sửa phân quyền trang của tài khoản ROOT", "userId");
        }
        if (actorId != null && actorId.equals(userId)) {
            throw BusinessException.badRequest(ErrorCode.VALIDATION_FAILED,
                    "Không tự khóa phân quyền của chính mình", "userId");
        }
        Set<String> seen = new HashSet<>();
        List<AdminUserPagePermissionsRequestDto.Item> items =
                request.getItems() == null ? List.of() : request.getItems();
        for (AdminUserPagePermissionsRequestDto.Item item : items) {
            String code = item.getPageCode() == null ? "" : item.getPageCode().trim();
            if (!PermissionPageCatalog.isKnown(code)) {
                throw BusinessException.badRequest(ErrorCode.VALIDATION_FAILED,
                        "pageCode không hợp lệ: " + item.getPageCode(), "pageCode");
            }
            if (!seen.add(code)) {
                throw BusinessException.badRequest(ErrorCode.VALIDATION_FAILED,
                        "Trùng pageCode: " + code, "pageCode");
            }
        }
        pagePermissions.deleteByUserId(userId);
        for (AdminUserPagePermissionsRequestDto.Item item : items) {
            String code = item.getPageCode().trim();
            boolean view = item.isView() || item.isEdit() || item.isDelete();
            boolean edit = item.isEdit();
            boolean delete = item.isDelete();
            if (!view && !edit && !delete) {
                continue;
            }
            UserPagePermissionEntity row = new UserPagePermissionEntity(userId, code, view, edit, delete);
            pagePermissions.create(row);
        }
        return pagePermissions.findByUserId(userId).stream().map(PagePermissionItemDto::from).toList();
    }

    private UserEntity requireUser(Long userId) {
        UserEntity user = users.findOne(userId);
        if (user == null || user.getDeletedDate() != null) {
            throw new ResourceNotFoundException("Người dùng", "User", userId);
        }
        return user;
    }
}
