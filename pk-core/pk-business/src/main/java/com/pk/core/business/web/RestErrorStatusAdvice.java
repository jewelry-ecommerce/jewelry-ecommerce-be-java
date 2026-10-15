package com.pk.core.business.web;

import com.pk.core.common.web.BaseRes;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * Bảo đảm HTTP status khớp {@code BaseRes.status} khi lỗi. Controller tự bắt lỗi rồi trả BaseRes lỗi (restErrorHandle),
 * nhưng method có {@code @ResponseStatus(CREATED)} thì Spring đặt lại 201 SAU khi method chạy xong, nên lỗi 404/409/400
 * bị trả với HTTP 201 (FE phải đọc body.status mới biết lỗi). Advice này chạy ngay trước khi ghi body nên đặt lại đúng status.
 * Chỉ nâng status cho lỗi (>= 400); thành công giữ nguyên (200/201 theo annotation).
 */
@RestControllerAdvice
public class RestErrorStatusAdvice implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return BaseRes.class.isAssignableFrom(returnType.getParameterType());
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request, ServerHttpResponse response) {
        if (body instanceof BaseRes res && res.getStatus() >= 400 && res.getStatus() < 600) {
            response.setStatusCode(HttpStatusCode.valueOf(res.getStatus()));
        }
        return body;
    }
}
