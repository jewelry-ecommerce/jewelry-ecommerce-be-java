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

/** Cấu hình banner chèn vào danh sách sản phẩm (V32). banners / categoryPathSlugs lưu JSON (TEXT). */
@Getter
@Setter
@NoArgsConstructor
@Table(name = TableConstant.PRODUCT_LIST_BANNER_CONFIGS)
public class ProductListBannerConfigEntity extends UpdateEntity {

    @Id
    @PrimaryKey(generationType = GenerationType.SEQUENCE, generator = "product_list_banner_configs_id_seq")
    @Column(name = "id")
    private Long id;

    /** CATEGORY | CMS_STOREFRONT. */
    @Column(name = "condition_type")
    private String conditionType;

    @Column(name = "target_id")
    private String targetId;

    @Column(name = "target_slug")
    private String targetSlug;

    @Column(name = "target_name")
    private String targetName;

    @Column(name = "category_path_slugs")
    private String categoryPathSlugs;

    @Column(name = "is_active")
    private boolean active = true;

    @Column(name = "banners")
    private String banners;
}
