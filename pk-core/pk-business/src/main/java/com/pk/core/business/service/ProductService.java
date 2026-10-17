package com.pk.core.business.service;

import com.pk.core.common.web.PageResponse;
import com.pk.core.model.dto.request.AdminCreateProductRequestDto;
import com.pk.core.model.dto.request.AdminSkuRequestDto;
import com.pk.core.model.dto.request.AdminUpdateProductRequestDto;
import com.pk.core.model.dto.response.ProductCountsDto;
import com.pk.core.model.dto.response.ProductFilterSectionDto;
import com.pk.core.model.dto.response.ProductResponseDto;
import com.pk.core.model.dto.response.ProductSkuResponseDto;

import java.math.BigDecimal;
import java.util.List;

public interface ProductService {

    /** Chi tiết sản phẩm theo slug. Chỉ trả sản phẩm PUBLISHED (draft/archived -> 404) -
     * GET /storefront/product/{slug}. */
    ProductResponseDto findBySlug(String slug);

    /** Sản phẩm PUBLISHED (và danh mục hiển thị) theo id cho storefront; null nếu không còn bán. */
    ProductResponseDto findPublishedById(Long productId);

    /** Danh sách/tìm kiếm sản phẩm - GET /storefront/product. Tham số rỗng/null = không lọc
     * (categorySlug, collectionSlug, keyword, material truyền "" hoặc null đều được coi là bỏ qua;
     * minPrice/maxPrice truyền null sẽ tự lấy khoảng mặc định [0, vô cực]).
     * sort: NEWEST (mặc định) | PRICE_ASC | PRICE_DESC. RATING_DESC có trong spec FE nhưng DB
     * chưa có cột rating -> tạm coi như NEWEST (chưa làm đợt này).
     * page: 1-based (mặc định 1). take: mặc định 20, tối đa 100 (quy ước chung, xem PageResponse.paginate). */
    PageResponse<ProductResponseDto> search(String categorySlug, String collectionSlug, String keyword,
                                             String material, BigDecimal minPrice, BigDecimal maxPrice,
                                             String sort, int page, int take);

    /** Tìm sản phẩm storefront đầy đủ: nhiều danh mục (kèm danh mục con), lọc theo giá trị thuộc tính, tồn kho, giá hiển
     * thị. sort: NEWEST | PRICE_ASC | PRICE_DESC | NAME | NAME_DESC | BEST_SELLING (RATING_DESC chưa có dữ liệu -> NEWEST). */
    PageResponse<ProductResponseDto> search(ProductSearchQuery query);

    /** Bộ lọc storefront (facets) theo phạm vi tìm hiện tại - GET /storefront/product/filters. Chỉ trả thuộc tính ACTIVE
     * dạng SELECT/MULTISELECT, mỗi giá trị kèm số sản phẩm khớp (bỏ giá trị 0 sản phẩm). Bỏ qua điều kiện attributes. */
    List<ProductFilterSectionDto> filters(ProductSearchQuery query);

    // ===================== ADMIN (mục J spec) =====================

    /** Danh sách sản phẩm cho admin, KHÔNG giới hạn PUBLISHED - GET /admin/catalog/products.
     * Tham số rỗng/null = không lọc (status/categorySlug/collectionSlug/keyword), cùng quy ước
     * search() storefront. page 1-based, take mặc định 20 tối đa 100. */
    PageResponse<ProductResponseDto> searchForAdmin(String status, String categorySlug, String collectionSlug,
                                                     String keyword, int page, int take);

    /** Danh sách admin đầy đủ bộ lọc (trạng thái, danh mục, bộ sưu tập, từ khoá, giá, thời gian). */
    PageResponse<ProductResponseDto> searchForAdmin(AdminProductQuery query);

    /** Số sản phẩm theo từng trạng thái vòng đời với cùng bộ lọc (bỏ qua status/page/take). */
    ProductCountsDto countsForAdmin(AdminProductQuery query);

    /** DISCONTINUED: ngừng kinh doanh (từ PUBLISHED hoặc UNPUBLISHED). */
    ProductResponseDto discontinue(Long productId);

    /** Khôi phục từ ARCHIVED hoặc DISCONTINUED về UNPUBLISHED (chưa bán lại cho tới khi publish). */
    ProductResponseDto restore(Long productId);

