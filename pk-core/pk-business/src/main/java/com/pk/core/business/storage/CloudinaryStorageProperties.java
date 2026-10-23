package com.pk.core.business.storage;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import java.time.Duration;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "app.upload.cloudinary")
public class CloudinaryStorageProperties {
    @NotBlank
    @Pattern(regexp = "[a-zA-Z0-9_-]+")
    private String cloudName;
    @NotBlank
    private String apiKey;
    @NotBlank
    private String apiSecret;
    @NotBlank
    @Pattern(regexp = "[a-zA-Z0-9_/-]+")
    private String folder = "jewelry-commerce";
    @NotNull
    private Duration connectTimeout = Duration.ofSeconds(5);
    @NotNull
    private Duration readTimeout = Duration.ofSeconds(30);
}
