package com.pk.core.business.config;

import com.pk.core.business.service.impl.UploadStorage;
import com.pk.core.model.constant.UrlConstant;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;

/**
 * Phục vụ file đã upload: GET /api/v1/storefront/files/** -> thư mục gốc của {@link UploadStorage} (công khai, chỉ đọc).
 * Folder riêng tư (private/, ie/) KHÔNG bao giờ được phục vụ ở đây - trả 404 dù URL viết hoa, %-encoded hay có ./.
 * File .svg luôn kèm CSP chặn script/tải ngoài (lớp phòng thủ thứ hai sau SvgSafety).
 */
@Configuration
@RequiredArgsConstructor
public class UploadWebConfig implements WebMvcConfigurer {

    private final UploadStorage storage;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = storage.root().toUri().toString();
        if (!location.endsWith("/")) {
            location += "/";
        }
        registry.addResourceHandler(UrlConstant.Common.API + UrlConstant.Common.VERSION + UrlConstant.Storefront.FILES + "/**")
                .addResourceLocations(location)
                .resourceChain(false)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) throws IOException {
                        return storage.isPublic(resourcePath) ? super.getResource(resourcePath, location) : null;
                    }
                });
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new org.springframework.web.servlet.HandlerInterceptor() {
            @Override
            public boolean preHandle(jakarta.servlet.http.HttpServletRequest request,
                                     jakarta.servlet.http.HttpServletResponse response, Object handler) {
                response.setHeader("X-Content-Type-Options", "nosniff");
                if (request.getRequestURI().toLowerCase(java.util.Locale.ROOT).endsWith(".svg")) {
                    response.setHeader("Content-Security-Policy",
                            "default-src 'none'; style-src 'unsafe-inline'; img-src data:; sandbox");
                }
                return true;
            }
        }).addPathPatterns(UrlConstant.Common.API + UrlConstant.Common.VERSION + UrlConstant.Storefront.FILES + "/**");
    }
}
