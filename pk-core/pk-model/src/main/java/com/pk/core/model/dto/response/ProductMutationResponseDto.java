package com.pk.core.model.dto.response;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Kết quả gửi duyệt / huỷ duyệt (FE: ProductMutationResponseDto). `uuid` = id dạng chuỗi (DB dùng id số). */
@Getter
@Setter
@NoArgsConstructor
public class ProductMutationResponseDto {

    private String id;
    private String uuid;
    private String status;
    private ProductApprovalSummaryDto approval;
    private boolean hasPendingApproval;
    private String approvalRequestId;
    private String message;
}
