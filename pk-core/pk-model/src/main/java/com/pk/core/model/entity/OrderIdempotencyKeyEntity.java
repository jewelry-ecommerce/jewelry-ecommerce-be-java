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

/** Khoá chống đặt đơn trùng (V25): (owner, idemKey) duy nhất, gắn với đơn đã tạo và hash nội dung request. */
@Getter
@Setter
@NoArgsConstructor
@Table(name = TableConstant.ORDER_IDEMPOTENCY_KEYS)
public class OrderIdempotencyKeyEntity extends CreateEntity {

    @Id
    @PrimaryKey(generationType = GenerationType.SEQUENCE, generator = "order_idempotency_keys_id_seq")
    @Column(name = "id")
    private Long id;

    @Column(name = "owner")
    private String owner;

    @Column(name = "idem_key")
    private String idemKey;

    @Column(name = "request_hash")
    private String requestHash;

    @Column(name = "order_code")
    private String orderCode;
}
