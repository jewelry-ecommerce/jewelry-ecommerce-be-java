package com.pk.core.business.repository;

import com.pk.core.model.entity.PromotionEntity;
import org.springframework.data.repository.query.Param;
import vn.com.unit.springframework.data.mirage.repository.query.Modifying;

import java.util.Date;
import java.util.List;

public interface PromotionRepo extends PkRepo<PromotionEntity, Long> {

    /** Bảng nối promotion_products KHÔNG có entity riêng (giống user_roles/UserRepo.addRole) - 3
     * method dưới đây thao tác trực tiếp bảng nối, đặt ở đây (repo của bảng "chủ") thay vì tạo repo
     * riêng cho bảng nối (1 interface repo trống nghĩa không extends PkRepo sẽ KHÔNG được
     * @EnableMirageRepositories nhận diện - đã thử và sửa lại theo đúng tiền lệ UserRepo). */
    /** Các khuyến mãi PUBLISHED, chưa xoá mềm, đang trong thời hạn tại `now` và áp cho `productId`. */
    List<PromotionEntity> findActiveByProductId(@Param("productId") Long productId, @Param("now") Date now);

    List<Long> findProductIdsByPromotionId(@Param("promotionId") Long promotionId);

    /** Id sản phẩm thuộc ít nhất 1 khuyến mãi đang hiệu lực tại `now` (carousel CAMPAIGN không chọn chương trình). */
    List<Long> findActiveProductIds(@Param("now") Date now);

    @Modifying
    int addProduct(@Param("promotionId") Long promotionId, @Param("productId") Long productId);

    /** Xoá hết liên kết cũ trước khi ghi lại danh sách sản phẩm mới lúc update (thay toàn bộ, không
     * merge từng phần - đơn giản hơn so với diff thêm/bớt). */
    @Modifying
    int deleteAllByPromotionId(@Param("promotionId") Long promotionId);
}
