package com.pk.core.business.service;

import com.pk.core.common.web.PageResponse;
import com.pk.core.model.dto.request.AdminProductApprovalReviewRequestDto;
import com.pk.core.model.dto.response.ProductApprovalRequestResponseDto;
import com.pk.core.model.dto.response.ProductMutationResponseDto;

import java.util.Map;

/** Quy trình duyệt sản phẩm: gửi duyệt -> duyệt (publish) | từ chối | huỷ. Xem V23__product_approval.sql. */
public interface ProductApprovalService {

    /** DRAFT/UNPUBLISHED -> yêu cầu PENDING (kiểm tra đủ điều kiện publish ngay lúc gửi). Sản phẩm bị khoá sửa trong lúc chờ. */
    ProductMutationResponseDto submit(Long productId, Long actorId);

    /** Duyệt (publish sản phẩm) hoặc từ chối nhiều yêu cầu; atomic. Trả {decision, processed, items[]}. */
    Map<String, Object> review(AdminProductApprovalReviewRequestDto req, Long actorId);

    /** Người gửi huỷ yêu cầu còn PENDING. Trả {success, productStatus, approval, hasPendingApproval}. */
    Map<String, Object> cancel(Long requestId);

    /** Danh sách yêu cầu (mới nhất trước), lọc status (PENDING | APPROVED | REJECTED | CANCELLED) và search theo tên/mã sản phẩm. */
    PageResponse<ProductApprovalRequestResponseDto> list(String status, String search, int page, int take);
}
