package com.pk.core.business.service;

import java.math.BigDecimal;
import java.util.List;

/**
 * Điều kiện tìm sản phẩm storefront. `categorySlugs` gộp với `categorySlug` (mỗi slug bao gồm cả danh mục con);
 * `attributeValueIds` là id giá trị thuộc tính (product_attribute_values.id): cùng thuộc tính = HOẶC, khác thuộc tính = VÀ.
 * Giá lọc theo GIÁ HIỂN THỊ (đã trừ khuyến mãi) chứ không phải base_price.
 */
public record ProductSearchQuery(String categorySlug, List<String> categorySlugs, String collectionSlug, String keyword,
                                 String material, BigDecimal minPrice, BigDecimal maxPrice,
                                 List<Long> attributeValueIds, Boolean inStock, String sort, int page, int take) {

    public ProductSearchQuery withoutAttributes() {
        return new ProductSearchQuery(categorySlug, categorySlugs, collectionSlug, keyword, material, minPrice,
                maxPrice, List.of(), inStock, sort, page, take);
    }
}
