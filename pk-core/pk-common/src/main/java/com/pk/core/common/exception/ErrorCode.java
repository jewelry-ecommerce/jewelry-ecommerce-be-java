package com.pk.core.common.exception;

/** Mã lỗi trả về cho FE (trường `code` trong BaseRes.errors[], xem pk-common/common/web/ErrorDetailRes).
 * Gom một chỗ để không gõ chuỗi rải rác. */
public final class ErrorCode {

    public static final String ACCOUNT_DISABLED = "ACCOUNT_DISABLED";
    public static final String BADGE_FLOW_LOCKED = "BADGE_FLOW_LOCKED";
    public static final String BADGE_IN_USE = "BADGE_IN_USE";
    public static final String BADGE_SCOPE_INVALID = "BADGE_SCOPE_INVALID";
    public static final String CAROUSEL_SOURCE_INVALID = "CAROUSEL_SOURCE_INVALID";
    public static final String CMS_SCHEDULE_INVALID = "CMS_SCHEDULE_INVALID";
    public static final String APPROVAL_INVALID = "APPROVAL_INVALID";
    public static final String APPROVAL_REQUIRED = "APPROVAL_REQUIRED";
    public static final String PRODUCT_PENDING_APPROVAL = "PRODUCT_PENDING_APPROVAL";
    public static final String BELOW_RESERVED = "BELOW_RESERVED";
    public static final String CART_EMPTY = "CART_EMPTY";
    public static final String CART_ITEM_INVALID = "CART_ITEM_INVALID";
    public static final String ATTRIBUTE_IN_USE = "ATTRIBUTE_IN_USE";
    public static final String ATTRIBUTE_VALUE_INVALID = "ATTRIBUTE_VALUE_INVALID";
    public static final String ATTRIBUTE_VALUE_IN_USE = "ATTRIBUTE_VALUE_IN_USE";
    public static final String CATEGORY_ATTRIBUTE_INVALID = "CATEGORY_ATTRIBUTE_INVALID";
    public static final String CATEGORY_EXISTS = "CATEGORY_EXISTS";
    public static final String CATEGORY_IN_USE = "CATEGORY_IN_USE";
    public static final String CATEGORY_PARENT_INVALID = "CATEGORY_PARENT_INVALID";
    public static final String CONCURRENT_UPDATE = "CONCURRENT_UPDATE";
    public static final String DATA_CONFLICT = "DATA_CONFLICT";
    public static final String DUPLICATE_CODE = "DUPLICATE_CODE";
    public static final String DUPLICATE_SKU = "DUPLICATE_SKU";
    public static final String DUPLICATE_SLUG = "DUPLICATE_SLUG";
    public static final String DUPLICATE_VARIATION = "DUPLICATE_VARIATION";
    public static final String EMAIL_TAKEN = "EMAIL_TAKEN";
    public static final String FORBIDDEN = "FORBIDDEN";
    public static final String INSUFFICIENT_STOCK = "INSUFFICIENT_STOCK";
    public static final String INTERNAL_ERROR = "INTERNAL_ERROR";
    public static final String INVALID_CREDENTIALS = "INVALID_CREDENTIALS";
    public static final String INVALID_NAME = "INVALID_NAME";
    public static final String INVALID_QUANTITY = "INVALID_QUANTITY";
    public static final String INVALID_STATUS_TRANSITION = "INVALID_STATUS_TRANSITION";
    public static final String NOT_FOUND = "NOT_FOUND";
    public static final String NO_ACTIVE_SKU = "NO_ACTIVE_SKU";
    public static final String OTP_EXPIRED = "OTP_EXPIRED";
    public static final String OTP_INVALID = "OTP_INVALID";
    public static final String OTP_RATE_LIMITED = "OTP_RATE_LIMITED";
    public static final String OTP_TOO_MANY_ATTEMPTS = "OTP_TOO_MANY_ATTEMPTS";
    public static final String NEGATIVE_STOCK = "NEGATIVE_STOCK";
    public static final String ORDER_ALREADY_CANCELLED = "ORDER_ALREADY_CANCELLED";
    public static final String ORDER_STATUS_TRANSITION_INVALID = "ORDER_STATUS_TRANSITION_INVALID";
    public static final String PAYMENT_ALREADY_PROCESSED = "PAYMENT_ALREADY_PROCESSED";
    public static final String PAYMENT_AMOUNT_MISMATCH = "PAYMENT_AMOUNT_MISMATCH";
    public static final String PAYMENT_SIGNATURE_INVALID = "PAYMENT_SIGNATURE_INVALID";
    public static final String PHONE_TAKEN = "PHONE_TAKEN";
    public static final String REFRESH_EXPIRED = "REFRESH_EXPIRED";
    public static final String REFRESH_INVALID = "REFRESH_INVALID";
    public static final String REFRESH_REUSED = "REFRESH_REUSED";
    public static final String REGISTRATION_TOKEN_INVALID = "REGISTRATION_TOKEN_INVALID";
    public static final String SKU_EXISTS = "SKU_EXISTS";
    public static final String STOCK_ADJUSTMENT_INVALID = "STOCK_ADJUSTMENT_INVALID";
    public static final String STOCK_CONFLICT = "STOCK_CONFLICT";
    public static final String IDEMPOTENCY_KEY_REUSED = "IDEMPOTENCY_KEY_REUSED";
    public static final String IDEMPOTENCY_KEY_INVALID = "IDEMPOTENCY_KEY_INVALID";
    public static final String UNAUTHORIZED = "UNAUTHORIZED";
    public static final String VALIDATION_FAILED = "VALIDATION_FAILED";
    public static final String VOUCHER_EXPIRED = "VOUCHER_EXPIRED";
    public static final String VOUCHER_MIN_ORDER_NOT_MET = "VOUCHER_MIN_ORDER_NOT_MET";
    public static final String VOUCHER_NOT_STARTED = "VOUCHER_NOT_STARTED";
    public static final String VOUCHER_USAGE_LIMIT_REACHED = "VOUCHER_USAGE_LIMIT_REACHED";

    private ErrorCode() {
    }
}
