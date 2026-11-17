package com.pk.core.business.ai;

/** Cách một câu chat được xử lý. PRODUCT mới được query catalog. */
public record AssistantRoute(String kind, String keyword, String slug) {

    public static final String GREETING = "GREETING";
    public static final String SHOP = "SHOP";
    public static final String PRODUCT = "PRODUCT";

    public boolean looksUpProducts() {
        return PRODUCT.equals(kind);
    }
}
