package com.pk.core.model.entity;

import com.pk.core.common.entity.CreateEntity;
import com.pk.core.model.constant.TableConstant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import vn.com.unit.miragesql.miragesql.annotation.Column;
import vn.com.unit.miragesql.miragesql.annotation.PrimaryKey;
import vn.com.unit.miragesql.miragesql.annotation.PrimaryKey.GenerationType;
import vn.com.unit.miragesql.miragesql.annotation.Table;

import java.math.BigDecimal;
import java.util.Date;

/** Lịch sử trạng thái yêu cầu đổi/trả. */
@Getter
@Setter
@NoArgsConstructor
@Table(name = TableConstant.ORDER_RETURN_STATUS_HISTORY)
public class OrderReturnHistoryEntity extends CreateEntity {

    @Id
    @PrimaryKey(generationType = GenerationType.SEQUENCE, generator = "order_return_status_history_id_seq")
    @Column(name = "id")
    private Long id;

    @Column(name = "return_id")
    private Long returnId;

    @Column(name = "from_status")
    private String fromStatus;

    @Column(name = "to_status")
    private String toStatus;

    @Column(name = "changed_by_id")
    private Long changedById;

    @Column(name = "changed_by_type")
    private String changedByType;

    @Column(name = "note")
    private String note;
}
