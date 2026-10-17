package com.pk.core.api.rest.admin;

import org.springframework.web.bind.annotation.RequestMapping;
import com.pk.core.model.constant.UrlConstant;
import lombok.RequiredArgsConstructor;

import com.pk.core.business.service.AdminProductQuery;
import com.pk.core.business.service.CategoryService;
import com.pk.core.business.service.CollectionService;
import com.pk.core.business.service.ProductAttributeService;
import com.pk.core.business.service.FileStorageService;
import com.pk.core.business.service.ProductMediaService;
import com.pk.core.business.service.ProductService;
import com.pk.core.business.web.AbstractRest;
import com.pk.core.common.web.BaseRes;
import com.pk.core.model.constant.admin.UrlAdminConstant;
import com.pk.core.model.dto.request.AdminAttributeRequestDto;
import com.pk.core.model.dto.request.AdminProductMediaRequestDto;
import com.pk.core.model.dto.request.AdminSkuAttributesRequestDto;
import com.pk.core.model.dto.request.AdminCategoryRequestDto;
import com.pk.core.model.dto.request.AdminCollectionProductsRequestDto;
import com.pk.core.model.dto.request.AdminSkuRequestDto;
import com.pk.core.model.dto.request.AdminCollectionRequestDto;
import com.pk.core.model.dto.request.AdminCreateProductRequestDto;
import com.pk.core.model.dto.request.AdminUpdateProductRequestDto;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * API admin - Catalog/PIM (document/09-tong-hop-api-fe.md mục J). Gói api.rest.admin (pk-api), TÁCH
 * RIÊNG khỏi Rest storefront (ProductRest, gói api.rest) - xem claude/RULE-CODE.md. Mọi route ở đây
 * đã bị SecurityConfig (pk-identity) bắt buộc hasRole("ADMIN") (rule chung cho cả UrlAdminConstant.
 * Common.BASE + "/**", trừ /admin/auth/login).
 *
 * <p>KHÔNG có endpoint sửa/thêm variant (SKU) riêng sau khi sản phẩm đã tạo - spec chỉ liệt kê GET
 * cho variants (xem {@code AdminSkuRequestDto} javadoc), nên PUT sản phẩm chỉ sửa thông tin mô tả,
 * không đụng tới SKU.</p>
 */
@RestController
@RequestMapping(UrlConstant.Common.API + UrlConstant.Common.VERSION)
@RequiredArgsConstructor
public class AdminCatalogRest extends AbstractRest {

    private final ProductService productService;
    private final CategoryService categoryService;
    private final CollectionService collectionService;
    private final ProductAttributeService attributeService;
    private final ProductMediaService mediaService;
    private final FileStorageService fileStorage;

    // ---------- Products ----------

    @GetMapping(UrlAdminConstant.Catalog.PRODUCTS)
    public BaseRes listProducts(@RequestParam(required = false) String search,
                                 @RequestParam(required = false) String status,
                                 @RequestParam(required = false) String categorySlug,
                                 @RequestParam(required = false) String collectionSlug,
                                 @RequestParam(required = false) java.math.BigDecimal minPrice,
                                 @RequestParam(required = false) java.math.BigDecimal maxPrice,
                                 @RequestParam(required = false) String timeFilter,
                                 @RequestParam(required = false) String timeFrom,
                                 @RequestParam(required = false) String timeTo,
                                 @RequestParam(required = false) String createdFrom,
                                 @RequestParam(required = false) String createdTo,
                                 @RequestParam(required = false) String updatedFrom,
                                 @RequestParam(required = false) String updatedTo,
                                 @RequestParam(required = false) Boolean hasPendingApproval,
                                 @RequestParam(required = false) String approvalStatus,
                                 @RequestParam(required = false) String stockStatus,
                                 @RequestParam(required = false) Long brandId,
                                 @RequestParam(required = false) Long seasonId,
                                 @RequestParam(required = false) String seasonUuid,
                                 @RequestParam(required = false) String orderBy,
                                 @RequestParam(required = false) String orderType,
                                 @RequestParam(required = false) Long createdById,
                                 @RequestParam(required = false, defaultValue = "1") int page,
                                 @RequestParam(required = false, defaultValue = "20") int take,
                                 HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            return restSuccessHandle.handleSuccess(productService.searchForAdmin(new AdminProductQuery(status,
                    categorySlug, collectionSlug, search, minPrice, maxPrice, timeFilter, timeFrom, timeTo,
                    createdFrom, createdTo, updatedFrom, updatedTo, page, take, hasPendingApproval, approvalStatus, stockStatus, brandId,
                    seasonOf(seasonId, seasonUuid), orderBy, orderType, createdById)));
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }

