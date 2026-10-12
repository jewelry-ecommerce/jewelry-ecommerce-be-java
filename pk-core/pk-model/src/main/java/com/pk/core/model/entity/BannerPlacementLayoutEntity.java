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

/** Một bố cục (layout) của vị trí banner: tập banner + slot, có trạng thái và cửa sổ thời gian hiển thị. */
@Getter
@Setter
@NoArgsConstructor
@Table(name = TableConstant.BANNER_PLACEMENT_LAYOUTS)
public class BannerPlacementLayoutEntity extends UpdateEntity {

    public static final String DRAFT = "DRAFT";
    public static final String SCHEDULED = "SCHEDULED";
    public static final String ACTIVE = "ACTIVE";
    public static final String INACTIVE = "INACTIVE";
    public static final String ARCHIVED = "ARCHIVED";

    @Id
    @PrimaryKey(generationType = GenerationType.SEQUENCE, generator = "banner_placement_layouts_id_seq")
    @Column(name = "id")
    private Long id;

    @Column(name = "placement_id")
    private Long placementId;

    @Column(name = "name")
    private String name;

    @Column(name = "status")
    private String status = DRAFT;

    @Column(name = "active_from")
    private Date activeFrom;

    @Column(name = "active_to")
    private Date activeTo;

    @Column(name = "note")
    private String note;
}
