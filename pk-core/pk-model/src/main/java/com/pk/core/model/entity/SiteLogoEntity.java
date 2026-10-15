package com.pk.core.model.entity;

import com.pk.core.common.entity.BaseEntity;
import com.pk.core.model.constant.TableConstant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import vn.com.unit.miragesql.miragesql.annotation.Column;
import vn.com.unit.miragesql.miragesql.annotation.PrimaryKey;
import vn.com.unit.miragesql.miragesql.annotation.PrimaryKey.GenerationType;
import vn.com.unit.miragesql.miragesql.annotation.Table;


/** Logo của site (V26). Mỗi loại (type) chỉ có một logo ACTIVE; "archive" là xoá mềm. */
@Getter
@Setter
@NoArgsConstructor
@Table(name = TableConstant.SITE_LOGOS)
public class SiteLogoEntity extends BaseEntity {

    @Id
    @PrimaryKey(generationType = GenerationType.SEQUENCE, generator = "site_logos_id_seq")
    @Column(name = "id")
    private Long id;

    @Column(name = "name")
    private String name;

    @Column(name = "type")
    private String type = "HEADER";

    @Column(name = "status")
    private String status = "DRAFT";

    @Column(name = "logo_url")
    private String logoUrl;

    @Column(name = "logo_theme")
    private String logoTheme;

    @Column(name = "logo_target_url")
    private String logoTargetUrl;
}
