package com.pk.core.business.service.impl;

import lombok.RequiredArgsConstructor;

import com.pk.core.business.repository.CategoryAttributeRepo;
import com.pk.core.business.repository.CategoryRepo;
import com.pk.core.business.repository.ProductAttributeRepo;
import com.pk.core.business.repository.ProductRepo;
import com.pk.core.business.service.CategoryService;
import com.pk.core.common.exception.BusinessException;
import com.pk.core.common.exception.ErrorCode;
import com.pk.core.common.exception.ResourceNotFoundException;
import com.pk.core.common.util.SlugUtil;
import com.pk.core.model.dto.request.AdminCategoryRequestDto;
import com.pk.core.model.dto.response.CategoryAttributeRefDto;
import com.pk.core.model.dto.response.CategoryResponseDto;
import com.pk.core.model.entity.CategoryAttributeEntity;
import com.pk.core.model.entity.CategoryEntity;
import com.pk.core.model.entity.ProductAttributeEntity;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    /** Tối đa 3 cấp (gốc = cấp 1). */
    static final int MAX_LEVEL = 3;
    private static final String ACTIVE = "ACTIVE";
    private static final String INACTIVE = "INACTIVE";

    private final CategoryRepo categories;
    private final ProductRepo products;
    private final CategoryAttributeRepo categoryAttributes;
    private final ProductAttributeRepo attributes;

    /** Ảnh chụp cả cây + thuộc tính gán trực tiếp, nạp 1 lần cho mỗi request (số danh mục nhỏ). */
    private record Tree(Map<Long, CategoryEntity> byId, Map<Long, List<Long>> directIds,
                        Map<Long, ProductAttributeEntity> attrs) {

        int levelOf(Long id) {
            int level = 0;
            Long cur = id;
            while (cur != null && level <= 10) {
                CategoryEntity c = byId.get(cur);
                if (c == null) {
                    break;
                }
                level++;
                cur = c.getParentId();
            }
            return level;
        }

        /** Tổ tiên từ gốc đến cha trực tiếp (không gồm chính nó). */
        List<CategoryEntity> ancestorsOf(Long id) {
            List<CategoryEntity> list = new ArrayList<>();
            CategoryEntity c = byId.get(id);
            Long cur = c == null ? null : c.getParentId();
            int guard = 0;
            while (cur != null && guard++ <= 10) {
                CategoryEntity p = byId.get(cur);
                if (p == null) {
                    break;
                }
                list.add(0, p);
                cur = p.getParentId();
            }
            return list;
        }

        /** Số cấp con bên dưới `id` (0 nếu là lá). */
        int heightBelow(Long id) {
            int max = 0;
            for (CategoryEntity c : byId.values()) {
                if (id.equals(c.getParentId())) {
                    max = Math.max(max, 1 + heightBelow(c.getId()));
                }
            }
            return max;
        }

        List<Long> inheritedIds(Long id) {
            Set<Long> out = new LinkedHashSet<>();
            for (CategoryEntity a : ancestorsOf(id)) {
                out.addAll(directIds.getOrDefault(a.getId(), List.of()));
            }
            return new ArrayList<>(out);
        }

        boolean publiclyVisible(CategoryEntity c) {
            if (!c.isActive()) {
                return false;
            }
            for (CategoryEntity a : ancestorsOf(c.getId())) {
                if (!a.isActive()) {
                    return false;
                }
            }
            return true;
        }
    }

    private Tree loadTree() {
        Map<Long, CategoryEntity> byId = new HashMap<>();
        for (CategoryEntity c : categories.findAll(Sort.unsorted())) {
            if (c.getDeletedDate() == null) {
                byId.put(c.getId(), c);
            }
        }
        Map<Long, List<Long>> direct = new HashMap<>();
        for (CategoryAttributeEntity ca : categoryAttributes.findAll(Sort.unsorted())) {
            direct.computeIfAbsent(ca.getCategoryId(), k -> new ArrayList<>()).add(ca.getAttributeId());
        }
        Map<Long, ProductAttributeEntity> attrs = new HashMap<>();
        for (ProductAttributeEntity a : attributes.findAll(Sort.unsorted())) {
            attrs.put(a.getId(), a);
        }
        return new Tree(byId, direct, attrs);
    }

    private CategoryResponseDto toDto(CategoryEntity c, Tree tree) {
        CategoryResponseDto dto = CategoryResponseDto.from(c);
        dto.setLevel(Math.max(1, tree.levelOf(c.getId())));
        List<CategoryEntity> ancestors = tree.ancestorsOf(c.getId());
        dto.setAncestorCategoryIds(ancestors.stream().map(a -> String.valueOf(a.getId())).toList());
        dto.setAncestorCategorySlugs(ancestors.stream().map(CategoryEntity::getSlug).toList());

        List<Long> direct = new ArrayList<>(tree.directIds().getOrDefault(c.getId(), List.of()));
        List<Long> inherited = tree.inheritedIds(c.getId());
        inherited.removeAll(direct);
        Set<Long> effective = new LinkedHashSet<>(inherited);
        effective.addAll(direct);
        dto.setDirectAttributeIds(direct);
        dto.setInheritedAttributeIds(inherited);
        dto.setEffectiveAttributeIds(new ArrayList<>(effective));
        dto.setDirectAttributes(refs(direct, tree));
        dto.setInheritedAttributes(refs(inherited, tree));
        List<CategoryAttributeRefDto> effectiveRefs = refs(new ArrayList<>(effective), tree);
        dto.setEffectiveAttributes(effectiveRefs);
        dto.setAllowedAttributeIds(effective.stream().map(String::valueOf).toList());
        dto.setAllowedFilterAttributes(effectiveRefs);
        return dto;
    }

    private static List<CategoryAttributeRefDto> refs(List<Long> ids, Tree tree) {
        List<CategoryAttributeRefDto> out = new ArrayList<>();
        for (Long id : ids) {
            ProductAttributeEntity a = tree.attrs().get(id);
            if (a != null) {
                out.add(CategoryAttributeRefDto.from(a));
            }
        }
        return out;
    }

    @Transactional(readOnly = true)
    @Override
    public List<CategoryResponseDto> findAllActive() {
        Tree tree = loadTree();
        return tree.byId().values().stream()
                .filter(tree::publiclyVisible)
                .sorted(java.util.Comparator.comparingInt(CategoryEntity::getSortOrder)
                        .thenComparing(CategoryEntity::getName, java.util.Comparator.nullsLast(String::compareTo)))
                .map(c -> toDto(c, tree))
                .toList();
    }

    @Transactional(readOnly = true)
    @Override
    public List<CategoryResponseDto> findAllForAdmin() {
        Tree tree = loadTree();
        return tree.byId().values().stream()
                .sorted(java.util.Comparator.comparing(CategoryEntity::getId))
                .map(c -> toDto(c, tree))
                .toList();
    }

    @Transactional
    @Override
    public CategoryResponseDto create(AdminCategoryRequestDto req) {
        String slug = resolveSlug(req.getSlug(), req.getName());
        if (categories.findBySlug(slug) != null) {
            throw BusinessException.conflict(ErrorCode.DUPLICATE_SLUG, "Đường dẫn (slug) đã tồn tại: " + slug, slug);
        }
        String code = blank(req.getCode()) ? slug : req.getCode().trim();
        if (categories.findByCode(code) != null) {
            throw BusinessException.conflict(ErrorCode.DUPLICATE_CODE, "Mã danh mục đã tồn tại: " + code, code);
        }
        Tree tree = loadTree();
        validateParent(tree, null, req.getParentId());
        List<Long> direct = validateAttributes(tree, req.getParentId(), req.getDirectAttributeIds());

        CategoryEntity c = new CategoryEntity(req.getName().trim(), slug, req.getDescription());
        c.setCode(code);
        c.setImageUrl(req.getImageUrl());
        c.setParentId(req.getParentId());
        c.setSortOrder(req.getSortOrder());
        c.setActive(ACTIVE.equals(parseStatus(req.getStatus(), ACTIVE)));
        categories.create(c);
        saveDirect(c.getId(), direct);
        return toDto(c, loadTree());
    }

    @Transactional
    @Override
    public CategoryResponseDto update(Long categoryId, AdminCategoryRequestDto req) {
        CategoryEntity c = categories.findOne(categoryId);
        if (c == null) {
            throw new ResourceNotFoundException("Danh mục", "Category", categoryId);
        }
        String slug = resolveSlug(req.getSlug(), req.getName());
        if (!slug.equals(c.getSlug()) && categories.findBySlug(slug) != null) {
            throw BusinessException.conflict(ErrorCode.DUPLICATE_SLUG, "Đường dẫn (slug) đã tồn tại: " + slug, slug);
        }
        if (!blank(req.getCode())) {
            String code = req.getCode().trim();
            CategoryEntity other = categories.findByCode(code);
            if (other != null && !other.getId().equals(categoryId)) {
                throw BusinessException.conflict(ErrorCode.DUPLICATE_CODE, "Mã danh mục đã tồn tại: " + code, code);
            }
            c.setCode(code);
        } else if (blank(c.getCode())) {
            c.setCode(slug);
        }
        Tree tree = loadTree();
        validateParent(tree, categoryId, req.getParentId());
        List<Long> direct = req.getDirectAttributeIds() == null ? null
                : validateAttributes(tree, req.getParentId(), req.getDirectAttributeIds());

        c.setName(req.getName().trim());
        c.setSlug(slug);
        c.setDescription(req.getDescription());
        c.setImageUrl(req.getImageUrl());
        c.setParentId(req.getParentId());
        c.setSortOrder(req.getSortOrder());
        if (req.getStatus() != null) {
            c.setActive(ACTIVE.equals(parseStatus(req.getStatus(), ACTIVE)));
        }
        c.touch();
        categories.update(c);
        if (direct != null) {
            categoryAttributes.deleteByCategoryId(categoryId);
            saveDirect(categoryId, direct);
        }
        return toDto(c, loadTree());
    }

    @Transactional
    @Override
    public void delete(Long categoryId) {
        CategoryEntity c = categories.findOne(categoryId);
        if (c == null) {
            throw new ResourceNotFoundException("Danh mục", "Category", categoryId);
        }
        Long children = categories.countChildren(categoryId);
        if (children != null && children > 0) {
            throw BusinessException.conflict(ErrorCode.CATEGORY_IN_USE,
                    "Danh mục còn " + children + " danh mục con, hãy chuyển hoặc xoá chúng trước", categoryId);
        }
        Long used = products.countByCategoryId(categoryId);
        if (used != null && used > 0) {
            throw BusinessException.conflict(ErrorCode.CATEGORY_IN_USE,
                    "Danh mục còn " + used + " sản phẩm, hãy chuyển sang danh mục khác trước", categoryId);
        }
        categoryAttributes.deleteByCategoryId(categoryId);
        categories.delete(c);
    }

    // ---------------------------------------------------------------- validate

    /** Cha phải tồn tại, không tự làm cha của chính nó, không tạo vòng, và tổng số cấp (kể cả cây con) ≤ 3. */
    private void validateParent(Tree tree, Long selfId, Long parentId) {
        if (parentId == null) {
            return;
        }
        if (parentId.equals(selfId)) {
            throw BusinessException.badRequest(ErrorCode.CATEGORY_PARENT_INVALID,
                    "Danh mục không thể là cha của chính nó", parentId);
        }
        CategoryEntity parent = tree.byId().get(parentId);
        if (parent == null) {
            throw BusinessException.badRequest(ErrorCode.CATEGORY_PARENT_INVALID,
                    "Không tìm thấy danh mục cha id=" + parentId, parentId);
        }
        if (selfId != null) {
            for (CategoryEntity a : tree.ancestorsOf(parentId)) {
                if (selfId.equals(a.getId())) {
                    throw BusinessException.badRequest(ErrorCode.CATEGORY_PARENT_INVALID,
                            "Không thể chuyển danh mục vào chính cây con của nó (vòng lặp)", parentId);
                }
            }
        }
        int below = selfId == null ? 0 : tree.heightBelow(selfId);
        int deepest = tree.levelOf(parentId) + 1 + below;
        if (deepest > MAX_LEVEL) {
            throw BusinessException.badRequest(ErrorCode.CATEGORY_PARENT_INVALID,
                    "Cây danh mục tối đa " + MAX_LEVEL + " cấp (sẽ thành " + deepest + " cấp)", parentId);
        }
    }

    /** Thuộc tính trực tiếp: phải tồn tại, không trùng nhau, không trùng thuộc tính đã kế thừa từ cha. */
    private List<Long> validateAttributes(Tree tree, Long parentId, List<Long> requested) {
        List<Long> out = new ArrayList<>();
        if (requested == null) {
            return out;
        }
        Set<Long> inherited = parentId == null ? Set.of() : new LinkedHashSet<>(parentEffective(tree, parentId));
        Set<Long> seen = new LinkedHashSet<>();
        for (Long id : requested) {
            if (id == null || !seen.add(id)) {
                continue;
            }
            if (!tree.attrs().containsKey(id)) {
                throw BusinessException.badRequest(ErrorCode.CATEGORY_ATTRIBUTE_INVALID,
                        "Không tìm thấy thuộc tính id=" + id, id);
            }
            if (inherited.contains(id)) {
                throw BusinessException.badRequest(ErrorCode.CATEGORY_ATTRIBUTE_INVALID,
                        "Thuộc tính id=" + id + " đã được kế thừa từ danh mục cha, không gán trùng", id);
            }
            out.add(id);
        }
        return out;
    }

    /** Thuộc tính hiệu lực của danh mục cha = của tổ tiên + trực tiếp của cha. */
    private static List<Long> parentEffective(Tree tree, Long parentId) {
        Set<Long> all = new LinkedHashSet<>(tree.inheritedIds(parentId));
        all.addAll(tree.directIds().getOrDefault(parentId, List.of()));
        return new ArrayList<>(all);
    }

    private void saveDirect(Long categoryId, List<Long> ids) {
        for (Long attributeId : ids) {
            categoryAttributes.create(new CategoryAttributeEntity(categoryId, attributeId));
        }
    }

    private static String parseStatus(String status, String fallback) {
        if (blank(status)) {
            return fallback;
        }
        String s = status.trim().toUpperCase(java.util.Locale.ROOT);
        if (!ACTIVE.equals(s) && !INACTIVE.equals(s)) {
            throw BusinessException.badRequest(ErrorCode.VALIDATION_FAILED,
                    "status không hợp lệ: " + status + " (ACTIVE | INACTIVE)", status);
        }
        return s;
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    private static String resolveSlug(String slug, String name) {
        return blank(slug) ? SlugUtil.slugify(name) : SlugUtil.slugify(slug);
    }
}
