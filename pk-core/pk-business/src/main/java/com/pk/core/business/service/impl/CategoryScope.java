package com.pk.core.business.service.impl;

import com.pk.core.model.entity.CategoryEntity;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Cây danh mục trong bộ nhớ cho storefront: danh mục hiển thị công khai + mở rộng slug sang cả danh mục con. */
final class CategoryScope {

    private final Map<Long, CategoryEntity> byId = new HashMap<>();

    CategoryScope(Collection<CategoryEntity> all) {
        for (CategoryEntity c : all) {
            if (c.getDeletedDate() == null) {
                byId.put(c.getId(), c);
            }
        }
    }

    /** Danh mục tồn tại, ACTIVE và mọi tổ tiên đều ACTIVE. */
    boolean visible(Long id) {
        Long cur = id;
        int guard = 0;
        while (cur != null && guard++ <= 10) {
            CategoryEntity c = byId.get(cur);
            if (c == null || !c.isActive()) {
                return false;
            }
            cur = c.getParentId();
        }
        return cur == null;
    }

    /** Id của các slug + toàn bộ danh mục con (chỉ lấy danh mục hiển thị). Slug không tồn tại -> bỏ qua. */
    Set<Long> idsForSlugs(Collection<String> slugs) {
        Set<Long> roots = new HashSet<>();
        for (CategoryEntity c : byId.values()) {
            if (slugs.contains(c.getSlug())) {
                roots.add(c.getId());
            }
        }
        Set<Long> out = new HashSet<>();
        for (CategoryEntity c : byId.values()) {
            Long cur = c.getId();
            int guard = 0;
            while (cur != null && guard++ <= 10) {
                if (roots.contains(cur)) {
                    if (visible(c.getId())) {
                        out.add(c.getId());
                    }
                    break;
                }
                CategoryEntity n = byId.get(cur);
                cur = n == null ? null : n.getParentId();
            }
        }
        return out;
    }
}
