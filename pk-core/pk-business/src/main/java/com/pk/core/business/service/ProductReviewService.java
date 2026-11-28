package com.pk.core.business.service;

import java.util.List;
import java.util.Map;

/** Đánh giá sản phẩm: khách tạo, storefront đọc APPROVED, admin duyệt/từ chối/reply. */
public interface ProductReviewService {

    List<Map<String, Object>> adminList(String status);

    Map<String, Object> adminApprove(Long id, String reply);

    Map<String, Object> adminReject(Long id, String reason);

    Map<String, Object> adminReply(Long id, String reply);

    List<Map<String, Object>> publicByProduct(Long productId);

    Map<String, Object> publicSummary(Long productId);

    Map<String, Object> create(Long productId, Long orderId, Long userId, String authorName,
                              int rating, String title, String content, List<String> images);
}
