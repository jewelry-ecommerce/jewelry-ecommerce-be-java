package com.pk.core.common.web;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Bọc mọi response trả ra API (cả thành công lẫn lỗi) - phỏng theo BaseRes của dự án tham khảo
 * boxchatSocket (core/basereponse/BaseRes.java), dùng chung cho AbstractRest/RestSuccessHandle/
 * RestErrorHandle ở pk-business và pk-identity. Dùng Instant thay cho Date của bản gốc để khớp
 * quy ước thời gian đã dùng sẵn trong dự án (trước đây ở ApiError).
 */
@Getter
@Setter
@NoArgsConstructor
public class BaseRes {

    /** status: 200/400/500... - bắt buộc, khớp status HTTP thật trả về. */
    private int status;

    /** message: nội dung lỗi/thành công tổng quát (đã dịch theo i18n nếu có) - bắt buộc. */
    private String message;

    /** title: tiêu đề ngắn phân loại (VD "Thành công", "Lỗi nghiệp vụ", "Dữ liệu không hợp lệ"). */
    private String title;

    /** time: thời điểm xử lý xong request. */
    private Instant time;

    /** errors: chi tiết từng lỗi (validate theo field, hoặc 1 phần tử cho lỗi nghiệp vụ) - optional. */
    private List<ErrorDetailRes> errors;

    /** dependencies: đối tượng liên quan tới lỗi (VD conflictingHeaders) - optional, null thì không xuất hiện. */
    private List<java.util.Map<String, Object>> dependencies;

    /** data: dữ liệu trả về khi thành công. */
    private Object data;

    public BaseRes(int status, String message, String title) {
        this.status = status;
        this.message = message;
        this.title = title;
        this.time = Instant.now();
    }

    public BaseRes(int status, String message, String title, Object data) {
        this.status = status;
        this.message = message;
        this.title = title;
        this.data = data;
        this.time = Instant.now();
    }

    public void addError(ErrorDetailRes errorDetailRes) {
        if (this.errors == null) {
            this.errors = new ArrayList<>();
        }
        this.errors.add(errorDetailRes);
    }
}
