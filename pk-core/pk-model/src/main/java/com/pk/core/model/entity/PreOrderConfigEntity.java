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

/** Một lần cấu hình bật/tắt đặt trước (pre-order). Chỉ thêm, không sửa/xoá: dòng mới nhất là cấu hình đang hiệu lực. */
@Getter
@Setter
@NoArgsConstructor
@Table(name = TableConstant.PRE_ORDER_CONFIGS)
public class PreOrderConfigEntity extends CreateEntity {

    @Id
    @PrimaryKey(generationType = GenerationType.SEQUENCE, generator = "pre_order_configs_id_seq")
    @Column(name = "id")
    private Long id;

    @Column(name = "enabled")
    private boolean enabled;

    @Column(name = "message")
    private String message;

    /** ALL (mặc định, hành vi cũ) | SKU | PRODUCT | CATEGORY | COLLECTION (V19). */
    @Column(name = "scope_type")
    private String scopeType = "ALL";

    /** Id trong phạm vi, ngăn cách bởi dấu phẩy; null khi scopeType = ALL (V19). */
    @Column(name = "scope_ids")
    private String scopeIds;

    public PreOrderConfigEntity(boolean enabled, String message, Long createdBy) {
        this.enabled = enabled;
        this.message = message;
        setCreatedId(createdBy);
    }
}
