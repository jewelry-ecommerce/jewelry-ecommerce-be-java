package com.pk.core.business.service.impl;

import com.pk.core.business.service.FileStorageService;
import com.pk.core.business.storage.UploadMediaKind;
import com.pk.core.business.storage.UploadMediaValidator;
import com.pk.core.common.exception.BusinessException;
import com.pk.core.common.exception.ErrorCode;
import com.pk.core.model.dto.response.UploadResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

/** Local uploads share validation with Cloudinary and use the v8 public/private storage layout. */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.upload.provider", havingValue = "local", matchIfMissing = true)
public class LocalFileStorageService implements FileStorageService {
    private final UploadMediaValidator validator;
    private final UploadStorage storage;

    @Override
    public UploadResponseDto store(MultipartFile file) {
        UploadMediaKind kind = validator.validate(file);
        String key = save(file, kind);
        return new UploadResponseDto(storage.publicUrl(key), key.substring(key.lastIndexOf('/') + 1),
                UploadMediaValidator.originalName(file), file.getSize(), kind.contentType, kind.type);
    }

    private String save(MultipartFile file, UploadMediaKind kind) {
        UploadStorage.Folder folder = "VIDEO".equals(kind.type)
                ? UploadStorage.Folder.VIDEOS : UploadStorage.Folder.IMAGES;
        try (InputStream input = file.getInputStream()) {
            return storage.save(folder, kind.extension, input);
        } catch (IOException | IllegalStateException failure) {
            throw new BusinessException(503, ErrorCode.UPLOAD_STORAGE_UNAVAILABLE, "Upload storage unavailable");
        }
    }
}
