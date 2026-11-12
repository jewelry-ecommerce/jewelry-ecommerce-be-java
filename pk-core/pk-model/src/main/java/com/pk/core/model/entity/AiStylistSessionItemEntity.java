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

import java.math.BigDecimal;

/** Một sản phẩm thuộc phiên AI Stylist. Không kế thừa audit vì bảng V40 không có các cột đó. */
@Getter
@Setter
@NoArgsConstructor
@Table(name = TableConstant.AI_STYLIST_SESSION_ITEMS)
public class AiStylistSessionItemEntity {

    @Id
    @PrimaryKey(generationType = GenerationType.SEQUENCE, generator = "ai_stylist_session_items_id_seq")
    @Column(name = "id")
    private Long id;

    @Column(name = "session_id")
    private Long sessionId;

    @Column(name = "product_id")
    private Long productId;

    @Column(name = "sku_id")
    private Long skuId;

    @Column(name = "reason")
    private String reason;

    @Column(name = "match_score")
    private BigDecimal matchScore;

    @Column(name = "sort_order")
    private int sortOrder;
}
