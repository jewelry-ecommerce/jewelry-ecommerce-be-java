package com.pk.core.business.storage;

import com.pk.core.common.exception.BusinessException;
import com.pk.core.common.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.io.InputStream;

@Component
public class UploadMediaValidator {
    private static final int SIGNATURE_BYTES = 16;
    private final long maxBytes;

    public UploadMediaValidator(@Value("${app.upload.max-bytes:10485760}") long maxBytes) {
        this.maxBytes = maxBytes;
    }

    public UploadMediaKind validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw invalid("Chưa chọn file (field `file`)");
        }
        if (file.getSize() > maxBytes) {
            throw invalid("File quá lớn (tối đa " + maxBytes / 1024 / 1024 + "MB)");
        }
        try (InputStream input = file.getInputStream()) {
            byte[] header = input.readNBytes(SIGNATURE_BYTES);
            UploadMediaKind kind = UploadMediaKind.detect(header, header.length);
            if (kind != null) return kind;
            if (isSvgCandidate(file)) {
                SvgUploadValidator.validate(file);
                return UploadMediaKind.SVG;
            }
            throw invalid("Định dạng không được hỗ trợ (png, jpg, gif, webp, svg, mp4, webm)");
        } catch (IOException failure) {
            throw invalid("Không đọc được file");
        }
    }

    private boolean isSvgCandidate(MultipartFile file) {
        String name = originalName(file);
        return "image/svg+xml".equalsIgnoreCase(file.getContentType())
                || (name != null && name.toLowerCase(java.util.Locale.ROOT).endsWith(".svg"));
    }

    public static String originalName(MultipartFile file) {
        String name = file.getOriginalFilename();
        if (name == null) return null;
        String normalized = name.replace('\\', '/');
        return normalized.substring(normalized.lastIndexOf('/') + 1);
    }

    private BusinessException invalid(String message) {
        return BusinessException.badRequest(ErrorCode.VALIDATION_FAILED, message);
    }
}
