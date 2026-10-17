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


/** Footer của site (V26) - chỉ có tối đa 1 dòng. mạng xã hội và cột menu lưu JSON (TEXT). */
@Getter
@Setter
@NoArgsConstructor
@Table(name = TableConstant.SITE_FOOTER)
public class SiteFooterEntity extends BaseEntity {

    @Id
    @PrimaryKey(generationType = GenerationType.SEQUENCE, generator = "site_footer_id_seq")
    @Column(name = "id")
    private Long id;

    @Column(name = "copyright_text")
    private String copyrightText;

    @Column(name = "footer_logo_url")
    private String footerLogoUrl;

    @Column(name = "certification_image_url")
    private String certificationImageUrl;

    @Column(name = "certification_url")
    private String certificationUrl;

    @Column(name = "newsletter_heading")
    private String newsletterHeading;

    @Column(name = "newsletter_sub_heading")
    private String newsletterSubHeading;

    @Column(name = "social_links")
    private String socialLinks;

    @Column(name = "menu_columns")
    private String menuColumns;
}
