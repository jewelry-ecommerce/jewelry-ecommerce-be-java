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

/** Vị trí hiển thị banner (VD HOME_HERO). Seed sẵn ở V6; quản lý qua /admin/banner/placements (V14/V17 thêm platform, status, settings). */
@Getter
@Setter
@NoArgsConstructor
@Table(name = TableConstant.BANNER_PLACEMENTS)
public class BannerPlacementEntity extends CreateEntity {

    @Id
    @PrimaryKey(generationType = GenerationType.SEQUENCE, generator = "banner_placements_id_seq")
    @Column(name = "id")
    private Long id;

    @Column(name = "code")
    private String code;

    @Column(name = "name")
    private String name;

    @Column(name = "display_type")
    private String displayType;

    /** WEB | APP | ALL (V17). */
    @Column(name = "platform")
    private String platform = "ALL";

    /** ACTIVE | INACTIVE (V17); INACTIVE thì storefront không render banner. */
    @Column(name = "status")
    private String status = "ACTIVE";

    /** JSON {columns, rows, gap, mobileOddFullWidthItem} (V17), null = không cấu hình. */
    @Column(name = "settings")
    private String settings;
}
