package com.pk.core.identity.service;

/**
 * Gửi mã OTP sau khi tạo. UI vẫn theo SĐT; triển khai hiện tại forward về mail cố định (Resend)
 * hoặc chỉ log khi thiếu cấu hình — không SMS.
 */
public interface OtpSender {

    /** @param purpose PhoneOtpEntity.REGISTER hoặc PhoneOtpEntity.RESET_PASSWORD */
    void send(String phone, String code, String purpose);
}
