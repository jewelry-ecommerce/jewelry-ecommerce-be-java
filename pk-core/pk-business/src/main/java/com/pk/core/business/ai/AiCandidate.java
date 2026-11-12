package com.pk.core.business.ai;

import java.math.BigDecimal;

/** Sản phẩm đã gắn SKU đại diện (giá bán, tồn) để xếp hạng. */
public record AiCandidate(
        long productId,
        Long categoryId,
        String categoryName,
        String name,
        String slug,
        String thumbnailUrl,
        String occasion,
        String material,
        Long skuId,
        String skuCode,
        BigDecimal price,
        int stock
) {
}
