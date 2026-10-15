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

import java.util.Date;

/** Thanh thông báo đầu trang (V26). Chỉ một header được hiển thị tại một thời điểm; xoá là xoá mềm. */
@Getter
@Setter
@NoArgsConstructor
@Table(name = TableConstant.SITE_HEADERS)
public class SiteHeaderEntity extends BaseEntity {

    public static final String DRAFT = "DRAFT";
    public static final String ACTIVE = "ACTIVE";
    public static final String DEACTIVE = "DEACTIVE";
    public static final String SCHEDULED = "SCHEDULED";

    @Id
    @PrimaryKey(generationType = GenerationType.SEQUENCE, generator = "site_headers_id_seq")
    @Column(name = "id")
    private Long id;

    @Column(name = "banner_name")
    private String bannerName;

    @Column(name = "display_style")
    private String displayStyle = "TEXT";

    @Column(name = "content")
    private String content;

    @Column(name = "bg_color")
    private String bgColor;

    @Column(name = "text_color")
    private String textColor;

    @Column(name = "target_url")
    private String targetUrl;

    @Column(name = "animation_style")
    private String animationStyle;

    @Column(name = "loop_interval_sec")
    private Integer loopIntervalSec;

    @Column(name = "start_time")
    private Date startTime;

    @Column(name = "end_time")
    private Date endTime;

    @Column(name = "status")
    private String status = DRAFT;
}
