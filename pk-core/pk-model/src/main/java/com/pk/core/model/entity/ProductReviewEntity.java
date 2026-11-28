package com.pk.core.model.entity;

import com.pk.core.common.entity.UpdateEntity;
import com.pk.core.model.constant.TableConstant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import vn.com.unit.miragesql.miragesql.annotation.Column;
import vn.com.unit.miragesql.miragesql.annotation.PrimaryKey;
import vn.com.unit.miragesql.miragesql.annotation.PrimaryKey.GenerationType;
import vn.com.unit.miragesql.miragesql.annotation.Table;

/** Đánh giá sản phẩm — khách tạo, admin duyệt/từ chối/reply. */
@Getter
@Setter
@NoArgsConstructor
@Table(name = TableConstant.PRODUCT_REVIEWS)
public class ProductReviewEntity extends UpdateEntity {

    public static final String PENDING = "PENDING";
    public static final String APPROVED = "APPROVED";
    public static final String REJECTED = "REJECTED";

    @Id
    @PrimaryKey(generationType = GenerationType.SEQUENCE, generator = "product_reviews_id_seq")
    @Column(name = "id")
    private Long id;

    @Column(name = "product_id")
    private Long productId;

    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "author_name")
    private String authorName = "";

    @Column(name = "rating")
    private Integer rating;

    @Column(name = "title")
    private String title = "";

    @Column(name = "content")
    private String content = "";

    @Column(name = "images_json")
    private String imagesJson = "[]";

    @Column(name = "status")
    private String status = PENDING;

    @Column(name = "admin_reply")
    private String adminReply;

    @Column(name = "rejected_reason")
    private String rejectedReason;
}
