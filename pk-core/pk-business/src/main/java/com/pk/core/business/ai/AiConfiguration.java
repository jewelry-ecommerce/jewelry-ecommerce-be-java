package com.pk.core.business.ai;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.util.concurrent.Semaphore;

@Configuration
@EnableConfigurationProperties(AiProperties.class)
public class AiConfiguration {

    @Bean("aiRestClient")
    RestClient aiRestClient(AiProperties properties) {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(properties.getTimeout())
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(client);
        factory.setReadTimeout(properties.getTimeout());
        return RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .requestFactory(factory)
                .build();
    }

    @Bean("aiProviderSemaphore")
    Semaphore aiProviderSemaphore(AiProperties properties) {
        int permits = Math.max(1, properties.getMaxConcurrentCalls());
        return new Semaphore(permits);
    }
}
