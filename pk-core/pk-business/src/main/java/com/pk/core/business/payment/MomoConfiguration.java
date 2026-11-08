package com.pk.core.business.payment;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import java.net.http.HttpClient;

@Configuration
@EnableConfigurationProperties(MomoProperties.class)
public class MomoConfiguration {
    @Bean("momoRestClient")
    RestClient momoRestClient(MomoProperties properties) {
        HttpClient client = HttpClient.newBuilder().connectTimeout(properties.getTimeout()).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(client);
        factory.setReadTimeout(properties.getTimeout());
        return RestClient.builder().baseUrl(MomoProperties.API_BASE).requestFactory(factory).build();
    }
}
