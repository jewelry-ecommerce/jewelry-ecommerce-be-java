package com.pk.core.business.service.impl;

import lombok.RequiredArgsConstructor;

import com.pk.core.business.repository.CollectionRepo;
import com.pk.core.business.repository.PreOrderConfigRepo;
import com.pk.core.business.repository.ProductRepo;
import com.pk.core.business.service.PreOrderScope;
import com.pk.core.common.exception.BusinessException;
import com.pk.core.common.exception.ErrorCode;
import com.pk.core.model.entity.CollectionEntity;
import com.pk.core.model.entity.ProductEntity;
import com.pk.core.model.entity.ProductSkuEntity;
import com.pk.core.business.service.PreOrderService;
import com.pk.core.model.dto.request.PreOrderConfigRequestDto;
import com.pk.core.model.dto.response.PreOrderConfigResponseDto;
import com.pk.core.model.entity.PreOrderConfigEntity;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PreOrderServiceImpl implements PreOrderService {

    private static final Set<String> SCOPE_TYPES = Set.of(PreOrderScope.ALL, PreOrderScope.SKU, PreOrderScope.PRODUCT,
            PreOrderScope.CATEGORY, PreOrderScope.COLLECTION);

    private final PreOrderConfigRepo configs;
    private final ProductRepo products;
    private final CollectionRepo collections;

    @Transactional(readOnly = true)
    @Override
    public List<PreOrderConfigResponseDto> list() {
        List<PreOrderConfigEntity> all = new ArrayList<>();
        for (PreOrderConfigEntity c : configs.findAll(Sort.unsorted())) {
            all.add(c);
        }
        all.sort(Comparator.comparing(PreOrderConfigEntity::getId).reversed());
        return all.stream().map(PreOrderConfigResponseDto::from).toList();
    }

    @Transactional(readOnly = true)
    @Override
    public PreOrderConfigResponseDto current() {
        List<PreOrderConfigResponseDto> all = list();
        return all.isEmpty() ? new PreOrderConfigResponseDto(null, false, null, null) : all.get(0);
    }

    @Transactional(readOnly = true)
    @Override
    public boolean isEnabled() {
        return current().isEnabled();
    }

    @Transactional(readOnly = true)
    @Override
    public PreOrderScope scope() {
        PreOrderConfigResponseDto c = current();
        if (!c.isEnabled()) {
            return PreOrderScope.off();
        }
        return new PreOrderScope(true, c.getScopeType(), new HashSet<>(c.getScopeIds()));
    }

    @Transactional(readOnly = true)
    @Override
    public boolean appliesTo(ProductSkuEntity sku) {
        PreOrderScope scope = scope();
        if (!scope.enabled()) {
            return false;
        }
        if (scope.isAll()) {
            return true;
        }
        ProductEntity product = sku.getProductId() == null ? null : products.findOne(sku.getProductId());
        Set<Long> collectionIds = new HashSet<>();
        if (sku.getProductId() != null) {
            for (CollectionEntity c : collections.findByProductId(sku.getProductId())) {
                collectionIds.add(c.getId());
            }
        }
        return scope.covers(sku.getId(), sku.getProductId(), product == null ? null : product.getCategoryId(), collectionIds);
    }

    @Transactional
    @Override
    public PreOrderConfigResponseDto create(Long adminId, PreOrderConfigRequestDto req) {
        String message = req.getMessage() == null || req.getMessage().isBlank() ? null : req.getMessage().trim();
        PreOrderConfigEntity entity = new PreOrderConfigEntity(Boolean.TRUE.equals(req.getEnabled()), message, adminId);
        String type = req.getScopeType() == null || req.getScopeType().isBlank() ? PreOrderScope.ALL
                : req.getScopeType().trim().toUpperCase(Locale.ROOT);
        if (!SCOPE_TYPES.contains(type)) {
            throw BusinessException.badRequest(ErrorCode.VALIDATION_FAILED,
                    "scopeType không hợp lệ: " + req.getScopeType() + " (ALL | SKU | PRODUCT | CATEGORY | COLLECTION)",
                    req.getScopeType());
        }
        List<Long> ids = req.getScopeIds() == null ? List.of() : req.getScopeIds();
        if (PreOrderScope.ALL.equals(type)) {
            if (!ids.isEmpty()) {
                throw BusinessException.badRequest(ErrorCode.VALIDATION_FAILED,
                        "scopeIds phải bỏ trống khi scopeType = ALL (đặt scopeType SKU/PRODUCT/CATEGORY/COLLECTION để giới hạn phạm vi)");
            }
        } else {
            if (ids.isEmpty() || ids.size() > 200 || ids.stream().anyMatch(i -> i == null || i < 1)) {
                throw BusinessException.badRequest(ErrorCode.VALIDATION_FAILED,
                        "scopeIds cần 1..200 id dương khi scopeType = " + type);
            }
            entity.setScopeIds(new LinkedHashSet<>(ids).stream().map(String::valueOf).collect(Collectors.joining(",")));
        }
        entity.setScopeType(type);
        configs.create(entity);
        return PreOrderConfigResponseDto.from(entity);
    }
}
