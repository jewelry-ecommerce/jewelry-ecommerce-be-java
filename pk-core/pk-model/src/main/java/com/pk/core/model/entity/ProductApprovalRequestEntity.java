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

/** Yêu cầu duyệt sản phẩm (gửi duyệt -> PENDING; duyệt -> APPROVED và sản phẩm được publish; từ chối -> REJECTED;
 * người gửi huỷ -> CANCELLED). Snapshot là JSON text của các trường chính tại thời điểm gửi. */
@Getter
@Setter
@NoArgsConstructor
@Table(name = TableConstant.PRODUCT_APPROVAL_REQUESTS)
public class ProductApprovalRequestEntity extends UpdateEntity {

    public static final String PENDING = "PENDING";
    public static final String APPROVED = "APPROVED";
    public static final String REJECTED = "REJECTED";
    public static final String CANCELLED = "CANCELLED";
    public static final String CREATE_PRODUCT = "CREATE_PRODUCT";
    public static final String UPDATE_PRODUCT = "UPDATE_PRODUCT";

    @Id
    @PrimaryKey(generationType = GenerationType.SEQUENCE, generator = "product_approval_requests_id_seq")
    @Column(name = "id")
    private Long id;

    @Column(name = "product_id")
    private Long productId;

    @Column(name = "action_type")
    private String actionType;

    @Column(name = "status")
    private String status = PENDING;

    @Column(name = "submitted_by")
    private Long submittedBy;

    @Column(name = "submitted_at")
    private Date submittedAt;

    @Column(name = "reviewed_by")
    private Long reviewedBy;

    @Column(name = "reviewed_at")
    private Date reviewedAt;

    @Column(name = "rejected_reason")
    private String rejectedReason;

    @Column(name = "before_snapshot")
    private String beforeSnapshot;

    @Column(name = "after_snapshot")
    private String afterSnapshot;

    public boolean isPending() {
        return PENDING.equals(status);
    }
}
