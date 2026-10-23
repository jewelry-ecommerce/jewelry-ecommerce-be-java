package com.pk.core.business.service.impl;

import com.pk.core.business.service.FileStorageService;
import com.pk.core.business.storage.UploadMediaKind;
import com.pk.core.business.storage.UploadMediaValidator;
import com.pk.core.common.exception.BusinessException;
import com.pk.core.common.exception.ErrorCode;
import com.pk.core.model.constant.UrlConstant;
import com.pk.core.model.dto.response.UploadResponseDto;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.UUID;

/** Local developer storage; cloud uses a separate provider without changing the upload contract. */
@Service
@ConditionalOnProperty(name = "app.upload.provider", havingValue = "local", matchIfMissing = true)
public class LocalFileStorageService implements FileStorageService {
    private final UploadMediaValidator validator;
    private final String publicBaseUrl;
    private final Path root;

    public LocalFileStorageService(UploadMediaValidator validator,
                                   @Value("${app.upload.dir:D:/pk-uploads}") String dir,
                                   @Value("${app.upload.public-base-url:}") String publicBaseUrl) {
        this.validator = validator;
        this.publicBaseUrl = publicBaseUrl;
        this.root = Paths.get(dir).toAbsolutePath().normalize();
    }

    @PostConstruct
    void init() throws IOException {
        Files.createDirectories(root);
    }

    @Override
    public UploadResponseDto store(MultipartFile file) {
        UploadMediaKind kind = validator.validate(file);
        LocalDate today = LocalDate.now();
        String relative = today.getYear() + "/" + String.format("%02d", today.getMonthValue())
                + "/" + UUID.randomUUID() + "." + kind.extension;
        copy(file, relative);
        String base = publicBaseUrl == null ? "" : publicBaseUrl.trim().replaceAll("/+$", "");
        String url = base + UrlConstant.Common.API + UrlConstant.Common.VERSION
                + UrlConstant.Storefront.FILES + "/" + relative;
        return new UploadResponseDto(url, relative.substring(relative.lastIndexOf('/') + 1),
                UploadMediaValidator.originalName(file), file.getSize(), kind.contentType, kind.type);
    }

    private void copy(MultipartFile file, String relative) {
        Path target = root.resolve(relative).normalize();
        if (!target.startsWith(root)) {
            throw BusinessException.badRequest(ErrorCode.VALIDATION_FAILED, "Đường dẫn lưu không hợp lệ");
        }
        try (InputStream input = file.getInputStream()) {
            Files.createDirectories(target.getParent());
            Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException failure) {
            throw new BusinessException(503, ErrorCode.UPLOAD_STORAGE_UNAVAILABLE, "Không ghi được file upload");
        }
    }
}
