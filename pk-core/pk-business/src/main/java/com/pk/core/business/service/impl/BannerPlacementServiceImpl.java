package com.pk.core.business.service.impl;

import com.pk.core.business.repository.BannerPlacementRepo;
import com.pk.core.business.service.BannerPlacementService;
import com.pk.core.common.exception.BusinessException;
import com.pk.core.common.exception.ErrorCode;
import com.pk.core.common.exception.ResourceNotFoundException;
import com.pk.core.model.dto.request.AdminBannerPlacementRequestDto;
import com.pk.core.model.dto.response.BannerPlacementResponseDto;
import com.pk.core.model.entity.BannerPlacementEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class BannerPlacementServiceImpl implements BannerPlacementService {

    private static final Set<String> DISPLAY_TYPES = Set.of("CAROUSEL", "SLIDER", "GRID", "DOUBLE_BANNER", "POPUP", "SINGLE");
    private static final Pattern CODE = Pattern.compile("[A-Z0-9_]{1,60}");

    private static final Set<String> PLATFORMS = Set.of("WEB", "APP", "ALL");
    private static final Set<String> STATUSES = Set.of("ACTIVE", "INACTIVE");

    private final BannerPlacementRepo placements;
    private final BannerLayoutSupport support;
    private final com.pk.core.business.repository.BannerRepo banners;
    private final com.pk.core.business.repository.BannerPlacementLayoutRepo layouts;
    private final com.pk.core.business.repository.BannerPlacementLayoutItemRepo layoutItems;

    @Transactional(readOnly = true)
    @Override
    public List<BannerPlacementResponseDto> findAll() {
        return placements.findAll(Sort.unsorted()).stream()
                .sorted(Comparator.comparing(BannerPlacementEntity::getId))
                .map(this::toDto).toList();
    }

    @Transactional
    @Override
    public BannerPlacementResponseDto create(AdminBannerPlacementRequestDto req) {
        if (req.getName() == null || req.getName().isBlank()) {
            throw BusinessException.badRequest(ErrorCode.VALIDATION_FAILED, "name không được để trống");
        }
        boolean given = req.getCode() != null && !req.getCode().isBlank();
        String code = given ? req.getCode().trim().toUpperCase(Locale.ROOT) : codeFromName(req.getName());
        if (!CODE.matcher(code).matches()) {
            throw BusinessException.badRequest(ErrorCode.VALIDATION_FAILED,
                    "code chỉ gồm chữ, số, gạch dưới (tối đa 60 ký tự), ví dụ HOME_HERO", req.getCode());
        }
        if (!given) { // code tự sinh: thêm hậu tố _2, _3... nếu trùng
            String base = code;
            for (int i = 2; placements.findByCode(code) != null; i++) {
                String suffix = "_" + i;
                code = base.substring(0, Math.min(base.length(), 60 - suffix.length())) + suffix;
            }
        }
        if (placements.findByCode(code) != null) {
            throw BusinessException.conflict(ErrorCode.DUPLICATE_CODE, "Mã vị trí banner đã tồn tại: " + code, code);
        }
        BannerPlacementEntity e = new BannerPlacementEntity();
        e.setCode(code);
        e.setName(req.getName().trim());
        e.setDisplayType(normalizeType(req.getDisplayType(), "CAROUSEL"));
        e.setPlatform(normalizeEnum(req.getPlatform(), PLATFORMS, "ALL", "platform"));
        e.setStatus(normalizeEnum(req.getStatus(), STATUSES, "ACTIVE", "status"));
        e.setSettings(support.writeMap(BannerLayoutSupport.normalizeSettings(req.getSettings())));
        placements.create(e);
        return toDto(e);
    }

    @Transactional
    @Override
    public BannerPlacementResponseDto update(Long id, AdminBannerPlacementRequestDto req) {
        BannerPlacementEntity e = id == null ? null : placements.findOne(id);
        if (e == null) {
            throw new ResourceNotFoundException("Vị trí banner", "BannerPlacement", id);
        }
        if (req.getName() != null) {
            if (req.getName().isBlank()) {
                throw BusinessException.badRequest(ErrorCode.VALIDATION_FAILED, "name không được để trống");
            }
            e.setName(req.getName().trim());
        }
        if (req.getDisplayType() != null) {
            e.setDisplayType(normalizeType(req.getDisplayType(), e.getDisplayType()));
        }
        e.setPlatform(normalizeEnum(req.getPlatform(), PLATFORMS, e.getPlatform(), "platform"));
        e.setStatus(normalizeEnum(req.getStatus(), STATUSES, e.getStatus(), "status"));
        if (req.getSettings() != null) { // {} = xoá cấu hình
            e.setSettings(support.writeMap(BannerLayoutSupport.normalizeSettings(req.getSettings())));
        }
        placements.update(e);
        return toDto(e);
    }

    @Transactional
    @Override
    public void delete(Long id) {
        BannerPlacementEntity e = id == null ? null : placements.findOne(id);
        if (e == null) {
            throw new ResourceNotFoundException("Vị trí banner", "BannerPlacement", id);
        }
        Long used = banners.countByPlacementCode(e.getCode());
        if (used != null && used > 0) {
            throw BusinessException.conflict("PLACEMENT_IN_USE",
                    "Vị trí \"" + e.getName() + "\" còn " + used + " banner đang dùng, hãy chuyển/xoá banner trước", used);
        }
        for (var layout : layouts.findByPlacementId(id)) {
            layoutItems.deleteByLayoutId(layout.getId());
            layouts.delete(layout);
        }
        placements.delete(e);
    }

    private BannerPlacementResponseDto toDto(BannerPlacementEntity e) {
        return BannerPlacementResponseDto.from(e, support.readMap(e.getSettings()));
    }

    private static String normalizeEnum(String v, Set<String> allowed, String fallback, String field) {
        if (v == null || v.isBlank()) {
            return fallback;
        }
        String t = v.trim().toUpperCase(Locale.ROOT);
        if (!allowed.contains(t)) {
            throw BusinessException.badRequest(ErrorCode.VALIDATION_FAILED,
                    field + " không hợp lệ: " + v + " (" + String.join(" | ", new java.util.TreeSet<>(allowed)) + ")", v);
        }
        return t;
    }

    /** "Banner Trang chủ" -> BANNER_TRANG_CHU. */
    private static String codeFromName(String name) {
        String ascii = java.text.Normalizer.normalize(name.trim(), java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").replace('đ', 'd').replace('Đ', 'D');
        String code = ascii.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_").replaceAll("^_+|_+$", "");
        return code.length() > 60 ? code.substring(0, 60) : code;
    }

    private static String normalizeType(String type, String fallback) {
        if (type == null || type.isBlank()) {
            return fallback;
        }
        String t = type.trim().toUpperCase(Locale.ROOT);
        if (!DISPLAY_TYPES.contains(t)) {
            throw BusinessException.badRequest(ErrorCode.VALIDATION_FAILED,
                    "displayType không hợp lệ: " + type + " (SLIDER | CAROUSEL | GRID | DOUBLE_BANNER | POPUP | SINGLE)", type);
        }
        return t;
    }
}
