package com.pk.core.business.repository;

import com.pk.core.model.entity.ProductEntity;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface ProductRepo extends PkRepo<ProductEntity, Long> {

    /** Trả null nếu không có. */
    ProductEntity findBySlug(@Param("slug") String slug);

    /** Trả null nếu không có - dùng kiểm tra trùng `code` lúc tạo sản phẩm (409 DUPLICATE_CODE,
     * Admin Catalog mục J). CHƯA lọc deleted_date (mã sản phẩm đã xoá mềm vẫn coi là trùng, không
     * cho tạo lại trùng code - tránh 2 sản phẩm khác nhau cùng 1 mã dù 1 cái đã xoá). */
    ProductEntity findByCode(@Param("code") String code);

    /** Số sản phẩm thuộc danh mục, TÍNH CẢ đã xoá mềm (FK category_id vẫn còn nên không xoá cứng được danh mục). */
    Long countByCategoryId(@Param("categoryId") Long categoryId);

    /** Danh sách sản phẩm cho ADMIN (Admin Catalog mục J: GET /admin/catalog/products) - KHÔNG giới
     * hạn status=PUBLISHED như {@link #searchPublished} storefront, loại trừ deleted_date. Lọc rỗng
     * ("" cho mọi tham số chuỗi, kể cả status) = không lọc, cùng quy ước tránh cú pháp IF/END của
     * Mirage như searchPublished. KHÔNG phân trang/sắp xếp ở SQL - ProductServiceImpl tự xử lý bằng
     * Java (PageResponse.paginate), giống searchPublished. */
    List<ProductEntity> searchAdmin(@Param("status") String status, @Param("categorySlug") String categorySlug,
                                     @Param("collectionSlug") String collectionSlug, @Param("keyword") String keyword);

    /** Sản phẩm PUBLISHED xếp theo số lượng bán giảm dần (đơn CONFIRMED/SHIPPING/DELIVERED, đã trừ hàng trả) - dùng cho sort BEST_SELLING. */
    List<ProductEntity> findPublishedBySold();

    /** Sản phẩm PUBLISHED có ít nhất 1 lượt yêu thích, xếp theo số lượt giảm dần (carousel MOST_FAVORITED). */
    List<ProductEntity> findPublishedByFavorites();

    /** Chỉ trả sản phẩm PUBLISHED. Lọc rỗng ("" cho chuỗi, 0/số rất lớn cho khoảng giá) = không lọc
     * (tránh dùng khối IF/END của Mirage 2-way SQL vì chưa có tiền lệ đã kiểm chứng trong dự án -
     * xem ProductRepo_searchPublished.sql). Theo spec FE (document/09-tong-hop-api-fe.md mục C):
     * hỗ trợ search/category/collection/material/minPrice/maxPrice; CHƯA hỗ trợ gemstone (thuộc
     * SKU, không phải product) và rating (chưa có cột) - cố ý bỏ qua đợt này.
     * KHÔNG phân trang/sắp xếp ở SQL - trả hết kết quả khớp, ProductServiceImpl tự sort + cắt
     * trang bằng Java (quy mô danh mục đồ án không lớn, đủ an toàn hơn là đoán cú pháp
     * LIMIT/OFFSET/ORDER BY động chưa từng dùng trong dự án). */
    List<ProductEntity> searchPublished(@Param("categorySlug") String categorySlug,
                                         @Param("collectionSlug") String collectionSlug,
                                         @Param("keyword") String keyword,
                                         @Param("material") String material,
                                         @Param("minPrice") BigDecimal minPrice,
                                         @Param("maxPrice") BigDecimal maxPrice);
}
