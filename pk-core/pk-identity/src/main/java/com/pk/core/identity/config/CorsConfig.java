package com.pk.core.identity.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/** CORS cho FE React (localhost khi dev, Vercel khi deploy). Cấu hình qua app.cors.allowed-origins.
 * @Primary: Spring MVC tự đăng ký bean "mvcHandlerMappingIntrospector" (HandlerMappingIntrospector)
 * và lớp đó CŨNG implement CorsConfigurationSource, nên khi SecurityConfig autowire theo type sẽ thấy
 * 2 bean cùng khớp (lỗi "expected single matching bean but found 2") nếu không đánh dấu bean nào là
 * chính - đây là vấn đề đã biết, không phải do code CorsConfig viết sai. */
@Configuration
public class CorsConfig {

    @Bean
    @Primary
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origins:http://localhost:5173,http://localhost:3000,http://localhost:6100,http://localhost:3100}") String[] origins) {
        CorsConfiguration cfg = new CorsConfiguration();
        // Patterns cho phép cả dạng https://*.vercel.app (preview deployments).
        cfg.setAllowedOriginPatterns(Arrays.stream(origins).map(String::trim).filter(s -> !s.isEmpty()).toList());
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "Idempotency-Key", "X-Guest-Cart-Id", "x-guest-id", "Language", "x-tenant-code"));
        cfg.setAllowCredentials(false); // dùng Bearer token, không dùng cookie
        cfg.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cfg);
        return source;
    }
}
