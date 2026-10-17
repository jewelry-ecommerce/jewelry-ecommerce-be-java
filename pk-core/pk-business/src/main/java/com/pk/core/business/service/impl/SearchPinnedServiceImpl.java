package com.pk.core.business.service.impl;

import com.pk.core.business.repository.ProductRepo;
import com.pk.core.business.repository.SearchPinnedProductRepo;
import com.pk.core.business.service.ProductService;
import com.pk.core.business.service.SearchPinnedService;
import com.pk.core.common.exception.BusinessException;
import com.pk.core.common.exception.ResourceNotFoundException;
import com.pk.core.model.dto.request.AdminSearchPinnedRequestDto;
import com.pk.core.model.dto.response.ProductResponseDto;
import com.pk.core.model.dto.response.SearchPinnedResponseDto;
import com.pk.core.model.entity.ProductEntity;
import com.pk.core.model.entity.SearchPinnedProductEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class SearchPinnedServiceImpl implements SearchPinnedService {

    static final int SLOTS = 4;
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final SearchPinnedProductRepo pins;
    private final ProductRepo products;
    private final ProductService productService;

    @Transactional(readOnly = true)
    @Override
    public List<SearchPinnedResponseDto> list() {
        return all().stream().map(SearchPinnedServiceImpl::toDto).toList();
    }

    @Transactional
    @Override
    public SearchPinnedResponseDto create(AdminSearchPinnedRequestDto req, Long actorId) {
        if (req.getProductId() == null) {
            throw invalid("productId bắt buộc");
        }
        requireProduct(req.getProductId());
        SearchPinnedProductEntity e = new SearchPinnedProductEntity();
        e.setKeywordPattern(normalizeKeyword(req.getKeywordPattern()));
        e.setProductId(req.getProductId());
        e.setStartDate(parse(req.getStartDate(), "startDate"));
        if (e.getStartDate() == null) {
            e.setStartDate(new Date());
        }
        e.setEndDate(parse(req.getEndDate(), "endDate"));
        checkWindow(e);
        List<SearchPinnedProductEntity> scope = scopeOf(e.getKeywordPattern(), null);
        if (scope.stream().anyMatch(x -> x.getProductId().equals(e.getProductId()))) {
            throw BusinessException.conflict("SEARCH_PINNED_PRODUCT_DUPLICATE", "Sản phẩm đã được ghim trong phạm vi này");
        }
        int pos = req.getPosition() != null ? checkPosition(req.getPosition()) : firstFree(scope);
        if (scope.stream().anyMatch(x -> x.getPosition() == pos)) {
            throw BusinessException.conflict("SEARCH_PINNED_POSITION_TAKEN", "Ô ghim " + pos + " đã có sản phẩm", pos);
        }
        e.setPosition(pos);
        e.setActive(req.getIsActive() == null || req.getIsActive());
        e.setCreatedId(actorId);
        pins.create(e);
        return toDto(e);
    }

    @Transactional
    @Override
    public SearchPinnedResponseDto update(Long id, AdminSearchPinnedRequestDto req, Long actorId) {
        SearchPinnedProductEntity e = require(id);
        if (Boolean.TRUE.equals(req.getClearKeywordPattern())) {
            e.setKeywordPattern(null);
        } else if (req.getKeywordPattern() != null) {
            e.setKeywordPattern(normalizeKeyword(req.getKeywordPattern()));
        }
        if (req.getProductId() != null) {
            requireProduct(req.getProductId());
            e.setProductId(req.getProductId());
        }
        if (req.getStartDate() != null) {
            Date s = parse(req.getStartDate(), "startDate");
            if (s == null) {
                throw invalid("startDate không được để trống");
            }
            e.setStartDate(s);
        }
        if (Boolean.TRUE.equals(req.getClearEndDate())) {
            e.setEndDate(null);
        } else if (req.getEndDate() != null) {
            e.setEndDate(parse(req.getEndDate(), "endDate"));
        }
        checkWindow(e);
        if (req.getIsActive() != null) {
            e.setActive(req.getIsActive());
        }
        List<SearchPinnedProductEntity> scope = scopeOf(e.getKeywordPattern(), e.getId());
        if (scope.stream().anyMatch(x -> x.getProductId().equals(e.getProductId()))) {
            throw BusinessException.conflict("SEARCH_PINNED_PRODUCT_DUPLICATE", "Sản phẩm đã được ghim trong phạm vi này");
        }
        if (req.getPosition() != null) {
            int target = checkPosition(req.getPosition());
            int old = e.getPosition();
            SearchPinnedProductEntity holder = scope.stream().filter(x -> x.getPosition() == target).findFirst().orElse(null);
            if (holder != null) { // đổi chỗ
                holder.setPosition(old);
                holder.setUpdatedId(actorId);
                holder.touch();
                pins.update(holder);
            }
            e.setPosition(target);
        } else if (scope.stream().anyMatch(x -> x.getPosition() == e.getPosition())) {
            // đổi phạm vi (keyword) làm trùng ô: dồn sang ô trống đầu tiên
            e.setPosition(firstFree(scope));
        }
        e.setUpdatedId(actorId);
        e.touch();
        pins.update(e);
        return toDto(e);
    }

    @Transactional
    @Override
    public void delete(Long id) {
        pins.delete(require(id));
    }

    @Transactional(readOnly = true)
    @Override
    public List<ProductResponseDto> activeLandingProducts() {
        Date now = new Date();
        List<ProductResponseDto> out = new ArrayList<>();
        for (SearchPinnedProductEntity p : all()) {
            if (p.getKeywordPattern() != null || !p.isActive()) {
                continue;
            }
            if ((p.getStartDate() != null && p.getStartDate().after(now)) || (p.getEndDate() != null && !now.before(p.getEndDate()))) {
                continue;
            }
            ProductResponseDto dto = productService.findPublishedById(p.getProductId());
            if (dto != null) {
                out.add(dto);
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ helpers

    private List<SearchPinnedProductEntity> all() {
        return pins.findAll(Sort.unsorted()).stream()
                .sorted(Comparator.comparing((SearchPinnedProductEntity x) -> x.getKeywordPattern() == null ? "" : x.getKeywordPattern())
                        .thenComparingInt(SearchPinnedProductEntity::getPosition).thenComparing(SearchPinnedProductEntity::getId))
                .toList();
    }

    private List<SearchPinnedProductEntity> scopeOf(String keyword, Long excludeId) {
        return all().stream().filter(x -> Objects.equals(x.getKeywordPattern(), keyword))
                .filter(x -> excludeId == null || !x.getId().equals(excludeId)).toList();
    }

    private static int firstFree(List<SearchPinnedProductEntity> scope) {
        for (int i = 0; i < SLOTS; i++) {
            final int slot = i;
            if (scope.stream().noneMatch(x -> x.getPosition() == slot)) {
                return i;
            }
        }
        throw BusinessException.conflict("SEARCH_PINNED_FULL", "Đã dùng hết " + SLOTS + " ô ghim");
    }

    private static int checkPosition(int p) {
        if (p < 0 || p >= SLOTS) {
            throw invalid("position phải từ 0 đến " + (SLOTS - 1));
        }
        return p;
    }

    private SearchPinnedProductEntity require(Long id) {
        SearchPinnedProductEntity e = id == null ? null : pins.findOne(id);
        if (e == null) {
            throw new ResourceNotFoundException("Sản phẩm ghim", "SearchPinned", id);
        }
        return e;
    }

    private void requireProduct(Long productId) {
        ProductEntity p = products.findOne(productId);
        if (p == null || p.getDeletedDate() != null) {
            throw BusinessException.badRequest("SEARCH_PINNED_PRODUCT_INVALID", "Sản phẩm không tồn tại: " + productId, productId);
        }
    }

    private static void checkWindow(SearchPinnedProductEntity e) {
        if (e.getEndDate() != null && e.getStartDate() != null && !e.getEndDate().after(e.getStartDate())) {
            throw invalid("endDate phải sau startDate");
        }
    }

    private static String normalizeKeyword(String k) {
        if (k == null || k.isBlank()) {
            return null;
        }
        String v = k.trim().toLowerCase(Locale.ROOT);
        if (v.length() > 200) {
            throw invalid("keywordPattern tối đa 200 ký tự");
        }
        return v;
    }

    private static Date parse(String s, String field) {
        if (s == null || s.isBlank()) {
            return null;
        }
        String v = s.trim();
        try {
            return Date.from(Instant.parse(v));
        } catch (RuntimeException ignore) {
            // thử dạng khác
        }
        try {
            return Date.from(OffsetDateTime.parse(v).toInstant());
        } catch (RuntimeException ignore) {
            // thử dạng khác
        }
        try {
            return Date.from(LocalDateTime.parse(v).atZone(ZONE).toInstant());
        } catch (RuntimeException e) {
            throw invalid(field + " không phải thời gian ISO-8601: " + s);
        }
    }

    private static BusinessException invalid(String message) {
        return BusinessException.badRequest("SEARCH_PINNED_INVALID", message);
    }

    private static SearchPinnedResponseDto toDto(SearchPinnedProductEntity e) {
        return new SearchPinnedResponseDto(e.getId(), e.getKeywordPattern(), e.getProductId(), e.getPosition(),
                e.getStartDate() == null ? null : e.getStartDate().toInstant().toString(),
                e.getEndDate() == null ? null : e.getEndDate().toInstant().toString(), e.isActive(),
                e.getCreatedDate().toInstant().toString(),
                e.getUpdatedDate() == null ? e.getCreatedDate().toInstant().toString() : e.getUpdatedDate().toInstant().toString());
    }
}
