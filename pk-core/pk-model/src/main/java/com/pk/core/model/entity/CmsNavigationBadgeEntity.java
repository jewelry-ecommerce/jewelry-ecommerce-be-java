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

/** Nhãn nhỏ gắn vào nút menu, có lịch hiển thị (V30). Cùng cơ chế 2 lớp DRAFT/PUBLISHED với nút menu. */
@Getter
@Setter
@NoArgsConstructor
@Table(name = TableConstant.CMS_NAVIGATION_BADGES)
public class CmsNavigationBadgeEntity extends UpdateEntity {

    @Id
    @PrimaryKey(generationType = GenerationType.SEQUENCE, generator = "cms_navigation_badges_id_seq")
    @Column(name = "id")
    private Long id;

    @Column(name = "layer")
    private String layer = CmsNavigationEntity.DRAFT;

    @Column(name = "source_id")
    private Long sourceId;

    @Column(name = "navigation_id")
    private Long navigationId;

    /** TEXT | IMAGE. */
    @Column(name = "design_type")
    private String designType;

    @Column(name = "display_text")
    private String displayText;

    @Column(name = "image_url")
    private String imageUrl;

    /** JSON object (TEXT). */
    @Column(name = "style_config")
    private String styleConfig;

    @Column(name = "start_time")
    private Date startTime;

    @Column(name = "end_time")
    private Date endTime;
}
