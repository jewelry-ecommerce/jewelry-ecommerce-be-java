package com.pk.core.model.entity;

import com.pk.core.common.entity.CreateEntity;
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
import java.util.Date;

/** Dòng hàng của yêu cầu đổi/trả (kind RETURN = hàng trả, EXCHANGE = hàng đổi). */
@Getter
@Setter
@NoArgsConstructor
@Table(name = TableConstant.ORDER_RETURN_ITEMS)
public class OrderReturnItemEntity extends CreateEntity {

    @Id
    @PrimaryKey(generationType = GenerationType.SEQUENCE, generator = "order_return_items_id_seq")
    @Column(name = "id")
    private Long id;

    @Column(name = "return_id")
    private Long returnId;

    @Column(name = "kind")
    private String kind;

    @Column(name = "order_item_id")
    private Long orderItemId;

    @Column(name = "sku_id")
    private Long skuId;

    @Column(name = "product_id")
    private Long productId;

    @Column(name = "product_name")
    private String productName;

    @Column(name = "variation_name")
    private String variationName;

    @Column(name = "sku_code")
    private String skuCode;

    @Column(name = "image_url")
    private String imageUrl;

    @Column(name = "unit_price")
    private BigDecimal unitPrice = BigDecimal.ZERO;

    @Column(name = "quantity")
    private int quantity;

    @Column(name = "line_total")
    private BigDecimal lineTotal = BigDecimal.ZERO;
}
