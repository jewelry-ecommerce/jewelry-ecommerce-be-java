package com.pk.core.business.storage;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import java.net.http.HttpClient;

@Configuration
@ConditionalOnProperty(name = "app.upload.provider", havingValue = "cloudinary")
@EnableConfigurationProperties(CloudinaryStorageProperties.class)
public class CloudinaryStorageConfiguration {
    public static final String API_BASE_URL = "https://api.cloudinary.com/v1_1/";
    public static final String DELIVERY_HOST = "res.cloudinary.com";
    public static final String SIGNATURE_ALGORITHM = "SHA-256";

    @Bean("cloudinaryRestClient")
    RestClient cloudinaryRestClient(CloudinaryStorageProperties properties) {
        HttpClient client = HttpClient.newBuilder().connectTimeout(properties.getConnectTimeout()).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(client);
        factory.setReadTimeout(properties.getReadTimeout());
        return RestClient.builder().requestFactory(factory).build();
    }
}
