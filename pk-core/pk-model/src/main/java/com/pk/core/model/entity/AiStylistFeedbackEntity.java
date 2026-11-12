package com.pk.core.model.entity;

import com.pk.core.model.constant.TableConstant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import vn.com.unit.miragesql.miragesql.annotation.Column;
import vn.com.unit.miragesql.miragesql.annotation.PrimaryKey;
import vn.com.unit.miragesql.miragesql.annotation.PrimaryKey.GenerationType;
import vn.com.unit.miragesql.miragesql.annotation.Table;

import java.util.Date;

/** Thích / không thích một sản phẩm trong phiên. Unique (session_id, product_id) — ghi đè bằng upsert. */
@Getter
@Setter
@NoArgsConstructor
@Table(name = TableConstant.AI_STYLIST_FEEDBACK)
public class AiStylistFeedbackEntity {

    @Id
    @PrimaryKey(generationType = GenerationType.SEQUENCE, generator = "ai_stylist_feedback_id_seq")
    @Column(name = "id")
    private Long id;

    @Column(name = "session_id")
    private Long sessionId;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "product_id")
    private Long productId;

    @Column(name = "liked")
    private boolean liked;

    @Column(name = "created_date")
    private Date createdDate = new Date();

    @Column(name = "updated_date")
    private Date updatedDate;
}