    /** Chi tiết để sửa (bao gồm mọi status, kể cả DRAFT/ARCHIVED) - GET /admin/catalog/products/{id}.
     * 404 nếu không có hoặc đã xoá mềm. */
    ProductResponseDto findByIdForAdmin(Long productId);

    /** Tạo sản phẩm + SKU - POST /admin/catalog/products. 409 DUPLICATE_CODE/DUPLICATE_SLUG/
     * DUPLICATE_SKU nếu trùng. */
    default ProductResponseDto create(AdminCreateProductRequestDto req) {
        return create(req, null);
    }

    /** Tạo sản phẩm, ghi người tạo (actorId = id tài khoản admin; null nếu không rõ). */
    ProductResponseDto create(AdminCreateProductRequestDto req, Long actorId);

    /** Cập nhật sản phẩm - PUT /admin/catalog/products/{id}. KHÔNG đổi variants (xem
     * AdminUpdateProductRequestDto javadoc). */
    ProductResponseDto update(Long productId, AdminUpdateProductRequestDto req);

    /** Xoá mềm (set deletedId/deletedDate) - DELETE /admin/catalog/products/{id}. Sau khi xoá mềm,
     * sản phẩm không còn xuất hiện ở bất kỳ truy vấn storefront/admin nào (searchAdmin/searchPublished/
     * findBySlug đều lọc deleted_date IS NULL). */
    void softDelete(Long productId);

    /** Archive/unpublish - PATCH /admin/catalog/products/{id}/archive. Chuyển status -> ARCHIVED
     * (không xoá dữ liệu, chỉ ẩn khỏi storefront vì storefront chỉ hiển thị PUBLISHED). */
    ProductResponseDto archive(Long productId);

    /** SKU thuộc 1 sản phẩm - GET /admin/catalog/products/{id}/variants. 404 nếu sản phẩm không có. */
    List<ProductSkuResponseDto> findVariantsByProductId(Long productId);

    /** Tìm SKU theo mã/tên (không giới hạn theo sản phẩm) - GET /admin/catalog/variants. */
    List<ProductSkuResponseDto> searchVariants(String keyword);

    /** Đưa sản phẩm lên bán: cần ít nhất 1 SKU chưa ARCHIVED có giá niêm yết > 0; các SKU DRAFT chuyển PUBLISHED,
     * bảo đảm có 1 SKU mặc định - PATCH /admin/catalog/products/{productId}/publish. */
    ProductResponseDto publish(Long productId);

    /** Ném 400 nếu sản phẩm chưa đủ điều kiện publish (chưa có SKU đang bán hoặc SKU giá <= 0). Không đổi dữ liệu. */
    void assertPublishable(Long productId);

    /** Publish do người duyệt đã phê duyệt (bỏ qua cờ app.pim.require-approval và khoá chờ duyệt). Chỉ ProductApprovalService gọi. */
    ProductResponseDto publishApproved(Long productId);

    /** Gỡ sản phẩm về DRAFT (không bán) - PATCH /admin/catalog/products/{productId}/unpublish. */
    ProductResponseDto unpublish(Long productId);

    /** Thêm SKU cho sản phẩm đã tạo (SKU mới ở DRAFT nếu không gửi status) - POST /admin/catalog/products/{productId}/variants. */
    ProductSkuResponseDto addVariant(Long productId, AdminSkuRequestDto req);

    /** Sửa SKU (giá, size, mặc định, status...). Tồn kho chỉ đổi qua inventory - PUT /admin/catalog/products/{productId}/variants/{skuId}. */
    ProductSkuResponseDto updateVariant(Long productId, Long skuId, AdminSkuRequestDto req);

    /** Thuộc tính đã gán cho SKU - GET /admin/catalog/products/{productId}/variants/{skuId}/attributes. */
    java.util.List<java.util.Map<String, Object>> findVariantAttributes(Long productId, Long skuId);

    /** Thay toàn bộ thuộc tính của SKU - PUT /admin/catalog/products/{productId}/variants/{skuId}/attributes. */
    ProductSkuResponseDto setVariantAttributes(Long productId, Long skuId,
                                               com.pk.core.model.dto.request.AdminSkuAttributesRequestDto req);
}
