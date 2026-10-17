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

/** Hình thức khuyến mãi của chiến dịch (MARKDOWN / FLAT_PRICE / VOUCHER). */
@Getter
@Setter
@NoArgsConstructor
@Table(name = TableConstant.PROMO_DRAFT_SCHEMAS)
public class PromoSchemaEntity extends UpdateEntity {

    @Id
    @PrimaryKey(generationType = GenerationType.SEQUENCE, generator = "promo_draft_schemas_id_seq")
    @Column(name = "id")
    private Long id;

    @Column(name = "draft_id")
    private Long draftId;

    @Column(name = "sort_order")
    private int sortOrder;

    @Column(name = "name")
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "scope")
    private String scope;

    @Column(name = "schema_type")
    private String schemaType;

    @Column(name = "cost_sources")
    private String costSources;

    @Column(name = "priority")
    private int priority;

    @Column(name = "status")
    private String status = "ENABLED";

    @Column(name = "condition_mode")
    private String conditionMode = "EXPLICIT";

    @Column(name = "schema_starts_at")
    private Date schemaStartsAt;

    @Column(name = "schema_ends_at")
    private Date schemaEndsAt;

    @Column(name = "unpublished_at")
    private Date unpublishedAt;

    @Column(name = "legacy_promotion_id")
    private Long legacyPromotionId;

}
