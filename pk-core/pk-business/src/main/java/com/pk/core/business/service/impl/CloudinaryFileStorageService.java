package com.pk.core.business.service.impl;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.pk.core.business.service.FileStorageService;
import com.pk.core.business.storage.*;
import com.pk.core.common.exception.BusinessException;
import com.pk.core.common.exception.ErrorCode;
import com.pk.core.model.dto.response.UploadResponseDto;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.multipart.MultipartFile;
import java.net.URI;
import java.time.Instant;
import java.util.UUID;

@Service
@ConditionalOnProperty(name = "app.upload.provider", havingValue = "cloudinary")
public class CloudinaryFileStorageService implements FileStorageService {
    private final CloudinaryStorageProperties properties;
    private final UploadMediaValidator validator;
    private final RestClient client;

    public CloudinaryFileStorageService(CloudinaryStorageProperties properties, UploadMediaValidator validator,
                                       @Qualifier("cloudinaryRestClient") RestClient client) {
        this.properties = properties;
        this.validator = validator;
        this.client = client;
    }

    @Override
    public UploadResponseDto store(MultipartFile file) {
        UploadMediaKind kind = validator.validate(file);
        String publicId = UUID.randomUUID().toString();
        CloudinaryResult result = upload(file, kind, publicId);
        String url = validateDeliveryUrl(result);
        return new UploadResponseDto(url, publicId + "." + kind.extension,
                UploadMediaValidator.originalName(file), file.getSize(), kind.contentType, kind.type);
    }

    private CloudinaryResult upload(MultipartFile file, UploadMediaKind kind, String publicId) {
        String endpoint = CloudinaryStorageConfiguration.API_BASE_URL + properties.getCloudName()
                + "/" + kind.resourceType() + "/upload";
        try {
            return client.post().uri(endpoint).contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(uploadForm(file, kind, publicId)).retrieve().body(CloudinaryResult.class);
        } catch (RestClientException failure) {
            // Do not include provider responses, signed forms or credentials in public errors.
            throw unavailable();
        }
    }

    private MultiValueMap<String, Object> uploadForm(MultipartFile file, UploadMediaKind kind, String publicId) {
        String timestamp = Long.toString(Instant.now().getEpochSecond());
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("api_key", properties.getApiKey());
        form.add("timestamp", timestamp);
        form.add("folder", properties.getFolder());
        form.add("public_id", publicId);
        form.add("signature", CloudinaryUploadSigner.sign(properties.getFolder(), publicId,
                timestamp, properties.getApiSecret()));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(kind.contentType));
        form.add("file", new HttpEntity<>(file.getResource(), headers));
        return form;
    }

    private String validateDeliveryUrl(CloudinaryResult result) {
        if (result == null || result.secureUrl() == null) throw unavailable();
        try {
            URI uri = URI.create(result.secureUrl());
            if (!"https".equals(uri.getScheme()) || uri.getUserInfo() != null
                    || !CloudinaryStorageConfiguration.DELIVERY_HOST.equals(uri.getHost())
                    || !uri.getPath().startsWith("/" + properties.getCloudName() + "/")) throw unavailable();
            return uri.toString();
        } catch (IllegalArgumentException failure) {
            throw unavailable();
        }
    }

    private BusinessException unavailable() {
        return new BusinessException(503, ErrorCode.UPLOAD_STORAGE_UNAVAILABLE,
                "Chưa thể lưu ảnh/video. Vui lòng thử lại sau.");
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CloudinaryResult(@JsonProperty("secure_url") String secureUrl) {}
}