    /** Số sản phẩm theo từng trạng thái vòng đời, cùng bộ lọc với danh sách (trừ status/page/take). */
    @GetMapping(UrlAdminConstant.Catalog.PRODUCTS + "/counts")
    public BaseRes productCounts(@RequestParam(required = false) String search,
                                  @RequestParam(required = false) String categorySlug,
                                  @RequestParam(required = false) String collectionSlug,
                                  @RequestParam(required = false) java.math.BigDecimal minPrice,
                                  @RequestParam(required = false) java.math.BigDecimal maxPrice,
                                  @RequestParam(required = false) String timeFilter,
                                  @RequestParam(required = false) String timeFrom,
                                  @RequestParam(required = false) String timeTo,
                                  @RequestParam(required = false) String createdFrom,
                                  @RequestParam(required = false) String createdTo,
                                  @RequestParam(required = false) String updatedFrom,
                                  @RequestParam(required = false) String updatedTo,
                                  @RequestParam(required = false) String stockStatus,
                                  @RequestParam(required = false) Long brandId,
                                  @RequestParam(required = false) Long seasonId,
                                  @RequestParam(required = false) String seasonUuid,
                                  @RequestParam(required = false) Long createdById,
                                  HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            return restSuccessHandle.handleSuccess(productService.countsForAdmin(new AdminProductQuery(null,
                    categorySlug, collectionSlug, search, minPrice, maxPrice, timeFilter, timeFrom, timeTo,
                    createdFrom, createdTo, updatedFrom, updatedTo, 1, 20, null, null, stockStatus, brandId,
                    seasonOf(seasonId, seasonUuid), null, null, createdById)));
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }

    @PostMapping(UrlAdminConstant.Catalog.PRODUCTS)
    @ResponseStatus(HttpStatus.CREATED)
    public BaseRes createProduct(@Valid @RequestBody AdminCreateProductRequestDto request,
                                  @org.springframework.security.core.annotation.AuthenticationPrincipal com.pk.core.identity.security.model.SecurityUser principal,
                                  HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            return restSuccessHandle.handleSuccess(productService.create(request, principal == null ? null : principal.getId()));
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }

    @GetMapping(UrlAdminConstant.Catalog.PRODUCTS + "/{productId}")
    public BaseRes productDetail(@PathVariable Long productId,
                                  HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            return restSuccessHandle.handleSuccess(productService.findByIdForAdmin(productId));
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }

    @PutMapping(UrlAdminConstant.Catalog.PRODUCTS + "/{productId}")
    public BaseRes updateProduct(@PathVariable Long productId,
                                  @Valid @RequestBody AdminUpdateProductRequestDto request,
                                  HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            return restSuccessHandle.handleSuccess(productService.update(productId, request));
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }

    /** 204 No Content: không có body nên không bọc AbstractRest/try-catch, giống các endpoint 204
     * khác của dự án (AuthRest.logout...) - lỗi (nếu có) rơi vào GlobalExceptionHandler. */
    @DeleteMapping(UrlAdminConstant.Catalog.PRODUCTS + "/{productId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteProduct(@PathVariable Long productId) {
        productService.softDelete(productId);
    }

    @PatchMapping(UrlAdminConstant.Catalog.PRODUCTS + "/{productId}/archive")
    public BaseRes archiveProduct(@PathVariable Long productId,
                                   HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            return restSuccessHandle.handleSuccess(productService.archive(productId));
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }

    @PatchMapping(UrlAdminConstant.Catalog.PRODUCTS + "/{productId}/discontinue")
    public BaseRes discontinueProduct(@PathVariable Long productId,
                                       HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            return restSuccessHandle.handleSuccess(productService.discontinue(productId));
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }

    /** Khôi phục từ ARCHIVED/DISCONTINUED về UNPUBLISHED. */
    @PatchMapping(UrlAdminConstant.Catalog.PRODUCTS + "/{productId}/restore")
    public BaseRes restoreProduct(@PathVariable Long productId,
                                   HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            return restSuccessHandle.handleSuccess(productService.restore(productId));
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }

    @PatchMapping(UrlAdminConstant.Catalog.PRODUCTS + "/{productId}/publish")
    public BaseRes publishProduct(@PathVariable Long productId,
                                  HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            return restSuccessHandle.handleSuccess(productService.publish(productId));
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }

