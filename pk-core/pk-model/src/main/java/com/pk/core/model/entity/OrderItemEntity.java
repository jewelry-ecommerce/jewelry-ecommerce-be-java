package com.pk.core.model.entity;

import com.pk.core.model.constant.TableConstant;
import com.pk.core.common.entity.CreateEntity;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import vn.com.unit.miragesql.miragesql.annotation.Column;
import vn.com.unit.miragesql.miragesql.annotation.PrimaryKey;
import vn.com.unit.miragesql.miragesql.annotation.PrimaryKey.GenerationType;
import vn.com.unit.miragesql.miragesql.annotation.Table;

import java.math.BigDecimal;

/** Dòng sản phẩm trong đơn - SAO CHÉP name/imageUrl/unitPrice tại thời điểm đặt hàng (không tham
 * chiếu sống tới products/product_skus): giá/tên sản phẩm đổi sau này không ảnh hưởng đơn đã đặt.
 * Bảng `order_items` chỉ có created_id/created_date (không có updated) nên extends CreateEntity. */
@Getter
@Setter
@NoArgsConstructor
@Table(name = TableConstant.ORDER_ITEMS)
public class OrderItemEntity extends CreateEntity {

    @Id
    @PrimaryKey(generationType = GenerationType.SEQUENCE, generator = "order_items_id_seq")
    @Column(name = "id")
    private Long id;

    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "product_id")
    private Long productId;

    @Column(name = "sku_id")
    private Long skuId;

    @Column(name = "name")
    private String name;

    @Column(name = "image_url")
    private String imageUrl;

    @Column(name = "quantity")
    private int quantity;

    @Column(name = "unit_price")
    private BigDecimal unitPrice;

    @Column(name = "line_total")
    private BigDecimal lineTotal;

    /** Số lượng đã trả lại của dòng này (0..quantity). */
    @Column(name = "returned_quantity")
    private int returnedQuantity;

    public OrderItemEntity(Long orderId, Long productId, Long skuId, String name, String imageUrl,
                            int quantity, BigDecimal unitPrice) {
        this.orderId = orderId;
        this.productId = productId;
        this.skuId = skuId;
        this.name = name;
        this.imageUrl = imageUrl;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.lineTotal = unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
}
