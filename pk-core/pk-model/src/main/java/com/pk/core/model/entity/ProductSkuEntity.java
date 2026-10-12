package com.pk.core.model.entity;

import com.pk.core.model.constant.TableConstant;
import com.pk.core.common.entity.BaseEntity;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import vn.com.unit.miragesql.miragesql.annotation.Column;
import vn.com.unit.miragesql.miragesql.annotation.PrimaryKey;
import vn.com.unit.miragesql.miragesql.annotation.PrimaryKey.GenerationType;
import vn.com.unit.miragesql.miragesql.annotation.Table;

import java.math.BigDecimal;

/** Biến thể (SKU) của sản phẩm - tồn kho theo SKU: available = onHand - reserved (ADR, xem
 * document/07-lo-trinh-database.md). */
@Getter
@Setter
@NoArgsConstructor
@Table(name = TableConstant.PRODUCT_SKUS)
public class ProductSkuEntity extends BaseEntity {

    public static final String DRAFT = "DRAFT";
    public static final String PUBLISHED = "PUBLISHED";
    public static final String ARCHIVED = "ARCHIVED";

    @Id
    @PrimaryKey(generationType = GenerationType.SEQUENCE, generator = "product_skus_id_seq")
    @Column(name = "id")
    private Long id;

    @Column(name = "product_id")
    private Long productId;

    @Column(name = "sku_code")
    private String skuCode;

    @Column(name = "name")
    private String name;

    @Column(name = "status")
    private String status = DRAFT;

    @Column(name = "material")
    private String material;

    @Column(name = "gemstone")
    private String gemstone;

    @Column(name = "size_label")
    private String sizeLabel;

    @Column(name = "metal_color_label")
    private String metalColorLabel;

    @Column(name = "carat_weight")
    private BigDecimal caratWeight;

    @Column(name = "weight_gram")
    private BigDecimal weightGram;

    @Column(name = "list_price")
    private BigDecimal listPrice;

    @Column(name = "sale_price")
    private BigDecimal salePrice;

    /** Giá vốn (admin-only, V18). */
    @Column(name = "cost_price")
    private BigDecimal costPrice;

    /** Giá bán sỉ B2B (admin-only, V18). */
    @Column(name = "b2b_wholesale_price")
    private BigDecimal b2bWholesalePrice;

    /** Thuế suất riêng (%) của SKU, null = theo sản phẩm/mặc định (V18). */
    @Column(name = "tax_rate_override")
    private BigDecimal taxRateOverride;

    @Column(name = "length")
    private BigDecimal length;

    @Column(name = "width")
    private BigDecimal width;

    @Column(name = "height")
    private BigDecimal height;

    @Column(name = "on_hand")
    private int onHand;

    @Column(name = "reserved")
    private int reserved;

    @Column(name = "is_default")
    private boolean isDefault;

    @Column(name = "version")
    private long version;

    public ProductSkuEntity(String skuCode, String sizeLabel, BigDecimal listPrice, int onHand) {
        this.skuCode = skuCode;
        this.sizeLabel = sizeLabel;
        this.listPrice = listPrice;
        this.onHand = onHand;
    }

    /** Giá thực bán: salePrice nếu có, ngược lại listPrice. */
    public BigDecimal effectivePrice() {
        return salePrice != null ? salePrice : listPrice;
    }

    /** Tồn khả dụng, không âm (pre-order có thể làm reserved vượt onHand). */
    public int available() {
        return Math.max(0, onHand - reserved);
    }

    /** OUT_OF_STOCK / LOW_STOCK (<=5) / IN_STOCK - ngưỡng tạm cố định, chưa cấu hình được. */
    public String stockStatus() {
        int avail = available();
        if (avail <= 0) {
            return "OUT_OF_STOCK";
        }
        return avail <= 5 ? "LOW_STOCK" : "IN_STOCK";
    }
}
