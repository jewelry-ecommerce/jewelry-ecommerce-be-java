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

/** Mùa (V27): khoảng tháng trong năm (có thể vắt qua năm, vd 11 -> 2). Xoá mềm; code duy nhất trong số bản ghi chưa xoá. */
@Getter
@Setter
@NoArgsConstructor
@Table(name = TableConstant.SEASONS)
public class SeasonEntity extends BaseEntity {

    @Id
    @PrimaryKey(generationType = GenerationType.SEQUENCE, generator = "seasons_id_seq")
    @Column(name = "id")
    private Long id;

    @Column(name = "code")
    private String code;

    @Column(name = "name")
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "start_month")
    private int startMonth;

    @Column(name = "end_month")
    private int endMonth;

    @Column(name = "sort_order")
    private int sortOrder;

    @Column(name = "is_active")
    private boolean active = true;
}
