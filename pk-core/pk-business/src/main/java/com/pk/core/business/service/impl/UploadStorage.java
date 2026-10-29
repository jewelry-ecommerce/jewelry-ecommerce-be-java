package com.pk.core.business.service.impl;

import com.pk.core.model.constant.UrlConstant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Nơi lưu file DUY NHẤT của hệ thống: một thư mục gốc `app.upload.dir` (biến môi trường UPLOAD_DIR), tách folder theo mục đích:
 * <pre>
 *   images/yyyy/mm/&lt;uuid&gt;.png|jpg|gif|webp|svg   công khai (GET /storefront/files/...)
 *   videos/yyyy/mm/&lt;uuid&gt;.mp4|webm               công khai
 *   private/import-export/yyyy/mm/&lt;uuid&gt;.csv     RIÊNG TƯ - không bao giờ được phục vụ công khai, chỉ tải qua link ký có hạn
 * </pre>
 * File cũ nằm thẳng dưới `yyyy/mm/` vẫn được phục vụ như trước (không đổi URL đã lưu trong DB); `ie/` (CSV cũ) bị coi là riêng tư.
 * Key lưu trong DB là đường dẫn tương đối so với gốc, vd "images/2026/10/abc.png".
 */
@Component
public class UploadStorage {

    /** Folder theo mục đích. */
    public enum Folder {
        IMAGES("images"), VIDEOS("videos"), IMPORT_EXPORT("private/import-export");

        final String path;

        Folder(String path) {
            this.path = path;
        }
    }

    /** Tiền tố (chữ thường) không bao giờ được phục vụ công khai. `ie` là nơi CSV cũ nằm trước khi tách folder. */
    private static final List<String> PRIVATE_ROOTS = List.of("private", "ie");

    private final Path root;
    private final String publicBaseUrl;

    public UploadStorage(@Value("${app.upload.dir:D:/pk-uploads}") String dir,
                         @Value("${app.upload.public-base-url:}") String publicBaseUrl) {
        this.root = Paths.get(dir).toAbsolutePath().normalize();
        this.publicBaseUrl = publicBaseUrl == null ? "" : publicBaseUrl.trim().replaceAll("/+$", "");
        try {
            Files.createDirectories(root);
        } catch (IOException ex) {
            throw new IllegalStateException("Không tạo được thư mục upload: " + root, ex);
        }
    }

    public Path root() {
        return root;
    }

    /** Tiền tố tuyệt đối (rỗng nếu không cấu hình) ghép trước URL trả cho FE. */
    public String baseUrl() {
        return publicBaseUrl;
    }

    /** Ghi file vào folder (thư mục con yyyy/mm tự tạo), tên do server sinh. @return key tương đối. */
    public String save(Folder folder, String ext, InputStream in) throws IOException {
        Path target = newTarget(folder, ext);
        Files.createDirectories(target.getParent());
        Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        return key(target);
    }

    public String save(Folder folder, String ext, byte[] content) throws IOException {
        Path target = newTarget(folder, ext);
        Files.createDirectories(target.getParent());
        Files.write(target, content);
        return key(target);
    }

    /** Đọc file theo key; null nếu không còn (vd máy chủ đã dựng lại trên đĩa tạm). Key ngoài thư mục gốc bị từ chối. */
    public byte[] read(String key) throws IOException {
        Path p = resolve(key);
        try {
            return Files.readAllBytes(p);
        } catch (NoSuchFileException ex) {
            return null;
        }
    }

    /** File này có được phục vụ công khai không (chặn private/, ie/ kể cả dạng %-encoded, ./, hoa-thường). */
    public boolean isPublic(String resourcePath) {
        String current = resourcePath == null ? "" : resourcePath;
        for (int i = 0; i < 4; i++) {
            if (!publicForm(current)) {
                return false;
            }
            String decoded;
            try {
                decoded = URLDecoder.decode(current.replace("+", "%2B"), StandardCharsets.UTF_8);
            } catch (IllegalArgumentException ex) {
                return false;
            }
            if (decoded.equals(current)) {
                return true;
            }
            current = decoded;
        }
        return false;
    }

    /** URL công khai của key (chỉ dùng cho key công khai). */
    public String publicUrl(String key) {
        return publicBaseUrl + UrlConstant.Common.API + UrlConstant.Common.VERSION + UrlConstant.Storefront.FILES + "/" + key;
    }

    private static boolean publicForm(String path) {
        try {
            String n = Paths.get(path.replace('\\', '/')).normalize().toString().replace('\\', '/').toLowerCase(Locale.ROOT);
            if (n.startsWith("/") || n.startsWith("..")) {
                return false;
            }
            String first = n.contains("/") ? n.substring(0, n.indexOf('/')) : n;
            return !PRIVATE_ROOTS.contains(first);
        } catch (InvalidPathException ex) {
            return false;
        }
    }

    private Path resolve(String key) {
        Path p = root.resolve(key == null ? "" : key).normalize();
        if (!p.startsWith(root)) {
            throw new IllegalStateException("Đường dẫn lưu không hợp lệ");
        }
        return p;
    }

    private Path newTarget(Folder folder, String ext) {
        LocalDate today = LocalDate.now();
        String relative = folder.path + "/" + today.getYear() + "/" + String.format("%02d", today.getMonthValue()) + "/"
                + UUID.randomUUID() + "." + ext;
        return resolve(relative);
    }

    private String key(Path target) {
        return root.relativize(target).toString().replace('\\', '/');
    }
}
