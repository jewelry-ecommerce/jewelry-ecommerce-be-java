package com.pk.core.model.entity;

import com.pk.core.model.constant.TableConstant;
import com.pk.core.common.entity.BaseEntity;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import vn.com.unit.miragesql.miragesql.annotation.Column;
import vn.com.unit.miragesql.miragesql.annotation.PrimaryKey;
import vn.com.unit.miragesql.miragesql.annotation.PrimaryKey.GenerationType;
import vn.com.unit.miragesql.miragesql.annotation.Table;

/** Danh mục sản phẩm (có thể lồng cấp qua parentId). Xem document/09-tong-hop-api-fe.md. */
@Getter
@Setter
@NoArgsConstructor
@Table(name = TableConstant.CATEGORIES)
public class CategoryEntity extends BaseEntity {

    @Id
    @PrimaryKey(generationType = GenerationType.SEQUENCE, generator = "categories_id_seq")
    @Column(name = "id")
    private Long id;

    @Column(name = "parent_id")
    private Long parentId;

    @Column(name = "name")
    private String name;

    @Column(name = "slug")
    private String slug;

    /** Mã riêng (khác slug); backfill = slug ở V20. */
    @Column(name = "code")
    private String code;

    @Column(name = "description")
    private String description;

    @Column(name = "image_url")
    private String imageUrl;

    @Column(name = "sort_order")
    private int sortOrder;

    @Column(name = "active")
    private boolean active = true;

    public CategoryEntity(String name, String slug, String description) {
        this.name = name;
        this.slug = slug;
        this.code = slug;
        this.description = description;
    }
}
