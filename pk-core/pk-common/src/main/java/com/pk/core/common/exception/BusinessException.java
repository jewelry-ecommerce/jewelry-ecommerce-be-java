package com.pk.core.common.exception;

/**
 * Lỗi nghiệp vụ có chủ đích (email trùng, hết hàng...). Mang theo HTTP status và mã lỗi
 * để FE hiển thị/đa ngôn ngữ. status là int để pk-common không phụ thuộc spring-web.
 * `message` là văn bản mặc định (tiếng Việt, dùng khi không tra được bản dịch ở pk-business);
 * `args` là tham số để MessageSource (pk-business) dịch message theo `code` sang ngôn ngữ khác.
 */
public class BusinessException extends RuntimeException {

    private final int status;
    private final String code;
    private final transient Object[] args;
    /** Danh sách đối tượng liên quan (VD header trùng lịch) -> BaseRes.dependencies cho FE. */
    private transient java.util.List<java.util.Map<String, Object>> dependencies;
    /** Field gây lỗi (tuỳ chọn). */
    private transient String field;

    public BusinessException(int status, String code, String message) {
        this(status, code, message, (Object[]) null);
    }

    public BusinessException(int status, String code, String message, Object... args) {
        super(message);
        this.status = status;
        this.code = code;
        this.args = args;
    }

    public static BusinessException badRequest(String code, String message) {
        return new BusinessException(400, code, message);
    }

    public static BusinessException badRequest(String code, String message, Object... args) {
        return new BusinessException(400, code, message, args);
    }

    public static BusinessException unauthorized(String code, String message) {
        return new BusinessException(401, code, message);
    }

    public static BusinessException conflict(String code, String message) {
        return new BusinessException(409, code, message);
    }

    /** 403: đã xác thực đúng danh tính (login thành công) nhưng không đủ quyền - VD tài khoản không có
     * role ADMIN mà gọi /admin/auth/login (thêm 2026-09-29 cho AuthService.loginAdmin). */
    public static BusinessException forbidden(String code, String message) {
        return new BusinessException(403, code, message);
    }

    public static BusinessException conflict(String code, String message, Object... args) {
        return new BusinessException(409, code, message, args);
    }

    /** 422: yêu cầu hợp lệ về cú pháp nhưng vi phạm ràng buộc nghiệp vụ không xử lý được (VD điều
     * chỉnh tồn kho khiến on_hand âm) - thêm 2026-09-29 cho Admin Inventory mục M
     * (POST /admin/inventory/adjustments, spec yêu cầu đúng mã 422). */
    public static BusinessException unprocessable(String code, String message, Object... args) {
        return new BusinessException(422, code, message, args);
    }

    /** 429: gọi quá nhanh/quá nhiều lần (VD gửi lại OTP trước hết khoảng chờ, nhập sai OTP quá số lần) -
     * thêm 2026-10-06 cho luồng OTP (spec FE ghi 429 ở send-otp). */
    public static BusinessException tooManyRequests(String code, String message, Object... args) {
        return new BusinessException(429, code, message, args);
    }

    public int getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public Object[] getArgs() {
        return args;
    }

    public BusinessException withDependencies(java.util.List<java.util.Map<String, Object>> deps) {
        this.dependencies = deps;
        return this;
    }

    public java.util.List<java.util.Map<String, Object>> getDependencies() {
        return dependencies;
    }

    /** Đường dẫn field gây lỗi (vd `config.items[0].imageUrl`) -> BaseRes.errors[].field cho FE. */
    public BusinessException withField(String field) {
        this.field = field;
        return this;
    }

    public String getField() {
        return field;
    }
}
