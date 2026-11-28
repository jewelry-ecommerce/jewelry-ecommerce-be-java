package com.pk.core.identity.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pk.core.identity.service.OtpSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OTP demo kiểu Seva: UI vẫn theo SĐT, không SMS.
 * Có {@code app.otp.forward-email} + {@code app.otp.resend-api-key} → gửi OTP về 1 hộp mail cố định (Resend free).
 * Thiếu cấu hình → chỉ ghi log (dev).
 */
@Component
public class LogOtpSender implements OtpSender {

    private static final Logger log = LoggerFactory.getLogger(LogOtpSender.class);
    private static final String RESEND_URL = "https://api.resend.com/emails";

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    @Value("${app.otp.forward-email:}")
    private String forwardEmail;

    @Value("${app.otp.resend-api-key:}")
    private String resendApiKey;

    @Value("${app.otp.resend-from:Jewelry OTP <onboarding@resend.dev>}")
    private String resendFrom;

    public LogOtpSender(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void send(String phone, String code, String purpose) {
        log.info("[OTP] {} cho {}: {}", purpose, phone, code);
        if (!canForwardByEmail()) {
            log.info("[OTP] Chưa cấu hình OTP_FORWARD_EMAIL + RESEND_API_KEY — chỉ log, không gửi mail");
            return;
        }
        sendViaResend(phone, code, purpose);
    }

    private boolean canForwardByEmail() {
        return forwardEmail != null && !forwardEmail.isBlank()
                && resendApiKey != null && !resendApiKey.isBlank();
    }

    private void sendViaResend(String phone, String code, String purpose) {
        try {
            String body = objectMapper.writeValueAsString(buildPayload(phone, code, purpose));
            HttpRequest request = HttpRequest.newBuilder(URI.create(RESEND_URL))
                    .timeout(Duration.ofSeconds(15))
                    .header("Authorization", "Bearer " + resendApiKey.trim())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                log.info("[OTP] Đã gửi mail tới {} (phone={}, purpose={})", forwardEmail.trim(), phone, purpose);
                return;
            }
            log.error("[OTP] Resend lỗi HTTP {}: {}", response.statusCode(), response.body());
            throw new IllegalStateException("Gửi OTP qua email thất bại (HTTP " + response.statusCode() + ")");
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("[OTP] Resend exception phone={}", phone, ex);
            throw new IllegalStateException("Gửi OTP qua email thất bại", ex);
        }
    }

    private Map<String, Object> buildPayload(String phone, String code, String purpose) {
        String label = purposeLabel(purpose);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("from", resendFrom);
        payload.put("to", List.of(forwardEmail.trim()));
        payload.put("subject", "[Jewelry] OTP " + label + " — " + phone);
        payload.put("text", "SĐT: " + phone + "\nMục đích: " + label + "\nMã OTP: " + code + "\n\n(Demo: OTP forward về hộp mail cố định, không gửi SMS.)");
        payload.put("html", "<p><b>SĐT:</b> " + phone + "</p><p><b>Mục đích:</b> " + label
                + "</p><p><b>Mã OTP:</b> <code style=\"font-size:20px\">" + code + "</code></p>"
                + "<p style=\"color:#667085\">Demo đồ án: OTP forward về mail cố định (không SMS).</p>");
        return payload;
    }

    private static String purposeLabel(String purpose) {
        if ("REGISTER".equals(purpose)) {
            return "đăng ký";
        }
        if ("RESET_PASSWORD".equals(purpose)) {
            return "quên mật khẩu";
        }
        if ("ADMIN_INVITE".equals(purpose)) {
            return "tạo tài khoản admin";
        }
        return purpose == null ? "OTP" : purpose;
    }
}
