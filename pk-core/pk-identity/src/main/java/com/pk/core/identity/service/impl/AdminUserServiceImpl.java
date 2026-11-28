package com.pk.core.identity.service.impl;

import lombok.RequiredArgsConstructor;

import com.pk.core.business.repository.RoleRepo;
import com.pk.core.business.repository.UserRepo;
import com.pk.core.common.exception.BusinessException;
import com.pk.core.common.exception.ErrorCode;
import com.pk.core.common.exception.ResourceNotFoundException;
import com.pk.core.common.util.PhoneUtil;
import com.pk.core.identity.security.model.Permission;
import com.pk.core.identity.service.AdminUserService;
import com.pk.core.identity.service.OtpSender;
import com.pk.core.identity.service.RefreshTokenService;
import com.pk.core.model.dto.request.AdminUserCreateRequestDto;
import com.pk.core.model.dto.request.AdminUserRolesRequestDto;
import com.pk.core.model.dto.request.AdminUserUpdateRequestDto;
import com.pk.core.model.dto.response.AdminUserResponseDto;
import com.pk.core.model.dto.response.RoleResponseDto;
import com.pk.core.model.entity.RoleEntity;
import com.pk.core.model.entity.UserEntity;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AdminUserServiceImpl implements AdminUserService {

    private final UserRepo users;
    private final RoleRepo roles;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokens;
    private final OtpSender otpSender;

    @Transactional
    @Override
    public AdminUserResponseDto create(AdminUserCreateRequestDto req) {
        String phone = PhoneUtil.normalize(req.getPhone());
        if (!PhoneUtil.isValid(phone)) {
            throw BusinessException.badRequest(ErrorCode.VALIDATION_FAILED, "Số điện thoại không hợp lệ", req.getPhone());
        }
        if (users.countByPhone(phone) > 0) {
            throw BusinessException.conflict(ErrorCode.PHONE_TAKEN, "Số điện thoại đã được đăng ký");
        }
        String email = normalizeEmail(req.getEmail());
        if (email != null && users.countByEmail(email) > 0) {
            throw BusinessException.conflict(ErrorCode.EMAIL_TAKEN, "Email đã được đăng ký");
        }
        Set<String> roleNames = normalizeRoles(req.getRoles());

        UserEntity user = new UserEntity(phone, passwordEncoder.encode(req.getPassword()), req.getFullName().trim());
        user.setEmail(email);
        users.create(user);
        writeRoles(user.getId(), roleNames);
        // Gửi OTP welcome qua mail forward (Resend) — cùng kênh OTP client; không SMS.
        String inviteCode = String.format("%06d", java.util.concurrent.ThreadLocalRandom.current().nextInt(0, 1_000_000));
        otpSender.send(phone, inviteCode, "ADMIN_INVITE");
        return toDto(user);
    }

    @Transactional(readOnly = true)
    @Override
    public AdminUserResponseDto findById(Long id) {
        return toDto(requireUser(id));
    }

    @Transactional
    @Override
    public AdminUserResponseDto update(Long actorId, Long id, AdminUserUpdateRequestDto req) {
        UserEntity user = requireUser(id);
        if (req.getFullName() != null) {
            if (req.getFullName().isBlank()) {
                throw BusinessException.badRequest(ErrorCode.VALIDATION_FAILED, "Họ tên không được để trống");
            }
            user.setFullName(req.getFullName().trim());
        }
        if (req.getEmail() != null) {
            String email = normalizeEmail(req.getEmail());
            if (email != null && !email.equals(user.getEmail())) {
                UserEntity other = users.findByEmail(email);
                if (other != null && !other.getId().equals(user.getId())) {
                    throw BusinessException.conflict(ErrorCode.EMAIL_TAKEN, "Email đã được đăng ký");
                }
            }
            user.setEmail(email);
        }
        boolean disabling = false;
        if (req.getEnabled() != null && req.getEnabled() != user.isEnabled()) {
            if (!req.getEnabled() && user.getId().equals(actorId)) {
                throw BusinessException.conflict(ErrorCode.DATA_CONFLICT, "Không thể tự khoá tài khoản của chính mình");
            }
            user.setEnabled(req.getEnabled());
            disabling = !req.getEnabled();
        }
        user.touch();
        users.update(user);
        if (disabling) {
            refreshTokens.revokeAllForUser(user.getId());
        }
        return toDto(user);
    }

    @Transactional
    @Override
    public AdminUserResponseDto assignRoles(Long actorId, Long id, AdminUserRolesRequestDto req) {
        UserEntity user = requireUser(id);
        Set<String> roleNames = normalizeRoles(req.getRoles());
        if (user.getId().equals(actorId) && !roleNames.contains(RoleEntity.ADMIN)) {
            throw BusinessException.conflict(ErrorCode.DATA_CONFLICT, "Không thể tự gỡ vai trò ADMIN của chính mình");
        }
        users.removeAllRoles(user.getId());
        writeRoles(user.getId(), roleNames);
        refreshTokens.revokeAllForUser(user.getId());
        return toDto(user);
    }

    @Transactional(readOnly = true)
    @Override
    public List<RoleResponseDto> listRoles() {
        List<RoleEntity> all = new ArrayList<>();
        for (RoleEntity r : roles.findAll(Sort.unsorted())) {
            all.add(r);
        }
        all.sort(Comparator.comparing(RoleEntity::getId));
        return all.stream()
                .map(r -> RoleResponseDto.from(r, Permission.forRole(r.getName()).stream().map(Enum::name).sorted().toList()))
                .toList();
    }

    // ------------------------------------------------------------------ helpers

    private UserEntity requireUser(Long id) {
        UserEntity user = id == null ? null : users.findOne(id);
        if (user == null || user.getDeletedDate() != null) {
            throw new ResourceNotFoundException("Tài khoản", "User", id);
        }
        return user;
    }

    private AdminUserResponseDto toDto(UserEntity user) {
        List<String> names = roles.findByUserId(user.getId()).stream().map(RoleEntity::getName).sorted().toList();
        return AdminUserResponseDto.from(user, names);
    }

    private static Set<String> normalizeRoles(List<String> raw) {
        Set<String> names = new LinkedHashSet<>();
        for (String r : raw) {
            String name = r == null ? "" : r.trim().toUpperCase(Locale.ROOT);
            if (!RoleEntity.ASSIGNABLE_ROLES.contains(name)) {
                throw BusinessException.badRequest(ErrorCode.VALIDATION_FAILED,
                        "Vai trò không hợp lệ: " + r + " (cho phép: " + new java.util.TreeSet<>(RoleEntity.ASSIGNABLE_ROLES) + ")", r);
            }
            names.add(name);
        }
        return names;
    }

    private void writeRoles(Long userId, Set<String> roleNames) {
        for (String name : roleNames) {
            RoleEntity role = roles.findByName(name);
            if (role == null) {
                role = roles.create(new RoleEntity(name));
            }
            users.addRole(userId, role.getId());
        }
    }

    private static String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
