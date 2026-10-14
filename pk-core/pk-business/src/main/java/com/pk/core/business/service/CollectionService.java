package com.pk.core.business.service;

import com.pk.core.model.dto.request.AdminCollectionRequestDto;
import com.pk.core.model.dto.response.CollectionResponseDto;

import java.util.List;

public interface CollectionService {

    /** Bộ sưu tập đã publish (GET /storefront/product/collections). */
    List<CollectionResponseDto> findAllPublished();

    /** Toàn bộ bộ sưu tập (kể cả DRAFT) - Admin Catalog mục J: GET /admin/catalog/collections. */
    List<CollectionResponseDto> findAllForAdmin();

    /** Tạo bộ sưu tập - Admin Catalog mục J: POST /admin/catalog/collections. */
    CollectionResponseDto create(AdminCollectionRequestDto req);

    /** Sửa bộ sưu tập - Admin Catalog mục J: PUT /admin/catalog/collections/{collectionId}. */
    CollectionResponseDto update(Long collectionId, AdminCollectionRequestDto req);

    /** Thêm sản phẩm vào bộ sưu tập (bỏ qua id đã có) - POST /admin/catalog/collections/{collectionId}/products. */
    CollectionResponseDto addProducts(Long collectionId, List<Long> productIds);

    /** Bỏ sản phẩm khỏi bộ sưu tập - DELETE /admin/catalog/collections/{collectionId}/products/{productId}. */
    CollectionResponseDto removeProduct(Long collectionId, Long productId);

    /** Xoá bộ sưu tập (gỡ liên kết sản phẩm, KHÔNG xoá sản phẩm) - DELETE /admin/catalog/collections/{collectionId}. */
    void delete(Long collectionId);
}
