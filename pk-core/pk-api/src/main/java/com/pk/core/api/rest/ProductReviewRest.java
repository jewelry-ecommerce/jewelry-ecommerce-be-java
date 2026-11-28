package com.pk.core.api.rest;

import com.pk.core.business.service.ProductReviewService;
import com.pk.core.business.web.AbstractRest;
import com.pk.core.common.web.BaseRes;
import com.pk.core.identity.security.model.SecurityUser;
import com.pk.core.model.constant.UrlConstant;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Storefront đánh giá sản phẩm (public list/summary + tạo khi đã login). */
@RestController
@RequestMapping(UrlConstant.Common.API + UrlConstant.Common.VERSION)
@RequiredArgsConstructor
public class ProductReviewRest extends AbstractRest {

    private static final String BASE = UrlConstant.Product.REVIEWS;

    private final ProductReviewService productReviewService;

    @GetMapping(BASE + "/product/{productId}")
    public BaseRes list(@PathVariable Long productId, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            return restSuccessHandle.handleSuccess(productReviewService.publicByProduct(productId));
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }

    @GetMapping(BASE + "/product/{productId}/summary")
    public BaseRes summary(@PathVariable Long productId, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            return restSuccessHandle.handleSuccess(productReviewService.publicSummary(productId));
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }

    @PostMapping(BASE)
    public BaseRes create(@AuthenticationPrincipal SecurityUser principal,
                          @RequestBody Map<String, Object> body,
                          HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            Long productId = asLong(body.get("productId"));
            Long orderId = asLong(body.get("orderId"));
            int rating = body.get("rating") instanceof Number n ? n.intValue() : 0;
            String title = body.get("title") == null ? "" : String.valueOf(body.get("title"));
            String content = body.get("content") == null ? "" : String.valueOf(body.get("content"));
            String authorName = body.get("authorName") == null
                    ? (principal == null ? "Khách" : principal.getPhone())
                    : String.valueOf(body.get("authorName"));
            @SuppressWarnings("unchecked")
            List<String> images = body.get("images") instanceof List<?> list ? (List<String>) list : List.of();
            Long userId = principal == null ? null : principal.getId();
            return restSuccessHandle.handleSuccess(
                    productReviewService.create(productId, orderId, userId, authorName, rating, title, content, images));
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }

    private static Long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return null;
        }
        String raw = String.valueOf(value).trim();
        if (raw.isEmpty()) {
            return null;
        }
        return Long.parseLong(raw);
    }
}
