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

/** Điều kiện / quyền lợi của schema: config chuẩn hoá + giá trị form để dựng lại màn hình. */
@Getter
@Setter
@NoArgsConstructor
@Table(name = TableConstant.PROMO_SCHEMA_COMPONENTS)
public class PromoSchemaComponentEntity extends UpdateEntity {

    @Id
    @PrimaryKey(generationType = GenerationType.SEQUENCE, generator = "promo_schema_components_id_seq")
    @Column(name = "id")
    private Long id;

    @Column(name = "schema_id")
    private Long schemaId;

    @Column(name = "kind")
    private String kind;

    @Column(name = "config")
    private String config;

    @Column(name = "authoring_values")
    private String authoringValues;

    @Column(name = "sort_order")
    private int sortOrder;

}
