package com.pk.core.identity.service.impl;

import lombok.RequiredArgsConstructor;

import com.pk.core.common.exception.ErrorCode;
import com.pk.core.identity.service.AuthService;
import com.pk.core.identity.service.RefreshTokenService;

import com.pk.core.common.exception.BusinessException;
import com.pk.core.common.exception.ResourceNotFoundException;
import com.pk.core.common.util.PhoneUtil;
import com.pk.core.model.dto.request.LoginRequestDto;
import com.pk.core.model.dto.request.RegisterRequestDto;
import com.pk.core.model.dto.request.UpdateProfileRequestDto;
import com.pk.core.model.dto.response.PagePermissionItemDto;
import com.pk.core.model.dto.response.TokenResponseDto;
import com.pk.core.model.dto.response.UserResponseDto;
import com.pk.core.identity.security.jwt.JwtProvider;
import com.pk.core.model.entity.RoleEntity;
import com.pk.core.business.repository.RoleRepo;
import com.pk.core.model.entity.UserEntity;
import com.pk.core.business.repository.UserPagePermissionRepo;
import com.pk.core.business.repository.UserRepo;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepo users;
    private final RoleRepo roles;
    private final UserPagePermissionRepo pagePermissions;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final RefreshTokenService refreshTokens;


    @Transactional
    @Override
    public TokenResponseDto register(RegisterRequestDto req) {
        String phone = PhoneUtil.normalize(req.getPhone());
        if (users.countByPhone(phone) > 0) {
            throw BusinessException.conflict(ErrorCode.PHONE_TAKEN, "Số điện thoại đã được đăng ký");
        }
        // Email là thông tin phụ tuỳ chọn: chỉ kiểm tra trùng khi người dùng có nhập.
        String email = normalizeEmail(req.getEmail());
        if (email != null && users.countByEmail(email) > 0) {
            throw BusinessException.conflict(ErrorCode.EMAIL_TAKEN, "Email đã được đăng ký");
        }
        UserEntity user = new UserEntity(phone, passwordEncoder.encode(req.getPassword()),
                req.getFullName().trim());
        user.setEmail(email);
        users.create(user);
        RoleEntity customer = roleOrCreate(RoleEntity.CUSTOMER);
        users.addRole(user.getId(), customer.getId());
        user.getRoles().add(customer);
        return tokensFor(user, refreshTokens.issue(user));
    }

    @Transactional
    @Override
    public TokenResponseDto login(LoginRequestDto req) {
        UserEntity user = UserRoles.attach(users.findByPhone(PhoneUtil.normalize(req.getPhone())), roles);
        // Cùng một thông báo cho "sai SĐT" và "sai mật khẩu" để không lộ SĐT nào đã đăng ký.
        if (user == null || !passwordEncoder.matches(req.getPassword(), user.getPasswordHash())) {
            throw BusinessException.unauthorized(ErrorCode.INVALID_CREDENTIALS, "Số điện thoại hoặc mật khẩu không đúng");
        }
        if (!user.isEnabled()) {
            throw BusinessException.unauthorized(ErrorCode.ACCOUNT_DISABLED, "Tài khoản đã bị khoá");
        }
        return tokensFor(user, refreshTokens.issue(user));
    }

    @Transactional
    @Override
    public TokenResponseDto loginAdmin(LoginRequestDto req) {
        UserEntity user = UserRoles.attach(users.findByPhone(PhoneUtil.normalize(req.getPhone())), roles);
        // Cùng thông báo cho "sai SĐT"/"sai mật khẩu" như login() thường - không lộ SĐT đã đăng ký.
        if (user == null || !passwordEncoder.matches(req.getPassword(), user.getPasswordHash())) {
            throw BusinessException.unauthorized(ErrorCode.INVALID_CREDENTIALS, "Số điện thoại hoặc mật khẩu không đúng");
        }
        if (!user.isEnabled()) {
            throw BusinessException.unauthorized(ErrorCode.ACCOUNT_DISABLED, "Tài khoản đã bị khoá");
        }
        // Khác login() thường: bắt buộc có role ADMIN, sai thì 403 (đã xác thực đúng danh tính, chỉ
        // không đủ quyền - không dùng lại INVALID_CREDENTIALS vì đó là lỗi 401 sai danh tính).
        // Vai trò nhân viên (ADMIN/CATALOG_MANAGER/ORDER_MANAGER) đều đăng nhập được khu quản trị; quyền vào từng
        // nhóm API do SecurityConfig quyết định theo role.
        if (RoleEntity.STAFF_ROLES.stream().noneMatch(user::hasRole)) {
            throw BusinessException.forbidden(ErrorCode.FORBIDDEN, "Tài khoản không có quyền quản trị");
        }
        return tokensFor(user, refreshTokens.issue(user));
    }

    @Transactional(noRollbackFor = BusinessException.class)
    @Override
    public TokenResponseDto refresh(String refreshToken) {
        RefreshTokenService.Rotation r = refreshTokens.rotate(refreshToken);
        return tokensFor(r.user(), r.newRefreshToken());
    }

    @Transactional
    @Override
    public void logout(String refreshToken) {
        refreshTokens.revoke(refreshToken);
    }

    @Transactional(readOnly = true)
    @Override
    public UserResponseDto me(Long userId) {
        UserEntity user = UserRoles.attach(users.findOne(userId), roles);
        if (user == null) {
            throw new ResourceNotFoundException("Người dùng", "User", userId);
        }
        return toUserDto(user);
    }

    @Transactional
    @Override
    public UserResponseDto updateProfile(Long userId, UpdateProfileRequestDto req) {
        UserEntity user = UserRoles.attach(users.findOne(userId), roles);
        if (user == null) {
            throw new ResourceNotFoundException("Người dùng", "User", userId);
        }
        // Email tuỳ chọn: null/rỗng = xoá email đã lưu (lưu NULL, không lưu chuỗi rỗng vì cột UNIQUE).
        String email = normalizeEmail(req.getEmail());
        if (email != null && !email.equals(user.getEmail()) && users.countByEmail(email) > 0) {
            throw BusinessException.conflict(ErrorCode.EMAIL_TAKEN, "Email đã được đăng ký");
        }
        user.setFullName(req.getFullName().trim());
        user.setEmail(email);
        user.touch();
        users.update(user);
        return toUserDto(user);
    }

    private TokenResponseDto tokensFor(UserEntity user, String refreshToken) {
        JwtProvider.AccessToken at = jwtProvider.issueAccessToken(user);
        return TokenResponseDto.bearer(at.token(), refreshToken, at.expiresInSeconds(), toUserDto(user));
    }

    private UserResponseDto toUserDto(UserEntity user) {
        List<PagePermissionItemDto> grants = pagePermissions.findByUserId(user.getId()).stream()
                .map(PagePermissionItemDto::from)
                .toList();
        return UserResponseDto.from(user, grants);
    }

    /** Role thường đã có từ migration V1; tạo lại nếu thiếu (vd. DB test rỗng). */
    private RoleEntity roleOrCreate(String name) {
        RoleEntity role = roles.findByName(name);
        return role != null ? role : roles.create(new RoleEntity(name));
    }

    /** Chữ thường + bỏ khoảng trắng; null hoặc rỗng => null (không có email). */
    private static String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

}
