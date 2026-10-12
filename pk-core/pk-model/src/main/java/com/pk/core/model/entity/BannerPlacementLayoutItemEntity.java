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

/** Banner nằm ở slot nào của layout. {@code displayMetadata} là JSON {x,y,width,height,order,slotType,childBannerIds}. */
@Getter
@Setter
@NoArgsConstructor
@Table(name = TableConstant.BANNER_PLACEMENT_LAYOUT_ITEMS)
public class BannerPlacementLayoutItemEntity extends UpdateEntity {

    @Id
    @PrimaryKey(generationType = GenerationType.SEQUENCE, generator = "banner_placement_layout_items_id_seq")
    @Column(name = "id")
    private Long id;

    @Column(name = "layout_id")
    private Long layoutId;

    @Column(name = "slot_key")
    private String slotKey;

    @Column(name = "banner_id")
    private Long bannerId;

    @Column(name = "is_active")
    private boolean active = true;

    @Column(name = "priority")
    private int priority;

    @Column(name = "display_metadata")
    private String displayMetadata;
}
