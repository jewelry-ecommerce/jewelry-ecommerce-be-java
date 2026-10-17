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

import java.util.Date;

/** Sản phẩm ghim ở ô tìm kiếm (V31). keywordPattern null = ghim landing. */
@Getter
@Setter
@NoArgsConstructor
@Table(name = TableConstant.SEARCH_PINNED_PRODUCTS)
public class SearchPinnedProductEntity extends UpdateEntity {

    @Id
    @PrimaryKey(generationType = GenerationType.SEQUENCE, generator = "search_pinned_products_id_seq")
    @Column(name = "id")
    private Long id;

    @Column(name = "keyword_pattern")
    private String keywordPattern;

    @Column(name = "product_id")
    private Long productId;

    @Column(name = "position")
    private int position;

    @Column(name = "start_date")
    private Date startDate;

    @Column(name = "end_date")
    private Date endDate;

    @Column(name = "is_active")
    private boolean active = true;
}