    @PatchMapping(UrlAdminConstant.Catalog.PRODUCTS + "/{productId}/unpublish")
    public BaseRes unpublishProduct(@PathVariable Long productId,
                                    HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            return restSuccessHandle.handleSuccess(productService.unpublish(productId));
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }

    @PostMapping(UrlAdminConstant.Catalog.PRODUCTS + "/{productId}/variants")
    @ResponseStatus(HttpStatus.CREATED)
    public BaseRes addVariant(@PathVariable Long productId,
                              @Valid @RequestBody AdminSkuRequestDto request,
                              HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            return restSuccessHandle.handleSuccess(productService.addVariant(productId, request));
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }

    @PutMapping(UrlAdminConstant.Catalog.PRODUCTS + "/{productId}/variants/{skuId}")
    public BaseRes updateVariant(@PathVariable Long productId, @PathVariable Long skuId,
                                 @Valid @RequestBody AdminSkuRequestDto request,
                                 HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            return restSuccessHandle.handleSuccess(productService.updateVariant(productId, skuId, request));
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }

    @PostMapping(value = UrlAdminConstant.Catalog.UPLOAD, consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public BaseRes upload(@org.springframework.web.bind.annotation.RequestPart("file") org.springframework.web.multipart.MultipartFile file,
                          HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            return restSuccessHandle.handleSuccess(fileStorage.store(file));
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }

    @GetMapping(UrlAdminConstant.Catalog.PRODUCTS + "/{productId}/media")
    public BaseRes listMedia(@PathVariable Long productId,
                             HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            return restSuccessHandle.handleSuccess(mediaService.findByProduct(productId));
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }

    @PostMapping(UrlAdminConstant.Catalog.PRODUCTS + "/{productId}/media")
    @ResponseStatus(HttpStatus.CREATED)
    public BaseRes addMedia(@PathVariable Long productId, @Valid @RequestBody AdminProductMediaRequestDto request,
                            HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            return restSuccessHandle.handleSuccess(mediaService.add(productId, request));
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }

    @PutMapping(UrlAdminConstant.Catalog.PRODUCTS + "/{productId}/media/{mediaId}")
    public BaseRes updateMedia(@PathVariable Long productId, @PathVariable Long mediaId,
                               @Valid @RequestBody AdminProductMediaRequestDto request,
                               HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            return restSuccessHandle.handleSuccess(mediaService.update(productId, mediaId, request));
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }

    @DeleteMapping(UrlAdminConstant.Catalog.PRODUCTS + "/{productId}/media/{mediaId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMedia(@PathVariable Long productId, @PathVariable Long mediaId) {
        mediaService.delete(productId, mediaId);
    }

    @GetMapping(UrlAdminConstant.Catalog.PRODUCTS + "/{productId}/variants/{skuId}/attributes")
    public BaseRes variantAttributes(@PathVariable Long productId, @PathVariable Long skuId,
                                     HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            return restSuccessHandle.handleSuccess(productService.findVariantAttributes(productId, skuId));
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }

    @PutMapping(UrlAdminConstant.Catalog.PRODUCTS + "/{productId}/variants/{skuId}/attributes")
    public BaseRes setVariantAttributes(@PathVariable Long productId, @PathVariable Long skuId,
                                        @Valid @RequestBody AdminSkuAttributesRequestDto request,
                                        HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            return restSuccessHandle.handleSuccess(productService.setVariantAttributes(productId, skuId, request));
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }

    @GetMapping(UrlAdminConstant.Catalog.PRODUCTS + "/{productId}/variants")
    public BaseRes productVariants(@PathVariable Long productId,
                                    HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            return restSuccessHandle.handleSuccess(productService.findVariantsByProductId(productId));
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }

    // ---------- Variants (tìm chung, không theo 1 sản phẩm) ----------

    @GetMapping(UrlAdminConstant.Catalog.VARIANTS)
    public BaseRes searchVariants(@RequestParam(required = false) String search,
                                   HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            return restSuccessHandle.handleSuccess(productService.searchVariants(search));
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }

    // ---------- Categories ----------

    @GetMapping(UrlAdminConstant.Catalog.CATEGORIES)
    public BaseRes listCategories(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            return restSuccessHandle.handleSuccess(categoryService.findAllForAdmin());
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }

    @PostMapping(UrlAdminConstant.Catalog.CATEGORIES)
    @ResponseStatus(HttpStatus.CREATED)
    public BaseRes createCategory(@Valid @RequestBody AdminCategoryRequestDto request,
                                   HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            return restSuccessHandle.handleSuccess(categoryService.create(request));
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }

    /** 204 No Content (lỗi 404/409 rơi vào GlobalExceptionHandler, giống deleteProduct). */
    @DeleteMapping(UrlAdminConstant.Catalog.CATEGORIES + "/{categoryId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCategory(@PathVariable Long categoryId) {
        categoryService.delete(categoryId);
    }

    @PutMapping(UrlAdminConstant.Catalog.CATEGORIES + "/{categoryId}")
    public BaseRes updateCategory(@PathVariable Long categoryId,
                                   @Valid @RequestBody AdminCategoryRequestDto request,
                                   HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            return restSuccessHandle.handleSuccess(categoryService.update(categoryId, request));
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }

    // ---------- Collections ----------

    @GetMapping(UrlAdminConstant.Catalog.COLLECTIONS)
    public BaseRes listCollections(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            return restSuccessHandle.handleSuccess(collectionService.findAllForAdmin());
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }

    @PostMapping(UrlAdminConstant.Catalog.COLLECTIONS)
    @ResponseStatus(HttpStatus.CREATED)
    public BaseRes createCollection(@Valid @RequestBody AdminCollectionRequestDto request,
                                     HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            return restSuccessHandle.handleSuccess(collectionService.create(request));
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }

    /** 204 No Content (lỗi 404/409 rơi vào GlobalExceptionHandler, giống deleteProduct). */
    @DeleteMapping(UrlAdminConstant.Catalog.COLLECTIONS + "/{collectionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCollection(@PathVariable Long collectionId) {
        collectionService.delete(collectionId);
    }

    @PutMapping(UrlAdminConstant.Catalog.COLLECTIONS + "/{collectionId}")
    public BaseRes updateCollection(@PathVariable Long collectionId,
                                     @Valid @RequestBody AdminCollectionRequestDto request,
                                     HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            return restSuccessHandle.handleSuccess(collectionService.update(collectionId, request));
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }

    @PostMapping(UrlAdminConstant.Catalog.COLLECTIONS + "/{collectionId}/products")
    public BaseRes addCollectionProducts(@PathVariable Long collectionId,
                                         @Valid @RequestBody AdminCollectionProductsRequestDto request,
                                         HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            return restSuccessHandle.handleSuccess(collectionService.addProducts(collectionId, request.getProductIds()));
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }

    @DeleteMapping(UrlAdminConstant.Catalog.COLLECTIONS + "/{collectionId}/products/{productId}")
    public BaseRes removeCollectionProduct(@PathVariable Long collectionId, @PathVariable Long productId,
                                           HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            return restSuccessHandle.handleSuccess(collectionService.removeProduct(collectionId, productId));
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }

    // ---------- Attributes ----------

    @GetMapping(UrlAdminConstant.Catalog.ATTRIBUTES)
    public BaseRes listAttributes(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            return restSuccessHandle.handleSuccess(attributeService.findAll());
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }

    @PostMapping(UrlAdminConstant.Catalog.ATTRIBUTES)
    @ResponseStatus(HttpStatus.CREATED)
    public BaseRes createAttribute(@Valid @RequestBody AdminAttributeRequestDto request,
                                    HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            return restSuccessHandle.handleSuccess(attributeService.create(request));
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }

    /** 204 No Content (lỗi 404/409 rơi vào GlobalExceptionHandler, giống deleteProduct). */
    @DeleteMapping(UrlAdminConstant.Catalog.ATTRIBUTES + "/{attributeId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAttribute(@PathVariable Long attributeId) {
        attributeService.delete(attributeId);
    }

    @PutMapping(UrlAdminConstant.Catalog.ATTRIBUTES + "/{attributeId}")
    public BaseRes updateAttribute(@PathVariable Long attributeId,
                                    @Valid @RequestBody AdminAttributeRequestDto request,
                                    HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            return restSuccessHandle.handleSuccess(attributeService.update(attributeId, request));
        } catch (Exception ex) {
            return restErrorHandle.handleException(ex, httpRequest, httpResponse);
        }
    }

    /** FE gọi mùa là `seasonUuid` (chuỗi); BE dùng id số nên chấp nhận cả `seasonId` và `seasonUuid` dạng số. */
    private static Long seasonOf(Long seasonId, String seasonUuid) {
        if (seasonId != null || seasonUuid == null || seasonUuid.isBlank()) {
            return seasonId;
        }
        try {
            return Long.parseLong(seasonUuid.trim());
        } catch (NumberFormatException ex) {
            throw com.pk.core.common.exception.BusinessException.badRequest("VALIDATION_FAILED",
                    "seasonUuid phải là id số của mùa: " + seasonUuid, seasonUuid);
        }
    }
}
