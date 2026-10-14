package com.pk.core.model.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/** POST /admin/catalog/product-approvals/review: duyệt hoặc từ chối nhiều yêu cầu cùng lúc (atomic - 1 yêu cầu lỗi thì không ghi gì).
 * `decision` APPROVE | REJECT; REJECT bắt buộc `reason`. */
@Getter
@Setter
@NoArgsConstructor
public class AdminProductApprovalReviewRequestDto {

    @Valid
    @NotEmpty(message = "Danh sách yêu cầu duyệt không được để trống")
    private List<Item> items;

    private String decision;
    private String reason;

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Item {
        @com.fasterxml.jackson.annotation.JsonAlias("productUuid")
        private Long productId;
        @com.fasterxml.jackson.annotation.JsonAlias("approvalRequestUuid")
        private Long approvalRequestId;
    }
}
