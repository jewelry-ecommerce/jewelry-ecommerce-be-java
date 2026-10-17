package com.pk.core.business.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pk.core.business.repository.SiteFooterRepo;
import com.pk.core.business.repository.SiteHeaderRepo;
import com.pk.core.business.repository.SiteLogoRepo;
import com.pk.core.business.service.SiteLayoutService;
import com.pk.core.common.exception.BusinessException;
import com.pk.core.common.exception.ResourceNotFoundException;
import com.pk.core.common.web.PageResponse;
import com.pk.core.model.dto.request.AdminSiteFooterRequestDto;
import com.pk.core.model.dto.request.AdminSiteHeaderRequestDto;
import com.pk.core.model.dto.request.AdminSiteLogoRequestDto;
import com.pk.core.model.dto.request.AdminSiteStatusRequestDto;
import com.pk.core.model.dto.response.SiteLayoutResponseDto.Footer;
import com.pk.core.model.dto.response.SiteLayoutResponseDto.Header;
import com.pk.core.model.dto.response.SiteLayoutResponseDto.Logo;
import com.pk.core.model.dto.response.SiteLayoutResponseDto.MenuColumn;
import com.pk.core.model.dto.response.SiteLayoutResponseDto.MenuItem;
import com.pk.core.model.dto.response.SiteLayoutResponseDto.SiteLayout;
import com.pk.core.model.dto.response.SiteLayoutResponseDto.SocialLink;
import com.pk.core.model.entity.SiteFooterEntity;
import com.pk.core.model.entity.SiteHeaderEntity;
import com.pk.core.model.entity.SiteLogoEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Header/footer/logo của site. Quy tắc:
 * - Header: tại một thời điểm chỉ một header hiển thị. Header ACTIVE/SCHEDULED có khoảng [start, end) giao nhau với header ACTIVE/SCHEDULED
 *   khác là xung đột (409 HEADER_SCHEDULE_CONFLICT); confirmReplaceActive=true thì các header trùng được chuyển DEACTIVE.
 *   displayStatus tính theo giờ hiện tại (không ghi DB).
 * - Logo: mỗi type chỉ một logo ACTIVE; kích hoạt logo nào thì các logo ACTIVE cùng type chuyển DEACTIVE. "Archive" là xoá mềm.
 * - Footer: một bản duy nhất, PUT ghi đè; chưa từng lưu thì GET trả cấu trúc rỗng (id = null).
 */
@Service
@RequiredArgsConstructor
public class SiteLayoutServiceImpl implements SiteLayoutService {

    private static final TypeReference<List<SocialLink>> SOCIAL_LIST = new TypeReference<>() { };
    private static final TypeReference<List<MenuColumn>> COLUMN_LIST = new TypeReference<>() { };

    private final SiteHeaderRepo headers;
    private final SiteFooterRepo footers;
    private final SiteLogoRepo logos;
    private final ObjectMapper objectMapper;

    // ===================================== header =====================================

