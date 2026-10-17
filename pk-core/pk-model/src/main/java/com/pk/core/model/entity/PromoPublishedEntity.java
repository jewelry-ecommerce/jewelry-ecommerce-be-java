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

/** Bản chiến dịch đã phát hành và vòng đời ACTIVE / PAUSED / ENDED / ARCHIVED. */
@Getter
@Setter
@NoArgsConstructor
@Table(name = TableConstant.PROMO_PUBLISHED)
public class PromoPublishedEntity extends UpdateEntity {

    @Id
    @PrimaryKey(generationType = GenerationType.SEQUENCE, generator = "promo_published_id_seq")
    @Column(name = "id")
    private Long id;

    @Column(name = "draft_id")
    private Long draftId;

    @Column(name = "name")
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "status")
    private String status = "ACTIVE";

    @Column(name = "starts_at")
    private Date startsAt;

    @Column(name = "ends_at")
    private Date endsAt;

    @Column(name = "published_at")
    private Date publishedAt;

    @Column(name = "ended_at")
    private Date endedAt;

}
