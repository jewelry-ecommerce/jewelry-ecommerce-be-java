package com.pk.core.model.dto.response;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Số sản phẩm theo vòng đời (cùng bộ lọc với danh sách, trừ status) - GET /admin/catalog/products/counts.
 * `hasPendingApproval`/`approval*` đếm theo yêu cầu duyệt MỚI NHẤT của từng sản phẩm. */
@Getter
@Setter
@NoArgsConstructor
public class ProductCountsDto {

    private long all;
    private long draft;
    private long unpublished;
    private long published;
    private long discontinued;
    private long archived;
    private long hasPendingApproval;
    private long approvalApproved;
    private long approvalRejected;
    private long approvalCancelled;

    public ProductCountsDto(long all, long draft, long unpublished, long published, long discontinued, long archived) {
        this.all = all;
        this.draft = draft;
        this.unpublished = unpublished;
        this.published = published;
        this.discontinued = discontinued;
        this.archived = archived;
    }
}