    @Transactional(readOnly = true)
    @Override
    public PageResponse<Header> listHeaders(String search, String status, int page, int take) {
        String kw = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        String st = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
        List<Header> all = liveHeaders().stream()
                .filter(h -> kw.isEmpty() || (h.getBannerName() != null && h.getBannerName().toLowerCase(Locale.ROOT).contains(kw)))
                .filter(h -> st.isEmpty() || st.equals(h.getStatus()))
                .sorted(Comparator.comparing(SiteHeaderEntity::getCreatedDate, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(SiteHeaderEntity::getId, Comparator.reverseOrder()))
                .map(this::toDto).toList();
        return PageResponse.paginate(all, page, take);
    }

    @Transactional(readOnly = true)
    @Override
    public Header headerDetail(Long id) {
        return toDto(findHeader(id));
    }

    @Transactional
    @Override
    public Header createHeader(AdminSiteHeaderRequestDto req) {
        if (blank(req.getBannerName())) {
            throw invalid("bannerName là bắt buộc", "bannerName");
        }
        if (blank(req.getBannerDisplayStyle())) {
            throw invalid("bannerDisplayStyle là bắt buộc (TEXT hoặc MEDIA)", "bannerDisplayStyle");
        }
        if (blank(req.getBannerStartTime())) {
            throw invalid("bannerStartTime là bắt buộc (ISO-8601)", "bannerStartTime");
        }
        SiteHeaderEntity h = new SiteHeaderEntity();
        applyHeader(h, req);
        validateHeader(h);
        h.setStatus(blank(req.getStatus()) ? SiteHeaderEntity.DRAFT : req.getStatus());
        resolveConflicts(h, Boolean.TRUE.equals(req.getConfirmReplaceActive()));
        headers.create(h);
        return toDto(h);
    }

    @Transactional
    @Override
    public Header updateHeader(Long id, AdminSiteHeaderRequestDto req) {
        SiteHeaderEntity h = findHeader(id);
        applyHeader(h, req);
        validateHeader(h);
        if (!blank(req.getStatus())) {
            h.setStatus(req.getStatus());
        }
        resolveConflicts(h, Boolean.TRUE.equals(req.getConfirmReplaceActive()));
        h.touch();
        headers.update(h);
        return toDto(h);
    }

    @Transactional
    @Override
    public Header updateHeaderStatus(Long id, AdminSiteStatusRequestDto req) {
        SiteHeaderEntity h = findHeader(id);
        h.setStatus(req.getStatus());
        resolveConflicts(h, Boolean.TRUE.equals(req.getConfirmReplaceActive()));
        h.touch();
        headers.update(h);
        return toDto(h);
    }

    @Transactional
    @Override
    public void deleteHeader(Long id) {
        SiteHeaderEntity h = findHeader(id);
        h.setDeletedDate(new Date());
        h.touch();
        headers.update(h);
    }

    private void applyHeader(SiteHeaderEntity h, AdminSiteHeaderRequestDto r) {
        if (r.getBannerName() != null) {
            h.setBannerName(r.getBannerName().trim());
        }
        if (r.getBannerDisplayStyle() != null) {
            h.setDisplayStyle(r.getBannerDisplayStyle());
        }
        if (r.getBannerContent() != null) {
            h.setContent(r.getBannerContent());
        }
        if (r.getBannerBgColor() != null) {
            h.setBgColor(r.getBannerBgColor());
        }
        if (r.getBannerTextColor() != null) {
            h.setTextColor(r.getBannerTextColor());
        }
        if (r.getBannerTargetUrl() != null) {
            h.setTargetUrl(r.getBannerTargetUrl());
        }
        if (r.getBannerAnimationStyle() != null) {
            h.setAnimationStyle(r.getBannerAnimationStyle());
        }
        if (r.getBannerLoopIntervalSec() != null) {
            h.setLoopIntervalSec(r.getBannerLoopIntervalSec());
        }
        if (r.getBannerStartTime() != null) {
            h.setStartTime(parseTime(r.getBannerStartTime(), "bannerStartTime"));
        }
        if (r.getBannerEndTime() != null) {
            h.setEndTime(blank(r.getBannerEndTime()) ? null : parseTime(r.getBannerEndTime(), "bannerEndTime"));
        }
    }

    private void validateHeader(SiteHeaderEntity h) {
        if (blank(h.getContent())) {
            throw invalid("bannerContent là bắt buộc (nội dung chữ hoặc URL ảnh/video)", "bannerContent");
        }
        if (h.getEndTime() != null && !h.getEndTime().after(h.getStartTime())) {
            throw invalid("bannerEndTime phải sau bannerStartTime", "bannerEndTime");
        }
    }

    /** Nếu header đang được bật (ACTIVE/SCHEDULED): tìm các header bật khác trùng lịch; có -> 409 hoặc (đã xác nhận) chuyển chúng sang DEACTIVE. */
    private void resolveConflicts(SiteHeaderEntity target, boolean confirmReplace) {
        if (!isEnabled(target)) {
            return;
        }
        List<SiteHeaderEntity> conflicts = liveHeaders().stream()
                .filter(o -> target.getId() == null || !o.getId().equals(target.getId()))
                .filter(SiteLayoutServiceImpl::isEnabled)
                .filter(o -> !SiteHeaderEntity.DEACTIVE.equals(effective(o)))
                .filter(o -> overlaps(target, o))
                .toList();
        if (conflicts.isEmpty()) {
            return;
        }
        if (!confirmReplace) {
            String names = conflicts.stream()
                    .map(c -> c.getBannerName() + " (id " + c.getId() + ", " + iso(c.getStartTime()) + " → "
                            + (c.getEndTime() == null ? "không hết hạn" : iso(c.getEndTime())) + ")")
                    .collect(Collectors.joining("; "));
            List<java.util.Map<String, Object>> deps = conflicts.stream().map(c -> {
                java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
                m.put("id", String.valueOf(c.getId()));
                m.put("bannerName", c.getBannerName());
                m.put("status", effective(c));
                m.put("bannerStartTime", iso(c.getStartTime()));
                m.put("bannerEndTime", c.getEndTime() == null ? null : iso(c.getEndTime()));
                return m;
            }).collect(Collectors.toList());
            throw BusinessException.conflict("HEADER_SCHEDULE_CONFLICT",
                    "Trùng lịch với header khác: " + names + ". Gửi confirmReplaceActive=true để thay thế (các header đó sẽ chuyển DEACTIVE).")
                    .withDependencies(deps);
        }
        for (SiteHeaderEntity c : conflicts) {
            c.setStatus(SiteHeaderEntity.DEACTIVE);
            c.touch();
            headers.update(c);
        }
    }

    private static boolean isEnabled(SiteHeaderEntity h) {
        return SiteHeaderEntity.ACTIVE.equals(h.getStatus()) || SiteHeaderEntity.SCHEDULED.equals(h.getStatus());
    }

    private static boolean overlaps(SiteHeaderEntity a, SiteHeaderEntity b) {
        long aEnd = a.getEndTime() == null ? Long.MAX_VALUE : a.getEndTime().getTime();
        long bEnd = b.getEndTime() == null ? Long.MAX_VALUE : b.getEndTime().getTime();
        return a.getStartTime().getTime() < bEnd && b.getStartTime().getTime() < aEnd;
    }

    /** Trạng thái hiệu lực theo giờ hiện tại. */
    static String effective(SiteHeaderEntity h) {
        long now = System.currentTimeMillis();
        boolean expired = h.getEndTime() != null && now >= h.getEndTime().getTime();
        boolean started = h.getStartTime() != null && now >= h.getStartTime().getTime();
        return switch (h.getStatus()) {
            case SiteHeaderEntity.ACTIVE, SiteHeaderEntity.SCHEDULED ->
                    expired ? SiteHeaderEntity.DEACTIVE : (started ? SiteHeaderEntity.ACTIVE : SiteHeaderEntity.SCHEDULED);
            default -> h.getStatus();
        };
    }

    private List<SiteHeaderEntity> liveHeaders() {
        return headers.findAll(Sort.unsorted()).stream().filter(h -> h.getDeletedDate() == null).toList();
    }

    private SiteHeaderEntity findHeader(Long id) {
        SiteHeaderEntity h = id == null ? null : headers.findOne(id);
        if (h == null || h.getDeletedDate() != null) {
            throw new ResourceNotFoundException("Header", "SiteHeader", id);
        }
        return h;
    }

    private Header toDto(SiteHeaderEntity h) {
        return new Header(h.getId(), h.getStatus(), effective(h), h.getBannerName(), h.getDisplayStyle(), h.getContent(),
                h.getBgColor(), h.getTextColor(), h.getTargetUrl(), h.getAnimationStyle(), h.getLoopIntervalSec(),
                iso(h.getStartTime()), iso(h.getEndTime()), iso(h.getCreatedDate()), iso(h.getUpdatedDate()));
    }

    // ===================================== footer =====================================

    @Transactional(readOnly = true)
    @Override
    public Footer footer() {
        SiteFooterEntity f = currentFooter();
        return f == null ? new Footer(null, null, null, null, null, null, null, List.of(), List.of(), null) : toDto(f);
    }

    @Transactional
    @Override
    public Footer saveFooter(AdminSiteFooterRequestDto req) {
        List<SocialLink> social = new ArrayList<>();
        for (AdminSiteFooterRequestDto.SocialLink s : nz(req.getSocialLinks())) {
            social.add(new SocialLink(idOr(s.getId()), s.getPlatform(), s.getDisplayName().trim(), s.getUrl().trim(),
                    s.getImage(), s.getOrderIndex()));
        }
        List<MenuColumn> columns = new ArrayList<>();
        for (AdminSiteFooterRequestDto.MenuColumn c : nz(req.getMenuColumns())) {
            List<MenuItem> items = new ArrayList<>();
            for (AdminSiteFooterRequestDto.MenuItem i : nz(c.getItems())) {
                items.add(new MenuItem(idOr(i.getId()), i.getLabel().trim(), i.getUrl().trim(), i.getOrderIndex()));
            }
            items.sort(Comparator.comparingInt(MenuItem::orderIndex));
            columns.add(new MenuColumn(idOr(c.getId()), c.getTitle().trim(), c.getOrderIndex(), items));
        }
        social.sort(Comparator.comparingInt(SocialLink::orderIndex));
        columns.sort(Comparator.comparingInt(MenuColumn::orderIndex));

        SiteFooterEntity f = currentFooter();
        boolean created = f == null;
        if (created) {
            f = new SiteFooterEntity();
        }
        f.setCopyrightText(req.getCopyrightText());
        f.setFooterLogoUrl(req.getFooterLogoUrl());
        f.setCertificationImageUrl(req.getCertificationImageUrl());
        f.setCertificationUrl(req.getCertificationUrl());
        f.setNewsletterHeading(blank(req.getNewsletterHeading()) ? null : req.getNewsletterHeading().trim());
        f.setNewsletterSubHeading(blank(req.getNewsletterSubHeading()) ? null : req.getNewsletterSubHeading().trim());
        f.setSocialLinks(write(social));
        f.setMenuColumns(write(columns));
        if (created) {
            footers.create(f);
        } else {
            f.touch();
            footers.update(f);
        }
        return toDto(f);
    }

    private SiteFooterEntity currentFooter() {
        return footers.findAll(Sort.unsorted()).stream().filter(f -> f.getDeletedDate() == null)
                .min(Comparator.comparing(SiteFooterEntity::getId)).orElse(null);
    }

    private Footer toDto(SiteFooterEntity f) {
        return new Footer(f.getId(), f.getCopyrightText(), f.getFooterLogoUrl(), f.getCertificationImageUrl(),
                f.getCertificationUrl(), f.getNewsletterHeading(), f.getNewsletterSubHeading(), read(f.getSocialLinks(), SOCIAL_LIST), read(f.getMenuColumns(), COLUMN_LIST),
                iso(f.getUpdatedDate() != null ? f.getUpdatedDate() : f.getCreatedDate()));
    }

    // ===================================== logo =====================================

    @Transactional(readOnly = true)
    @Override
    public PageResponse<Logo> listLogos(String search, String status, String type, int page, int take) {
        String kw = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        String st = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
        String ty = type == null ? "" : type.trim().toUpperCase(Locale.ROOT);
        List<Logo> all = liveLogos().stream()
                .filter(l -> kw.isEmpty() || (l.getName() != null && l.getName().toLowerCase(Locale.ROOT).contains(kw)))
                .filter(l -> st.isEmpty() || st.equals(l.getStatus()))
                .filter(l -> ty.isEmpty() || ty.equals(l.getType()))
                .sorted(Comparator.comparing(SiteLogoEntity::getCreatedDate, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(SiteLogoEntity::getId, Comparator.reverseOrder()))
                .map(this::toDto).toList();
        return PageResponse.paginate(all, page, take);
    }

    @Transactional(readOnly = true)
    @Override
    public Logo logoDetail(Long id) {
        return toDto(findLogo(id));
    }

    @Transactional
    @Override
    public Logo createLogo(AdminSiteLogoRequestDto req) {
        if (blank(req.getName())) {
            throw invalid("name là bắt buộc", "name");
        }
        if (blank(req.getLogoUrl())) {
            throw invalid("logoUrl là bắt buộc", "logoUrl");
        }
        SiteLogoEntity l = new SiteLogoEntity();
        applyLogo(l, req);
        logos.create(l);
        if ("ACTIVE".equals(l.getStatus())) {
            deactivateOthers(l);
        }
        return toDto(l);
    }

    @Transactional
    @Override
    public Logo updateLogo(Long id, AdminSiteLogoRequestDto req) {
        SiteLogoEntity l = findLogo(id);
        applyLogo(l, req);
        if (blank(l.getLogoUrl())) {
            throw invalid("logoUrl không được để trống", "logoUrl");
        }
        l.touch();
        logos.update(l);
        if ("ACTIVE".equals(l.getStatus())) {
            deactivateOthers(l);
        }
        return toDto(l);
    }

    @Transactional
    @Override
    public Logo updateLogoStatus(Long id, AdminSiteStatusRequestDto req) {
        SiteLogoEntity l = findLogo(id);
        l.setStatus(req.getStatus());
        l.touch();
        logos.update(l);
        if ("ACTIVE".equals(l.getStatus())) {
            deactivateOthers(l);
        }
        return toDto(l);
    }

    @Transactional
    @Override
    public void archiveLogo(Long id) {
        SiteLogoEntity l = findLogo(id);
        l.setDeletedDate(new Date());
        l.touch();
        logos.update(l);
    }

    private void applyLogo(SiteLogoEntity l, AdminSiteLogoRequestDto r) {
        if (r.getName() != null) {
            l.setName(r.getName().trim());
        }
        if (r.getType() != null) {
            l.setType(r.getType());
        }
        if (r.getStatus() != null) {
            l.setStatus(r.getStatus());
        }
        if (r.getLogoUrl() != null) {
            l.setLogoUrl(r.getLogoUrl().trim());
        }
        if (r.getLogoTheme() != null) {
            l.setLogoTheme(r.getLogoTheme());
        }
        if (r.getLogoTargetUrl() != null) {
            l.setLogoTargetUrl(r.getLogoTargetUrl());
        }
    }

    private void deactivateOthers(SiteLogoEntity active) {
        for (SiteLogoEntity o : liveLogos()) {
            if (!o.getId().equals(active.getId()) && active.getType().equals(o.getType()) && "ACTIVE".equals(o.getStatus())) {
                o.setStatus("DEACTIVE");
                o.touch();
                logos.update(o);
            }
        }
    }

    private List<SiteLogoEntity> liveLogos() {
        return logos.findAll(Sort.unsorted()).stream().filter(l -> l.getDeletedDate() == null).toList();
    }

    private SiteLogoEntity findLogo(Long id) {
        SiteLogoEntity l = id == null ? null : logos.findOne(id);
        if (l == null || l.getDeletedDate() != null) {
            throw new ResourceNotFoundException("Logo", "SiteLogo", id);
        }
        return l;
    }

    private Logo toDto(SiteLogoEntity l) {
        return new Logo(l.getId(), l.getName(), l.getType(), l.getStatus(), l.getLogoUrl(), l.getLogoTheme(),
                l.getLogoTargetUrl(), iso(l.getCreatedDate()), iso(l.getUpdatedDate()));
    }

    // ===================================== public =====================================

    @Transactional(readOnly = true)
    @Override
    public SiteLayout publicLayout() {
        Header header = liveHeaders().stream()
                .filter(h -> SiteHeaderEntity.ACTIVE.equals(effective(h)))
                .max(Comparator.comparing(SiteHeaderEntity::getStartTime))
                .map(this::toDto).orElse(null);
        Map<String, Logo> byType = new LinkedHashMap<>();
        liveLogos().stream().filter(l -> "ACTIVE".equals(l.getStatus()))
                .sorted(Comparator.comparing(SiteLogoEntity::getId))
                .forEach(l -> byType.put(l.getType(), toDto(l)));
        SiteFooterEntity f = currentFooter();
        return new SiteLayout(header, f == null ? null : toDto(f), byType);
    }

    // ===================================== helpers =====================================

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    private static <T> List<T> nz(List<T> l) {
        return l == null ? List.of() : l;
    }

    private static String idOr(String id) {
        return blank(id) ? UUID.randomUUID().toString() : id.trim();
    }

    private static BusinessException invalid(String message, String field) {
        return BusinessException.badRequest("SITE_LAYOUT_INVALID", message, field);
    }

    private static Date parseTime(String value, String field) {
        try {
            return Date.from(Instant.parse(value.trim()));
        } catch (DateTimeParseException e) {
            try {
                return Date.from(java.time.OffsetDateTime.parse(value.trim()).toInstant());
            } catch (DateTimeParseException e2) {
                throw invalid(field + " phải là thời gian ISO-8601, ví dụ 2026-10-20T00:00:00Z", field);
            }
        }
    }

    private static String iso(Date d) {
        return d == null ? null : Instant.ofEpochMilli(d.getTime()).toString();
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("Không ghi được JSON footer", e);
        }
    }

    private <T> List<T> read(String json, TypeReference<List<T>> type) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            throw new IllegalStateException("JSON footer hỏng trong DB", e);
        }
    }
}
