package com.pk.core.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Tóm tắt yêu cầu duyệt mới nhất của sản phẩm (FE: ProductApprovalSummary). */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ProductApprovalSummaryDto {

    private boolean hasPending;
    private String requestId;
    private String action;
    private String status;
    private String submittedAt;
    private String submittedBy;
    private String rejectedReason;
}
