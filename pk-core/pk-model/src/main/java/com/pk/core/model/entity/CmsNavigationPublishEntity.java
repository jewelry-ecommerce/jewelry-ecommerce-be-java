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

/** Dòng duy nhất lưu dấu vân tay bản nháp ở lần xuất bản gần nhất (V30). */
@Getter
@Setter
@NoArgsConstructor
@Table(name = TableConstant.CMS_NAVIGATION_PUBLISH)
public class CmsNavigationPublishEntity extends UpdateEntity {

    @Id
    @PrimaryKey(generationType = GenerationType.SEQUENCE, generator = "cms_navigation_publish_id_seq")
    @Column(name = "id")
    private Long id;

    @Column(name = "fingerprint")
    private String fingerprint;

    @Column(name = "published_date")
    private Date publishedDate;
}
