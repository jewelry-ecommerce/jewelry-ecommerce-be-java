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

/** Nút menu điều hướng (V30). Mỗi nút tồn tại ở lớp DRAFT (admin chỉnh) và, sau khi xuất bản, ở lớp PUBLISHED (bản sao, sourceId = id nút nháp). */
@Getter
@Setter
@NoArgsConstructor
@Table(name = TableConstant.CMS_NAVIGATIONS)
public class CmsNavigationEntity extends UpdateEntity {

    public static final String DRAFT = "DRAFT";
    public static final String PUBLISHED = "PUBLISHED";

    @Id
    @PrimaryKey(generationType = GenerationType.SEQUENCE, generator = "cms_navigations_id_seq")
    @Column(name = "id")
    private Long id;

    @Column(name = "layer")
    private String layer = DRAFT;

    @Column(name = "source_id")
    private Long sourceId;

    @Column(name = "parent_id")
    private Long parentId;

    @Column(name = "label")
    private String label;

    /** NONE | CATEGORY | PAGE | LINK. */
    @Column(name = "type")
    private String type = "NONE";

    @Column(name = "target_id")
    private String targetId;

    @Column(name = "url")
    private String url;

    /** JSON object (TEXT). */
    @Column(name = "query_params")
    private String queryParams;

    @Column(name = "icon")
    private String icon;

    @Column(name = "image")
    private String image;

    @Column(name = "open_in_new_tab")
    private boolean openInNewTab;

    @Column(name = "sort_order")
    private int sortOrder;

    @Column(name = "show_on_menu_popup")
    private boolean showOnMenuPopup = true;

    @Column(name = "show_on_plp")
    private boolean showOnPlp = true;

    @Column(name = "is_archived")
    private boolean archived;
}
