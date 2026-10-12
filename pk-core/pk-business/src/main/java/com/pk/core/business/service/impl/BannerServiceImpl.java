package com.pk.core.business.service.impl;

import lombok.RequiredArgsConstructor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pk.core.business.repository.BannerPlacementRepo;
import com.pk.core.business.repository.BannerRepo;
import com.pk.core.business.service.BannerService;
import com.pk.core.common.exception.BusinessException;
import com.pk.core.common.exception.ErrorCode;
import com.pk.core.common.exception.ResourceNotFoundException;
import com.pk.core.common.web.PageResponse;
import com.pk.core.model.dto.request.AdminBannerRequestDto;
import com.pk.core.model.dto.request.BannerActionRequestDto;
import com.pk.core.model.dto.response.BannerActionResponseDto;
import com.pk.core.model.dto.response.BannerPageResponseDto;
import com.pk.core.model.dto.response.BannerPlacementRenderResponseDto;
import com.pk.core.model.dto.response.BannerResponseDto;
import com.pk.core.model.entity.BannerEntity;
import com.pk.core.model.entity.BannerPlacementEntity;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class BannerServiceImpl implements BannerService {

    private static final TypeReference<List<BannerActionResponseDto>> ACTION_LIST = new TypeReference<>() {
    };

    private final BannerRepo banners;
    private final BannerPlacementRepo placements;
    private final ObjectMapper objectMapper;

    /** Field-injection (không vào constructor) để unit test dựng service 3 tham số; null = luôn render kiểu cũ. */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private BannerLayoutSupport layoutSupport;

    @Transactional(readOnly = true)
    @Override
    public BannerPageResponseDto findAll(int page, int take, String status, String mediaType, String actionType,
                                         String internalName) {
        List<BannerEntity> matched = new ArrayList<>();
        for (BannerEntity b : banners.findAll(Sort.unsorted())) {
            if (b.getDeletedDate() != null) {
                continue;
            }
            if (!blank(status) && !status.trim().equalsIgnoreCase(b.getStatus())) {
                continue;
            }
            if (!blank(mediaType) && !mediaType.trim().equalsIgnoreCase(b.getMediaType())) {
                continue;
            }
            if (!blank(internalName) && (b.getInternalName() == null || !b.getInternalName()
                    .toLowerCase(Locale.ROOT).contains(internalName.trim().toLowerCase(Locale.ROOT)))) {
                continue;
            }
            if (!blank(actionType) && !hasAction(b, actionType.trim())) {
                continue;
            }
            matched.add(b);
        }
        matched.sort(Comparator.comparing(BannerEntity::getId).reversed());

        PageResponse<BannerEntity> paged = PageResponse.paginate(matched, page, take);
        List<BannerResponseDto> items = paged.content().stream().map(this::toDto).toList();
        return new BannerPageResponseDto(items, paged.totalElements(), paged.page(), paged.size());
    }

    @Transactional(readOnly = true)
    @Override
    public BannerResponseDto findById(Long id) {
        return toDto(findOrThrow(id));
    }

    @Transactional
    @Override
    public BannerResponseDto create(AdminBannerRequestDto req) {
        BannerEntity banner = new BannerEntity();
        apply(banner, req);
        banners.create(banner);
        return toDto(banner);
    }

    @Transactional
    @Override
    public BannerResponseDto update(Long id, AdminBannerRequestDto req) {
        BannerEntity banner = findOrThrow(id);
        apply(banner, req);
        banner.touch();
        banners.update(banner);
        return toDto(banner);
    }

    @Transactional
    @Override
    public void delete(Long id) {
        BannerEntity banner = findOrThrow(id);
        banner.setDeletedDate(new Date());
        banner.touch();
        banners.update(banner);
    }

    @Transactional(readOnly = true)
    @Override
    public BannerPlacementRenderResponseDto renderByPlacementCode(String code, String platform) {
        BannerPlacementEntity placement = blank(code) ? null : placements.findByCode(code.trim().toUpperCase(Locale.ROOT));
        if (placement == null) {
            throw new ResourceNotFoundException("Vị trí banner", "BannerPlacement", code);
        }
        boolean hidden = "INACTIVE".equals(placement.getStatus())
                || (!blank(platform) && !"ALL".equals(placement.getPlatform())
                && !platform.trim().equalsIgnoreCase(placement.getPlatform()));
        if (hidden) {
            return emptyRender(placement);
        }
        if (layoutSupport != null) {
            BannerPlacementRenderResponseDto fromLayout = layoutSupport.render(placement);
            if (fromLayout != null) {
                return fromLayout;
            }
        }
        List<BannerResponseDto> list = banners.findActiveByPlacementCode(placement.getCode()).stream()
                .map(this::toDto).toList();
        BannerPlacementRenderResponseDto dto = new BannerPlacementRenderResponseDto(placement.getCode(),
                placement.getName(), placement.getDisplayType(), list);
        dto.setPlatform(placement.getPlatform());
        return dto;
    }

    private BannerPlacementRenderResponseDto emptyRender(BannerPlacementEntity placement) {
        BannerPlacementRenderResponseDto dto = new BannerPlacementRenderResponseDto(placement.getCode(),
                placement.getName(), placement.getDisplayType(), new ArrayList<>());
        dto.setPlatform(placement.getPlatform());
        return dto;
    }

    // ------------------------------------------------------------------ helpers

    /** Ghi các field khác null của req vào entity (dùng chung cho tạo và PATCH). */
    private void apply(BannerEntity b, AdminBannerRequestDto req) {
        if (req.getInternalName() != null) {
            if (req.getInternalName().isBlank()) {
                throw BusinessException.badRequest(ErrorCode.VALIDATION_FAILED, "internalName không được để trống");
            }
            b.setInternalName(req.getInternalName().trim());
        }
        if (req.getMediaUrl() != null) {
            b.setMediaUrl(req.getMediaUrl());
        }
        if (req.getMediaMobileUrl() != null) {
            b.setMediaMobileUrl(req.getMediaMobileUrl());
        }
        if (req.getMediaLinkUrl() != null) {
            b.setMediaLinkUrl(req.getMediaLinkUrl());
        }
        if (req.getMediaPosterUrl() != null) {
            b.setMediaPosterUrl(req.getMediaPosterUrl());
        }
        if (req.getMediaMobilePosterUrl() != null) {
            b.setMediaMobilePosterUrl(req.getMediaMobilePosterUrl());
        }
        if (req.getMediaFit() != null) {
            b.setMediaFit(req.getMediaFit());
        }
        if (req.getMediaType() != null) {
            b.setMediaType(req.getMediaType());
        }
        if (req.getLayout() != null) {
            b.setLayout(req.getLayout());
        }
        if (req.getTitle() != null) {
            b.setTitle(req.getTitle());
        }
        if (req.getSubtitle() != null) {
            b.setSubtitle(req.getSubtitle());
        }
        if (req.getTitleColor() != null) {
            b.setTitleColor(req.getTitleColor());
        }
        if (req.getActionsLayout() != null) {
            b.setActionsLayout(req.getActionsLayout());
        }
        if (req.getActions() != null) {
            b.setActions(writeActions(req.getActions()));
        }
        if (req.getOverlayOpacity() != null) {
            b.setOverlayOpacity(BigDecimal.valueOf(req.getOverlayOpacity()));
        }
        if (req.getStatus() != null) {
            b.setStatus(req.getStatus());
        }
        if (req.getSortOrder() != null) {
            b.setSortOrder(req.getSortOrder());
        }
        if (req.getPlacementCode() != null) {
            if (req.getPlacementCode().isBlank()) {
                b.setPlacementCode(null);
            } else {
                String code = req.getPlacementCode().trim().toUpperCase(Locale.ROOT);
                if (placements.findByCode(code) == null) {
                    throw new ResourceNotFoundException("Vị trí banner", "BannerPlacement", code);
                }
                b.setPlacementCode(code);
            }
        }
    }

    /** Lưu DB chỉ với key camelCase (không lẫn các key snake_case tính toán dành cho response FE). */
    private String writeActions(List<BannerActionRequestDto> actions) {
        List<java.util.Map<String, Object>> out = new ArrayList<>();
        for (BannerActionRequestDto a : actions) {
            java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("actionType", a.getActionType());
            m.put("actionTarget", a.getActionTarget());
            m.put("ctaText", a.getCtaText());
            m.put("ctaBg", a.getCtaBg());
            m.put("ctaColor", a.getCtaColor());
            out.add(m);
        }
        try {
            return objectMapper.writeValueAsString(out);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Không ghi được actions của banner", ex);
        }
    }

    private List<BannerActionResponseDto> readActions(String json) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(json, ACTION_LIST);
        } catch (JsonProcessingException ex) {
            return new ArrayList<>(); // dữ liệu hỏng thì coi như không có nút, không làm hỏng cả trang banner
        }
    }

    private boolean hasAction(BannerEntity b, String type) {
        boolean link = "LINK".equalsIgnoreCase(type) || "URL".equalsIgnoreCase(type);
        for (BannerActionResponseDto a : readActions(b.getActions())) {
            String t = a.getActionType();
            if (t == null) {
                continue;
            }
            if (link ? ("URL".equalsIgnoreCase(t) || "LINK".equalsIgnoreCase(t)) : type.equalsIgnoreCase(t)) {
                return true;
            }
        }
        return false;
    }

    private BannerResponseDto toDto(BannerEntity b) {
        return BannerResponseDto.from(b, readActions(b.getActions()));
    }

    private BannerEntity findOrThrow(Long id) {
        BannerEntity banner = banners.findOne(id);
        if (banner == null || banner.getDeletedDate() != null) {
            throw new ResourceNotFoundException("Banner", "Banner", id);
        }
        return banner;
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
