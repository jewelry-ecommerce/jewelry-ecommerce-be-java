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

/** Thành phần cấp chiến dịch: TARGET / AUDIENCE / APPLICABILITY, config là JSON. */
@Getter
@Setter
@NoArgsConstructor
@Table(name = TableConstant.PROMO_DRAFT_COMPONENTS)
public class PromoDraftComponentEntity extends UpdateEntity {

    @Id
    @PrimaryKey(generationType = GenerationType.SEQUENCE, generator = "promo_draft_components_id_seq")
    @Column(name = "id")
    private Long id;

    @Column(name = "draft_id")
    private Long draftId;

    @Column(name = "kind")
    private String kind;

    @Column(name = "config")
    private String config;

    @Column(name = "sort_order")
    private int sortOrder;

}
