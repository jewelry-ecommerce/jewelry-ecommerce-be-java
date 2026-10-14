package com.pk.core.model.dto.response;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Một yêu cầu duyệt trong danh sách duyệt (FE: ProductApprovalRequestDto + thông tin sản phẩm). */
@Getter
@Setter
@NoArgsConstructor
public class ProductApprovalRequestResponseDto {

    private String id;
    private String productId;
    private String productCode;
    private String productName;
    private String productStatus;
    private String status;
    private String actionType;
    private String submittedAt;
    private String submittedBy;
    private String reviewedAt;
    private String reviewedBy;
    private String rejectedReason;
    /** = submittedAt (FE đọc createdAt). */
    private String createdAt;
}
