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

/** Sản phẩm phối kèm (mix & match) của một sản phẩm. */
@Getter
@Setter
@NoArgsConstructor
@Table(name = TableConstant.PRODUCT_MIX_MATCHES)
public class ProductMixMatchEntity extends CreateEntity {

    @Id
    @PrimaryKey(generationType = GenerationType.SEQUENCE, generator = "product_mix_matches_id_seq")
    @Column(name = "id")
    private Long id;

    @Column(name = "product_id")
    private Long productId;

    @Column(name = "related_product_id")
    private Long relatedProductId;

    @Column(name = "sort_order")
    private int sortOrder;
}
