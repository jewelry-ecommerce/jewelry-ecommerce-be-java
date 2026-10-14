package com.pk.core.business.service.impl;

import lombok.RequiredArgsConstructor;

import com.pk.core.business.repository.CategoryAttributeRepo;
import com.pk.core.business.repository.ProductAttributeRepo;
import com.pk.core.business.repository.ProductAttributeValueRepo;
import com.pk.core.business.repository.SkuAttributeValueRepo;
import com.pk.core.business.service.ProductAttributeService;
import com.pk.core.common.exception.BusinessException;
import com.pk.core.common.exception.ErrorCode;
import com.pk.core.common.exception.ResourceNotFoundException;
import com.pk.core.model.dto.request.AdminAttributeRequestDto;
import com.pk.core.model.dto.request.AdminAttributeValueRequestDto;
import com.pk.core.model.dto.response.ProductAttributeResponseDto;
import com.pk.core.model.entity.ProductAttributeEntity;
import com.pk.core.model.entity.ProductAttributeValueEntity;
import com.pk.core.model.entity.SkuAttributeValueEntity;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ProductAttributeServiceImpl implements ProductAttributeService {

    private static final Set<String> TYPES = Set.of(ProductAttributeEntity.TEXT, ProductAttributeEntity.SELECT,
            ProductAttributeEntity.MULTISELECT);
    private static final Set<String> DISPLAY_TYPES = Set.of("TEXT", "IMAGE");
    private static final Set<String> STATUSES = Set.of(ProductAttributeEntity.ACTIVE, ProductAttributeEntity.INACTIVE);

    private final ProductAttributeRepo attributes;
    private final ProductAttributeValueRepo valueRepo;
    private final SkuAttributeValueRepo skuValues;
    private final CategoryAttributeRepo categoryAttributes;

    /** Một dòng giá trị đã chuẩn hoá từ request. */
    private record Item(Long id, String code, String value, String imageUrl) {
    }

    @Transactional(readOnly = true)
    @Override
    public List<ProductAttributeResponseDto> findAll() {
        Map<Long, List<ProductAttributeValueEntity>> byAttribute = new HashMap<>();
        for (ProductAttributeValueEntity v : valueRepo.findAll(Sort.unsorted())) {
            byAttribute.computeIfAbsent(v.getAttributeId(), k -> new ArrayList<>()).add(v);
        }
        byAttribute.values().forEach(list -> list.sort(java.util.Comparator
                .comparingInt(ProductAttributeValueEntity::getSortOrder)
                .thenComparing(ProductAttributeValueEntity::getId)));
        List<ProductAttributeResponseDto> out = new ArrayList<>();
        for (ProductAttributeEntity e : attributes.findAll(Sort.unsorted())) {
            out.add(ProductAttributeResponseDto.from(e, byAttribute.getOrDefault(e.getId(), List.of())));
        }
        return out;
    }

    @Transactional
    @Override
    public ProductAttributeResponseDto create(AdminAttributeRequestDto req) {
        String code = req.getCode().trim();
        if (attributes.findByCode(code) != null) {
            throw BusinessException.conflict(ErrorCode.DUPLICATE_CODE, "Code thuộc tính đã tồn tại: " + code, code);
        }
        ProductAttributeEntity e = new ProductAttributeEntity(req.getName().trim(), code, parseType(req.getType()), null);
        e.setDisplayType(parseEnum(req.getDisplayType(), DISPLAY_TYPES, "displayType", "TEXT"));
        e.setStatus(parseEnum(req.getStatus(), STATUSES, "status", ProductAttributeEntity.ACTIVE));
        List<Item> items = requestedItems(req);
        validateItems(items == null ? List.of() : items);
        attributes.create(e);
        List<ProductAttributeValueEntity> saved = reconcile(e, new ArrayList<>(), items == null ? List.of() : items);
        return ProductAttributeResponseDto.from(e, saved);
    }

    @Transactional
    @Override
    public ProductAttributeResponseDto update(Long attributeId, AdminAttributeRequestDto req) {
        ProductAttributeEntity e = attributes.findOne(attributeId);
        if (e == null) {
            throw new ResourceNotFoundException("Thuộc tính", "Attribute", attributeId);
        }
        String code = req.getCode().trim();
        if (!code.equalsIgnoreCase(e.getCode()) && attributes.findByCode(code) != null) {
            throw BusinessException.conflict(ErrorCode.DUPLICATE_CODE, "Code thuộc tính đã tồn tại: " + code, code);
        }
        e.setName(req.getName().trim());
        e.setCode(code);
        e.setType(parseType(req.getType()));
        if (req.getDisplayType() != null) {
            e.setDisplayType(parseEnum(req.getDisplayType(), DISPLAY_TYPES, "displayType", "TEXT"));
        }
        if (req.getStatus() != null) {
            e.setStatus(parseEnum(req.getStatus(), STATUSES, "status", ProductAttributeEntity.ACTIVE));
        }
        List<ProductAttributeValueEntity> current = new ArrayList<>(valueRepo.findByAttributeId(attributeId));
        List<Item> items = requestedItems(req);
        if (items != null) {
            validateItems(items);
            current = reconcile(e, current, items);
        } else {
            e.touch();
            attributes.update(e);
        }
        return ProductAttributeResponseDto.from(e, current);
    }

    @Transactional
    @Override
    public void delete(Long attributeId) {
        ProductAttributeEntity e = attributes.findOne(attributeId);
        if (e == null) {
            throw new ResourceNotFoundException("Thuộc tính", "Attribute", attributeId);
        }
        Long onSkus = skuValues.countByAttributeId(attributeId);
        if (onSkus != null && onSkus > 0) {
            throw BusinessException.conflict(ErrorCode.ATTRIBUTE_IN_USE,
                    "Thuộc tính đang được gán cho " + onSkus + " SKU, hãy gỡ trước khi xoá", attributeId);
        }
        Long onCategories = categoryAttributes.countByAttributeId(attributeId);
        if (onCategories != null && onCategories > 0) {
            throw BusinessException.conflict(ErrorCode.ATTRIBUTE_IN_USE,
                    "Thuộc tính đang được gán cho " + onCategories + " danh mục, hãy gỡ trước khi xoá", attributeId);
        }
        attributes.delete(e);
    }

    // ------------------------------------------------------------------ giá trị

    /** `values` ưu tiên; không có thì dùng `options` (chuỗi, code = tên). null = request không đụng tới giá trị. */
    private static List<Item> requestedItems(AdminAttributeRequestDto req) {
        if (req.getValues() != null) {
            List<Item> items = new ArrayList<>();
            for (AdminAttributeValueRequestDto v : req.getValues()) {
                String value = v.getValue() == null ? "" : v.getValue().trim();
                String code = v.getCode() == null || v.getCode().isBlank() ? null : v.getCode().trim();
                String image = v.getImageUrl() == null || v.getImageUrl().isBlank() ? null : v.getImageUrl().trim();
                items.add(new Item(v.getId(), code, value, image));
            }
            return items;
        }
        if (req.getOptions() != null) {
            List<Item> items = new ArrayList<>();
            for (String o : req.getOptions()) {
                items.add(new Item(null, null, o == null ? "" : o.trim(), null));
            }
            return items;
        }
        return null;
    }

    private static void validateItems(List<Item> items) {
        Set<String> values = new HashSet<>();
        Set<String> codes = new HashSet<>();
        for (Item i : items) {
            if (i.value().isEmpty()) {
                throw BusinessException.badRequest(ErrorCode.ATTRIBUTE_VALUE_INVALID, "Tên giá trị không được trống");
            }
            if (!values.add(i.value())) {
                throw BusinessException.badRequest(ErrorCode.ATTRIBUTE_VALUE_INVALID,
                        "Giá trị bị lặp: " + i.value(), i.value());
            }
            String code = i.code() == null ? i.value() : i.code();
            if (!codes.add(code)) {
                throw BusinessException.badRequest(ErrorCode.ATTRIBUTE_VALUE_INVALID,
                        "Mã giá trị bị lặp: " + code, code);
            }
        }
    }

    /**
     * Đồng bộ danh sách giá trị với request theo id: có id -> sửa (mã bị khoá, đổi tên thì đổi theo ở SKU), khớp mã
     * hoặc tên (options cũ) -> coi là cùng giá trị, còn lại -> thêm mới. Giá trị đang có mà request bỏ đi thì xoá, trừ
     * khi SKU đang dùng (409 ATTRIBUTE_VALUE_IN_USE). Đồng thời giữ cột `options` (chuỗi) khớp.
     */
    private List<ProductAttributeValueEntity> reconcile(ProductAttributeEntity attribute,
                                                        List<ProductAttributeValueEntity> existing, List<Item> items) {
        Map<Long, ProductAttributeValueEntity> byId = new LinkedHashMap<>();
        Map<String, ProductAttributeValueEntity> byCode = new HashMap<>();
        Map<String, ProductAttributeValueEntity> byValue = new HashMap<>();
        for (ProductAttributeValueEntity v : existing) {
            byId.put(v.getId(), v);
            byCode.put(v.getCode(), v);
            byValue.put(v.getValue(), v);
        }

        // 1) Ghép từng dòng request với giá trị đã lưu (hoặc null = thêm mới).
        List<ProductAttributeValueEntity> matched = new ArrayList<>();
        Set<Long> keepIds = new HashSet<>();
        for (Item item : items) {
            ProductAttributeValueEntity m;
            if (item.id() != null) {
                m = byId.get(item.id());
                if (m == null) {
                    throw BusinessException.badRequest(ErrorCode.ATTRIBUTE_VALUE_INVALID,
                            "Không tìm thấy giá trị id=" + item.id() + " của thuộc tính", item.id());
                }
                if (item.code() != null && !item.code().equals(m.getCode())) {
                    throw BusinessException.badRequest(ErrorCode.ATTRIBUTE_VALUE_INVALID,
                            "Mã giá trị đã lưu không được đổi (" + m.getCode() + ")", item.id());
                }
            } else if (item.code() != null) {
                m = byCode.get(item.code());
            } else {
                m = byValue.get(item.value());
            }
            if (m != null && !keepIds.add(m.getId())) {
                throw BusinessException.badRequest(ErrorCode.ATTRIBUTE_VALUE_INVALID,
                        "Giá trị xuất hiện nhiều lần: " + item.value(), item.value());
            }
            matched.add(m);
        }

        // 2) Xoá giá trị bị bỏ khỏi request (chặn nếu SKU đang dùng) - xoá trước để nhường tên/mã cho dòng mới.
        for (ProductAttributeValueEntity old : existing) {
            if (!keepIds.contains(old.getId())) {
                Long used = skuValues.countByAttributeIdAndValue(attribute.getId(), old.getValue());
                if (used != null && used > 0) {
                    throw BusinessException.conflict(ErrorCode.ATTRIBUTE_VALUE_IN_USE,
                            "Giá trị \"" + old.getValue() + "\" đang được " + used + " SKU dùng, không thể xoá", old.getId());
                }
                valueRepo.delete(old);
            }
        }

        // 3) Sửa / thêm theo thứ tự request.
        List<ProductAttributeValueEntity> result = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            Item item = items.get(i);
            ProductAttributeValueEntity m = matched.get(i);
            if (m == null) {
                m = new ProductAttributeValueEntity();
                m.setAttributeId(attribute.getId());
                m.setCode(item.code() == null ? item.value() : item.code());
                m.setValue(item.value());
                m.setImageUrl(item.imageUrl());
                m.setSortOrder(i);
                valueRepo.create(m);
            } else {
                String oldValue = m.getValue();
                if (!oldValue.equals(item.value())) {
                    renameInSkus(attribute.getId(), oldValue, item.value());
                    m.setValue(item.value());
                }
                m.setImageUrl(item.imageUrl());
                m.setSortOrder(i);
                m.touch();
                valueRepo.update(m);
            }
            result.add(m);
        }

        attribute.setOptionsList(result.stream().map(ProductAttributeValueEntity::getValue).toList());
        attribute.touch();
        attributes.update(attribute);
        return result;
    }

    /** Đổi tên giá trị thì SKU đang dùng đổi theo (kể cả khi nằm trong chuỗi MULTISELECT "a||b"). */
    private void renameInSkus(Long attributeId, String oldValue, String newValue) {
        for (SkuAttributeValueEntity row : skuValues.findContaining(attributeId, oldValue)) {
            String[] parts = row.getValue().split("\\|\\|", -1);
            for (int i = 0; i < parts.length; i++) {
                if (parts[i].equals(oldValue)) {
                    parts[i] = newValue;
                }
            }
            row.setValue(String.join("||", parts));
            row.touch();
            skuValues.update(row);
        }
    }

    private static String parseType(String type) {
        String t = type == null ? "" : type.trim().toUpperCase(Locale.ROOT);
        if (!TYPES.contains(t)) {
            throw BusinessException.badRequest(ErrorCode.VALIDATION_FAILED,
                    "type không hợp lệ: " + type + " (TEXT | SELECT | MULTISELECT)", type);
        }
        return t;
    }

    private static String parseEnum(String value, Set<String> allowed, String field, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String v = value.trim().toUpperCase(Locale.ROOT);
        if (!allowed.contains(v)) {
            throw BusinessException.badRequest(ErrorCode.VALIDATION_FAILED,
                    field + " không hợp lệ: " + value + " (cho phép: " + new java.util.TreeSet<>(allowed) + ")", value);
        }
        return v;
    }
}
