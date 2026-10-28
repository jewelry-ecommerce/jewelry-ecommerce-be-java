package com.pk.core.business.service.impl;

import com.pk.core.business.service.FileStorageService;
import com.pk.core.common.exception.BusinessException;
import com.pk.core.common.exception.ErrorCode;
import com.pk.core.model.dto.response.UploadResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;

/**
 * Upload ảnh/video/SVG qua {@link UploadStorage} (một thư mục gốc, tách folder images/ và videos/). Tên file do server sinh (UUID)
 * và phần mở rộng suy ra từ NỘI DUNG file (magic bytes) nên không thể upload HTML/script giả làm ảnh. SVG phải qua {@link SvgSafety}.
 */
@Service
@RequiredArgsConstructor
public class LocalFileStorageService implements FileStorageService {

    private enum Kind {
        PNG("png", "image/png", "IMAGE"), JPEG("jpg", "image/jpeg", "IMAGE"), GIF("gif", "image/gif", "IMAGE"),
        WEBP("webp", "image/webp", "IMAGE"), SVG("svg", "image/svg+xml", "IMAGE"),
        MP4("mp4", "video/mp4", "VIDEO"), WEBM("webm", "video/webm", "VIDEO");

        final String ext;
        final String contentType;
        final String type;

        Kind(String ext, String contentType, String type) {
            this.ext = ext;
            this.contentType = contentType;
            this.type = type;
        }

        UploadStorage.Folder folder() {
            return "VIDEO".equals(type) ? UploadStorage.Folder.VIDEOS : UploadStorage.Folder.IMAGES;
        }
    }

    private final UploadStorage storage;

    @Value("${app.upload.max-bytes:10485760}")
    private long maxBytes;

    @Override
    public UploadResponseDto store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw BusinessException.badRequest(ErrorCode.VALIDATION_FAILED, "Chưa chọn file (field `file`)");
        }
        if (file.getSize() > maxBytes) {
            throw BusinessException.badRequest(ErrorCode.VALIDATION_FAILED,
                    "File quá lớn (tối đa " + (maxBytes / 1024 / 1024) + "MB)");
        }
        byte[] head = new byte[16];
        int read;
        try (InputStream in = file.getInputStream()) {
            read = in.readNBytes(head, 0, head.length);
        } catch (IOException ex) {
            throw BusinessException.badRequest(ErrorCode.VALIDATION_FAILED, "Không đọc được file");
        }
        Kind kind = sniff(head, read);
        byte[] svg = null;
        if (kind == null && looksLikeText(head, read)) {
            svg = readSvg(file);
            kind = Kind.SVG;
        }
        if (kind == null) {
            throw BusinessException.badRequest(ErrorCode.VALIDATION_FAILED,
                    "Định dạng không được hỗ trợ (cho phép: png, jpg, gif, webp, svg tĩnh, mp4, webm)");
        }
        String key;
        try (InputStream in = svg != null ? new ByteArrayInputStream(svg) : file.getInputStream()) {
            key = storage.save(kind.folder(), kind.ext, in);
        } catch (IOException | IllegalStateException ex) {
            throw new IllegalStateException("Không ghi được file upload", ex);
        }
        String original = file.getOriginalFilename() == null ? null : Paths.get(file.getOriginalFilename()).getFileName().toString();
        return new UploadResponseDto(storage.publicUrl(key), key.substring(key.lastIndexOf('/') + 1), original, file.getSize(),
                kind.contentType, kind.type);
    }

    /** Đọc toàn bộ file text (đã giới hạn kích thước) và bắt buộc qua chính sách SVG; không phải SVG thì coi là định dạng không hỗ trợ. */
    private static byte[] readSvg(MultipartFile file) {
        if (file.getSize() > SvgSafety.MAX_BYTES) {
            throw BusinessException.badRequest(ErrorCode.VALIDATION_FAILED,
                    "Định dạng không được hỗ trợ (cho phép: png, jpg, gif, webp, svg tĩnh, mp4, webm)");
        }
        byte[] all;
        try {
            all = file.getBytes();
        } catch (IOException ex) {
            throw BusinessException.badRequest(ErrorCode.VALIDATION_FAILED, "Không đọc được file");
        }
        if (!SvgSafety.looksLikeSvg(all)) {
            throw BusinessException.badRequest(ErrorCode.VALIDATION_FAILED,
                    "Định dạng không được hỗ trợ (cho phép: png, jpg, gif, webp, svg tĩnh, mp4, webm)");
        }
        SvgSafety.validate(all);
        return all;
    }

    /** Bắt đầu bằng BOM UTF-8 hoặc khoảng trắng rồi '<' - dấu hiệu của XML/SVG (không phải nhị phân). */
    private static boolean looksLikeText(byte[] b, int n) {
        int i = 0;
        if (n >= 3 && (b[0] & 0xFF) == 0xEF && (b[1] & 0xFF) == 0xBB && (b[2] & 0xFF) == 0xBF) {
            i = 3;
        }
        while (i < n && (b[i] == ' ' || b[i] == '\t' || b[i] == '\r' || b[i] == '\n')) {
            i++;
        }
        return i < n && b[i] == '<';
    }

    private static Kind sniff(byte[] b, int n) {
        if (n >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') {
            return Kind.PNG;
        }
        if (n >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return Kind.JPEG;
        }
        if (n >= 6 && new String(b, 0, 4, StandardCharsets.ISO_8859_1).equals("GIF8")) {
            return Kind.GIF;
        }
        if (n >= 12 && new String(b, 0, 4, StandardCharsets.ISO_8859_1).equals("RIFF")
                && new String(b, 8, 4, StandardCharsets.ISO_8859_1).equals("WEBP")) {
            return Kind.WEBP;
        }
        if (n >= 12 && new String(b, 4, 4, StandardCharsets.ISO_8859_1).equals("ftyp")) {
            return Kind.MP4;
        }
        if (n >= 4 && (b[0] & 0xFF) == 0x1A && (b[1] & 0xFF) == 0x45 && (b[2] & 0xFF) == 0xDF && (b[3] & 0xFF) == 0xA3) {
            return Kind.WEBM;
        }
        return null;
    }
}
