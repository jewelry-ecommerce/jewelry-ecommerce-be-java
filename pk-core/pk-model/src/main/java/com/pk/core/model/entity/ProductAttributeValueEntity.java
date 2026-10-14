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

/** Giá trị của một thuộc tính (id ổn định, code, tên hiển thị `value`, ảnh tuỳ chọn). SKU lưu giá trị theo chuỗi `value`. */
@Getter
@Setter
@NoArgsConstructor
@Table(name = TableConstant.PRODUCT_ATTRIBUTE_VALUES)
public class ProductAttributeValueEntity extends UpdateEntity {

    @Id
    @PrimaryKey(generationType = GenerationType.SEQUENCE, generator = "product_attribute_values_id_seq")
    @Column(name = "id")
    private Long id;

    @Column(name = "attribute_id")
    private Long attributeId;

    @Column(name = "code")
    private String code;

    @Column(name = "value")
    private String value;

    @Column(name = "image_url")
    private String imageUrl;

    @Column(name = "sort_order")
    private int sortOrder;
}
