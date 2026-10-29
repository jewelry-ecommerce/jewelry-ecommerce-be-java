package com.pk.core.business.service.impl;

import com.pk.core.model.constant.UrlConstant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;

/**
 * Link tải file riêng tư (CSV import/export) có chữ ký HMAC-SHA256 và hạn dùng: `...?exp=<epoch giây>&sig=<hex>`.
 * Khoá ký lấy từ `app.upload.signing-secret` (UPLOAD_SIGNING_SECRET). Để trống thì sinh khoá ngẫu nhiên mỗi lần khởi động -
 * an toàn (fail-closed) nhưng link cũ hết hiệu lực khi máy chủ khởi động lại; admin chỉ cần mở lại job để lấy link mới.
 */
@Slf4j
@Component
public class PrivateFileLinks {

    private static final long MIN_TTL = 60;
    private static final long MAX_TTL = 86_400;

    private final byte[] secret;
    private final long ttlSeconds;
    private final String baseUrl;

    public PrivateFileLinks(@Value("${app.upload.signing-secret:}") String signingSecret,
                            @Value("${app.upload.link-ttl-seconds:600}") long ttlSeconds, UploadStorage storage) {
        if (signingSecret == null || signingSecret.isBlank()) {
            this.secret = new byte[32];
            new SecureRandom().nextBytes(this.secret);
            log.warn("UPLOAD_SIGNING_SECRET trống: dùng khoá ngẫu nhiên, link tải CSV sẽ hết hiệu lực khi khởi động lại");
        } else {
            this.secret = signingSecret.getBytes(StandardCharsets.UTF_8);
        }
        this.ttlSeconds = Math.max(MIN_TTL, Math.min(MAX_TTL, ttlSeconds));
        this.baseUrl = storage.baseUrl();
    }

    /** Link tải file của một job import/export, hiệu lực {@code ttlSeconds} giây kể từ bây giờ. */
    public String importExportUrl(long jobId) {
        long exp = Instant.now().getEpochSecond() + ttlSeconds;
        return baseUrl + UrlConstant.Common.API + UrlConstant.Common.VERSION + UrlConstant.Storefront.PRIVATE_FILES
                + "/import-export/" + jobId + "?exp=" + exp + "&sig=" + sign(jobId, exp);
    }

    /** Đúng chữ ký và chưa quá hạn. */
    public boolean verify(long jobId, Long exp, String sig) {
        if (exp == null || sig == null || exp < Instant.now().getEpochSecond()) {
            return false;
        }
        byte[] expected = sign(jobId, exp).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, sig.getBytes(StandardCharsets.UTF_8));
    }

    private String sign(long jobId, long exp) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(("import-export:" + jobId + ":" + exp).getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Không ký được link tải file", ex);
        }
    }
}
