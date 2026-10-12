package com.pk.core.business.service;

import java.util.Collection;
import java.util.Set;

/**
 * Phạm vi đặt trước của cấu hình đang hiệu lực. type ALL (cấu hình cũ): mọi SKU, nhưng chỉ HẾT HÀNG mới là hàng đặt trước.
 * type SKU | PRODUCT | CATEGORY | COLLECTION: chỉ SKU nằm trong ids là hàng đặt trước (kể cả khi còn hàng, để hiện nhãn).
 */
public record PreOrderScope(boolean enabled, String type, Set<Long> ids) {

    public static final String ALL = "ALL";
    public static final String SKU = "SKU";
    public static final String PRODUCT = "PRODUCT";
    public static final String CATEGORY = "CATEGORY";
    public static final String COLLECTION = "COLLECTION";

    public static PreOrderScope off() {
        return new PreOrderScope(false, ALL, Set.of());
    }

    public static PreOrderScope all(boolean enabled) {
        return new PreOrderScope(enabled, ALL, Set.of());
    }

    public boolean isAll() {
        return type == null || ALL.equals(type);
    }

    /** SKU có thuộc phạm vi không (ALL = luôn đúng). Không xét tồn kho. */
    public boolean covers(Long skuId, Long productId, Long categoryId, Collection<Long> collectionIds) {
        if (!enabled) {
            return false;
        }
        if (isAll()) {
            return true;
        }
        return switch (type) {
            case SKU -> skuId != null && ids.contains(skuId);
            case PRODUCT -> productId != null && ids.contains(productId);
            case CATEGORY -> categoryId != null && ids.contains(categoryId);
            case COLLECTION -> collectionIds != null && collectionIds.stream().anyMatch(ids::contains);
            default -> false;
        };
    }

    /** SKU này là hàng đặt trước (để hiện nhãn PRE_ORDER): ALL -> chỉ khi hết hàng; phạm vi riêng -> thuộc phạm vi. */
    public boolean isPreOrderItem(boolean outOfStock, Long skuId, Long productId, Long categoryId,
                                  Collection<Long> collectionIds) {
        if (!enabled) {
            return false;
        }
        return isAll() ? outOfStock : covers(skuId, productId, categoryId, collectionIds);
    }
}
