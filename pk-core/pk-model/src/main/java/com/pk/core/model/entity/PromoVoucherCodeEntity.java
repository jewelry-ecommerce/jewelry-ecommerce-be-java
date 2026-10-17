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

/** Mã voucher của schema VOUCHER (tối đa 1 mã/schema). */
@Getter
@Setter
@NoArgsConstructor
@Table(name = TableConstant.PROMO_VOUCHER_CODES)
public class PromoVoucherCodeEntity extends UpdateEntity {

    @Id
    @PrimaryKey(generationType = GenerationType.SEQUENCE, generator = "promo_voucher_codes_id_seq")
    @Column(name = "id")
    private Long id;

    @Column(name = "schema_id")
    private Long schemaId;

    @Column(name = "code")
    private String code;

    @Column(name = "normalized_code")
    private String normalizedCode;

    @Column(name = "status")
    private String status = "ACTIVE";

    @Column(name = "image_url")
    private String imageUrl;

    @Column(name = "issuance_starts_at")
    private Date issuanceStartsAt;

    @Column(name = "issuance_ends_at")
    private Date issuanceEndsAt;

    @Column(name = "usage_starts_at")
    private Date usageStartsAt;

    @Column(name = "usage_ends_at")
    private Date usageEndsAt;

    @Column(name = "policy")
    private String policy;

    @Column(name = "legacy_voucher_id")
    private Long legacyVoucherId;

}
