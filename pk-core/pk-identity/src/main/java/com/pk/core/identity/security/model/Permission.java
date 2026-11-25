package com.pk.core.identity.security.model;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Quyền chi tiết. Role gom các quyền; token mang cả roles lẫn permissions
 * để dùng @PreAuthorize("hasAuthority('PRODUCT_WRITE')") khi cần kiểm soát mịn hơn role.
 */
public enum Permission {
    PRODUCT_READ,
    PRODUCT_WRITE,
    INVENTORY_MANAGE,
    ORDER_READ_OWN,
    ORDER_MANAGE,
    USER_MANAGE;

    public static Set<Permission> forRole(String role) {
        return switch (role) {
            case "ROOT", "ADMIN" -> EnumSet.allOf(Permission.class);
            case "CATALOG_MANAGER" -> EnumSet.of(PRODUCT_READ, PRODUCT_WRITE, INVENTORY_MANAGE);
            case "ORDER_MANAGER" -> EnumSet.of(PRODUCT_READ, ORDER_MANAGE);
            case "CUSTOMER" -> EnumSet.of(PRODUCT_READ, ORDER_READ_OWN);
            default -> EnumSet.noneOf(Permission.class);
        };
    }

    public static Set<Permission> forRoles(List<String> roles) {
        Set<Permission> result = EnumSet.noneOf(Permission.class);
        roles.forEach(r -> result.addAll(forRole(r)));
        return result;
    }
}
