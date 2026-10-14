package com.pk.core.business.repository;

import vn.com.unit.springframework.data.mirage.repository.query.Modifying;
import com.pk.core.model.entity.CollectionEntity;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CollectionRepo extends PkRepo<CollectionEntity, Long> {

    /** Trả null nếu không có. */
    CollectionEntity findBySlug(@Param("slug") String slug);

    List<CollectionEntity> findAllPublished();

    /** Các collection (đã publish hoặc chưa) mà sản phẩm thuộc về - dùng để dựng trường
     * `collections[]` của ProductResponseDto theo spec FE. */
    List<CollectionEntity> findByProductId(@Param("productId") Long productId);

    /** Thêm sản phẩm vào bộ sưu tập (bỏ qua nếu đã có). Trả 1 nếu thêm mới, 0 nếu đã có hoặc bộ sưu tập không tồn tại. */
    @Modifying
    int addProduct(@Param("collectionId") Long collectionId, @Param("productId") Long productId);

    /** Bỏ sản phẩm khỏi bộ sưu tập. Trả 1 nếu có xoá, 0 nếu không có quan hệ. */
    /** Gỡ toàn bộ sản phẩm khỏi bộ sưu tập (trước khi xoá bộ sưu tập). */
    @Modifying
    int removeAllProducts(@Param("collectionId") Long collectionId);

    @Modifying
    int removeProduct(@Param("collectionId") Long collectionId, @Param("productId") Long productId);
}
