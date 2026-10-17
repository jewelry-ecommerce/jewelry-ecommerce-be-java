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

/** Nhật ký thao tác trên đơn hàng (OMS audit). */
@Getter
@Setter
@NoArgsConstructor
@Table(name = TableConstant.ORDER_AUDIT_LOGS)
public class OrderAuditLogEntity extends CreateEntity {

    @Id
    @PrimaryKey(generationType = GenerationType.SEQUENCE, generator = "order_audit_logs_id_seq")
    @Column(name = "id")
    private Long id;

    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "entity_name")
    private String entityName = "oms";

    @Column(name = "action")
    private String action;

    @Column(name = "action_type")
    private String actionType;

    @Column(name = "source")
    private String source;

    @Column(name = "message")
    private String message;

    @Column(name = "note")
    private String note;

    @Column(name = "actor_id")
    private Long actorId;

    @Column(name = "actor_name")
    private String actorName;

    @Column(name = "actor_role")
    private String actorRole;

    @Column(name = "changes")
    private String changes;
}
