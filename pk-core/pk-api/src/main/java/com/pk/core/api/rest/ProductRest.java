package com.pk.core.api.rest;

import org.springframework.web.bind.annotation.RequestMapping;
import lombok.RequiredArgsConstructor;

import com.pk.core.business.service.CategoryService;
import com.pk.core.business.service.CollectionService;
import com.pk.core.business.service.ProductSearchQuery;
import com.pk.core.business.service.ProductService;
import com.pk.core.business.web.AbstractRest;
import com.pk.core.common.exception.BusinessException;
import com.pk.core.common.exception.ErrorCode;
import com.pk.core.common.web.BaseRes;
import com.pk.core.model.constant.UrlConstant;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.pk.core.identity.security.model.SecurityUser;
import com.pk.core.common.web.PageResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Storefront - đọc catalog, không cần đăng nhập (xem document/09-tong-hop-api-fe.md mục C).
 * Admin CRUD (tạo/sửa/xoá category, product, sku...) CHƯA làm lại đợt này - phần đã xoá trước đó
 * (AdminCatalogRest, CategoryRest cũ) không nằm trong lần dựng lại này (yêu cầu "ngoài admin").
 */
@RestController
@RequestMapping(UrlConstant.Common.API + UrlConstant.Common.VERSION)
@RequiredArgsConstructor
public class ProductRest extends AbstractRest {

    private final ProductService productService;
    private final CategoryService categoryService;
    private final CollectionService collectionService;
    private final com.pk.core.business.service.SearchPinnedService searchPinnedService;
    private final com.pk.core.business.service.SearchKeywordService searchKeywordService;

    /** Sản phẩm đang được ghim ở ô tìm kiếm (landing), theo thứ tự ô. */
    @GetMapping(UrlConstant.Product.SEARCH_PINNED)
    public BaseRes searchPinned(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            return restSuccessHandle.handleSuccess(searchPinnedService.activeLandingProducts());
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }

    @GetMapping(UrlConstant.Product.BASE)
    public BaseRes search(@RequestParam(required = false) String category,
                           @RequestParam(required = false) String collection,
                           @RequestParam(required = false) String search,
                           @RequestParam(required = false) String material,
                           @RequestParam(required = false) BigDecimal minPrice,
                           @RequestParam(required = false) BigDecimal maxPrice,
                           @RequestParam(required = false) String categorySlugs,
                           @RequestParam(required = false) String attributes,
                           @RequestParam(required = false) Boolean inStock,
                           @RequestParam(required = false, defaultValue = "NEWEST") String sort,
                           @RequestParam(required = false, defaultValue = "1") int page,
                           @RequestParam(required = false, defaultValue = "20") int take,
                           @AuthenticationPrincipal SecurityUser principal,
                           HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            PageResponse<?> result = productService.search(new ProductSearchQuery(category,
                    splitStrings(categorySlugs), collection, search, material, minPrice, maxPrice,
                    splitIds(attributes), inStock, sort, page, take));
            recordSearch(principal, search, page, result);
            return restSuccessHandle.handleSuccess(result);
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }

    /**
     * Ghi từ khoá vào trending + lịch sử (nếu đã đăng nhập) khi trang ĐẦU của một lượt tìm có kết quả. Lỗi ghi nhận không bao giờ
     * làm hỏng kết quả tìm kiếm.
     */
    private void recordSearch(SecurityUser principal, String search, int page, PageResponse<?> result) {
        if (search == null || search.isBlank() || page > 1 || result.totalElements() == 0) {
            return;
        }
        try {
            searchKeywordService.record(principal == null ? null : principal.getId(), search);
        } catch (Exception ex) {
            org.slf4j.LoggerFactory.getLogger(ProductRest.class).debug("Không ghi nhận được từ khoá tìm kiếm", ex);
        }
    }

    /** Bộ lọc (facets) cho trang danh sách: [{id, label, options:[{value, label, count, code}]}]. `value` dùng cho `attributes=`. */
    @GetMapping(UrlConstant.Product.BASE + "/filters")
    public BaseRes filters(@RequestParam(required = false) String category,
                            @RequestParam(required = false) String categorySlugs,
                            @RequestParam(required = false) String collection,
                            @RequestParam(required = false) String search,
                            @RequestParam(required = false) BigDecimal minPrice,
                            @RequestParam(required = false) BigDecimal maxPrice,
                            @RequestParam(required = false) Boolean inStock,
                            HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            return restSuccessHandle.handleSuccess(productService.filters(new ProductSearchQuery(category,
                    splitStrings(categorySlugs), collection, search, null, minPrice, maxPrice, List.of(), inStock,
                    "NEWEST", 1, 20)));
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }

    private static List<String> splitStrings(String csv) {
        List<String> out = new ArrayList<>();
        if (csv != null) {
            for (String part : csv.split(",")) {
                if (!part.isBlank()) {
                    out.add(part.trim());
                }
            }
        }
        return out;
    }

    /** `attributes=12,15` -> id giá trị thuộc tính; phần tử không phải số -> 400 (không bỏ qua im lặng). */
    private static List<Long> splitIds(String csv) {
        List<Long> out = new ArrayList<>();
        for (String part : splitStrings(csv)) {
            try {
                out.add(Long.valueOf(part));
            } catch (NumberFormatException ex) {
                throw BusinessException.badRequest(ErrorCode.VALIDATION_FAILED,
                        "attributes phải là danh sách id số, cách nhau dấu phẩy: " + part, part);
            }
        }
        return out;
    }

    @GetMapping(UrlConstant.Product.CATEGORIES)
    public BaseRes categories(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            return restSuccessHandle.handleSuccess(categoryService.findAllActive());
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }

    @GetMapping(UrlConstant.Product.COLLECTIONS)
    public BaseRes collections(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            return restSuccessHandle.handleSuccess(collectionService.findAllPublished());
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }

    /** Đặt sau CATEGORIES/COLLECTIONS: Spring ưu tiên khớp path tĩnh hơn {slug} nên không xung đột. */
    @GetMapping(UrlConstant.Product.BASE + "/{slug}")
    public BaseRes detail(@PathVariable String slug,
                           HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            return restSuccessHandle.handleSuccess(productService.findBySlug(slug));
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }
}
