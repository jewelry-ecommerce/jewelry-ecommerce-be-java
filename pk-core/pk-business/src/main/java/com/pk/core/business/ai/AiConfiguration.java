package com.pk.core.business.ai;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.Semaphore;

@Configuration
@EnableConfigurationProperties(AiProperties.class)
public class AiConfiguration {

    @Bean("aiRestClient")
    RestClient aiRestClient(AiProperties properties) {
        return client(properties.getBaseUrl(), properties.getTimeout());
    }

    @Bean("assistantRestClient")
    RestClient assistantRestClient(AiProperties properties) {
        return client(properties.getBaseUrl(), properties.getAssistantTimeout());
    }

    @Bean("tryOnRestClient")
    RestClient tryOnRestClient(AiProperties properties) {
        return client(properties.getBaseUrl(), properties.getTryOnTimeout());
    }

    private static RestClient client(String baseUrl, Duration timeout) {
        HttpClient client = HttpClient.newBuilder().connectTimeout(timeout).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(client);
        factory.setReadTimeout(timeout);
        return RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    @Bean("aiProviderSemaphore")
    Semaphore aiProviderSemaphore(AiProperties properties) {
        int permits = Math.max(1, properties.getMaxConcurrentCalls());
        return new Semaphore(permits);
    }
}
