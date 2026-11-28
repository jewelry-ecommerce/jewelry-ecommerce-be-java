package com.pk.core.business.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pk.core.business.repository.ProductReviewRepo;
import com.pk.core.business.service.ProductReviewService;
import com.pk.core.common.exception.BusinessException;
import com.pk.core.common.exception.ErrorCode;
import com.pk.core.common.exception.ResourceNotFoundException;
import com.pk.core.model.entity.ProductReviewEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductReviewServiceImpl implements ProductReviewService {

    private final ProductReviewRepo reviews;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    @Override
    public List<Map<String, Object>> adminList(String status) {
        String filter = status == null || status.isBlank() || "ALL".equalsIgnoreCase(status) ? null : status.trim().toUpperCase();
        List<ProductReviewEntity> rows = filter == null
                ? new ArrayList<>(reviews.findAll(Sort.unsorted()))
                : reviews.findByStatus(filter);
        if (filter == null) {
            rows.sort((a, b) -> Long.compare(b.getId() == null ? 0L : b.getId(), a.getId() == null ? 0L : a.getId()));
        }
        return rows.stream().map(this::toAdminMap).collect(Collectors.toList());
    }

    @Transactional
    @Override
    public Map<String, Object> adminApprove(Long id, String reply) {
        ProductReviewEntity r = require(id);
        r.setStatus(ProductReviewEntity.APPROVED);
        if (reply != null && !reply.isBlank()) {
            r.setAdminReply(reply.trim());
        }
        r.setRejectedReason(null);
        r.touch();
        reviews.update(r);
        return toAdminMap(r);
    }

    @Transactional
    @Override
    public Map<String, Object> adminReject(Long id, String reason) {
        ProductReviewEntity r = require(id);
        r.setStatus(ProductReviewEntity.REJECTED);
        r.setRejectedReason(reason == null || reason.isBlank() ? "Không đạt yêu cầu hiển thị" : reason.trim());
        r.touch();
        reviews.update(r);
        return toAdminMap(r);
    }

    @Transactional
    @Override
    public Map<String, Object> adminReply(Long id, String reply) {
        ProductReviewEntity r = require(id);
        if (reply == null || reply.isBlank()) {
            throw BusinessException.badRequest(ErrorCode.VALIDATION_FAILED, "Nội dung phản hồi không được trống");
        }
        r.setAdminReply(reply.trim());
        r.touch();
        reviews.update(r);
        return toAdminMap(r);
    }

    @Transactional(readOnly = true)
    @Override
    public List<Map<String, Object>> publicByProduct(Long productId) {
        return reviews.findByProductId(productId).stream()
                .filter(r -> ProductReviewEntity.APPROVED.equals(r.getStatus()))
                .map(this::toPublicMap)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    @Override
    public Map<String, Object> publicSummary(Long productId) {
        List<ProductReviewEntity> approved = reviews.findByProductId(productId).stream()
                .filter(r -> ProductReviewEntity.APPROVED.equals(r.getStatus()))
                .toList();
        double avg = approved.isEmpty() ? 0d : approved.stream().mapToInt(ProductReviewEntity::getRating).average().orElse(0d);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("productId", String.valueOf(productId));
        out.put("averageRating", Math.round(avg * 10d) / 10d);
        out.put("totalReviews", approved.size());
        return out;
    }

    @Transactional
    @Override
    public Map<String, Object> create(Long productId, Long orderId, Long userId, String authorName,
                                      int rating, String title, String content, List<String> images) {
        if (productId == null || productId < 1) {
            throw BusinessException.badRequest(ErrorCode.VALIDATION_FAILED, "productId không hợp lệ");
        }
        if (rating < 1 || rating > 5) {
            throw BusinessException.badRequest(ErrorCode.VALIDATION_FAILED, "rating phải từ 1 đến 5");
        }
        ProductReviewEntity r = new ProductReviewEntity();
        r.setProductId(productId);
        r.setOrderId(orderId);
        r.setUserId(userId);
        r.setAuthorName(authorName == null || authorName.isBlank() ? "Khách" : authorName.trim());
        r.setRating(rating);
        r.setTitle(title == null ? "" : title.trim());
        r.setContent(content == null ? "" : content.trim());
        r.setImagesJson(writeImages(images));
        r.setStatus(ProductReviewEntity.PENDING);
        reviews.create(r);
        return toAdminMap(r);
    }

    private ProductReviewEntity require(Long id) {
        ProductReviewEntity r = reviews.findOne(id);
        if (r == null) {
            throw new ResourceNotFoundException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy đánh giá " + id);
        }
        return r;
    }

    private Map<String, Object> toAdminMap(ProductReviewEntity r) {
        Map<String, Object> m = toPublicMap(r);
        m.put("status", r.getStatus());
        m.put("rejectedReason", r.getRejectedReason());
        m.put("orderId", r.getOrderId() == null ? null : String.valueOf(r.getOrderId()));
        m.put("userId", r.getUserId() == null ? null : String.valueOf(r.getUserId()));
        return m;
    }

    private Map<String, Object> toPublicMap(ProductReviewEntity r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", String.valueOf(r.getId()));
        m.put("productId", String.valueOf(r.getProductId()));
        m.put("authorName", r.getAuthorName());
        m.put("rating", r.getRating());
        m.put("title", r.getTitle());
        m.put("content", r.getContent());
        m.put("images", readImages(r.getImagesJson()));
        m.put("adminReply", r.getAdminReply());
        m.put("createdAt", r.getCreatedDate());
        return m;
    }

    private String writeImages(List<String> images) {
        try {
            return objectMapper.writeValueAsString(images == null ? List.of() : images);
        } catch (Exception ex) {
            return "[]";
        }
    }

    private List<String> readImages(String json) {
        try {
            if (json == null || json.isBlank()) {
                return new ArrayList<>();
            }
            return objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (Exception ex) {
            return new ArrayList<>();
        }
    }
}
