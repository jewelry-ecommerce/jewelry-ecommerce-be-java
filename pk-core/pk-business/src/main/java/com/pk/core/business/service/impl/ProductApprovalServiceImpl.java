package com.pk.core.business.service.impl;

import com.pk.core.business.repository.ProductApprovalRequestRepo;
import com.pk.core.business.repository.ProductRepo;
import com.pk.core.business.repository.ProductSkuRepo;
import com.pk.core.business.service.ProductApprovalService;
import com.pk.core.business.service.ProductService;
import com.pk.core.common.exception.BusinessException;
import com.pk.core.common.exception.ErrorCode;
import com.pk.core.common.exception.ResourceNotFoundException;
import com.pk.core.common.web.PageResponse;
import com.pk.core.model.dto.request.AdminProductApprovalReviewRequestDto;
import com.pk.core.model.dto.response.ProductApprovalRequestResponseDto;
import com.pk.core.model.dto.response.ProductApprovalSummaryDto;
import com.pk.core.model.dto.response.ProductMutationResponseDto;
import com.pk.core.model.entity.ProductApprovalRequestEntity;
import com.pk.core.model.entity.ProductEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ProductApprovalServiceImpl implements ProductApprovalService {

    private static final Set<String> STATUSES = Set.of(ProductApprovalRequestEntity.PENDING,
            ProductApprovalRequestEntity.APPROVED, ProductApprovalRequestEntity.REJECTED,
            ProductApprovalRequestEntity.CANCELLED);

    private final ProductApprovalRequestRepo requests;
    private final ProductRepo products;
    private final ProductSkuRepo productSkus;
    private final ProductService productService;
    private final ProductApprovalSupport support;

    @Transactional
    @Override
    public ProductMutationResponseDto submit(Long productId, Long actorId) {
        ProductEntity p = requireProduct(productId);
        if (!ProductEntity.DRAFT.equals(p.getStatus()) && !ProductEntity.UNPUBLISHED.equals(p.getStatus())) {
            throw BusinessException.conflict(ErrorCode.INVALID_STATUS_TRANSITION,
                    "Chỉ gửi duyệt sản phẩm DRAFT hoặc UNPUBLISHED (hiện là " + p.getStatus() + ")", p.getStatus());
        }
        List<ProductApprovalRequestEntity> history = requests.findByProductId(productId);
        if (history.stream().anyMatch(ProductApprovalRequestEntity::isPending)) {
            throw BusinessException.conflict(ErrorCode.PRODUCT_PENDING_APPROVAL,
                    "Sản phẩm đã có yêu cầu đang chờ duyệt", productId);
        }
        productService.assertPublishable(productId); // thiếu SKU/giá thì báo ngay lúc gửi, không đợi người duyệt

        ProductApprovalRequestEntity req = new ProductApprovalRequestEntity();
        req.setProductId(productId);
        req.setActionType(history.stream().anyMatch(r -> ProductApprovalRequestEntity.APPROVED.equals(r.getStatus()))
                || ProductEntity.UNPUBLISHED.equals(p.getStatus())
                ? ProductApprovalRequestEntity.UPDATE_PRODUCT : ProductApprovalRequestEntity.CREATE_PRODUCT);
        req.setStatus(ProductApprovalRequestEntity.PENDING);
        req.setSubmittedBy(actorId);
        req.setSubmittedAt(new Date());
        req.setAfterSnapshot(support.snapshotOf(p, productSkus.findByProductId(productId)));
        history.stream().filter(r -> ProductApprovalRequestEntity.APPROVED.equals(r.getStatus())).findFirst()
                .ifPresent(prev -> req.setBeforeSnapshot(prev.getAfterSnapshot()));
        requests.create(req);
        return mutation(p, req, "Đã gửi duyệt");
    }

    @Transactional
    @Override
    public Map<String, Object> review(AdminProductApprovalReviewRequestDto dto, Long actorId) {
        String decision = dto.getDecision() == null ? "" : dto.getDecision().trim().toUpperCase(Locale.ROOT);
        if (!"APPROVE".equals(decision) && !"REJECT".equals(decision)) {
            throw BusinessException.badRequest(ErrorCode.APPROVAL_INVALID, "decision phải là APPROVE hoặc REJECT",
                    dto.getDecision());
        }
        boolean approve = "APPROVE".equals(decision);
        if (!approve && (dto.getReason() == null || dto.getReason().isBlank())) {
            throw BusinessException.badRequest(ErrorCode.APPROVAL_INVALID, "Từ chối cần nhập lý do (reason)");
        }
        // Kiểm tra toàn bộ trước, rồi mới ghi: 1 yêu cầu sai thì không yêu cầu nào bị đổi.
        List<ProductApprovalRequestEntity> loaded = new ArrayList<>();
        for (AdminProductApprovalReviewRequestDto.Item item : dto.getItems()) {
            if (item.getApprovalRequestId() == null) {
                throw BusinessException.badRequest(ErrorCode.APPROVAL_INVALID, "Mỗi mục cần approvalRequestId");
            }
            ProductApprovalRequestEntity req = requests.findOne(item.getApprovalRequestId());
            if (req == null) {
                throw new ResourceNotFoundException("Yêu cầu duyệt", "ProductApprovalRequest", item.getApprovalRequestId());
            }
            if (item.getProductId() != null && !item.getProductId().equals(req.getProductId())) {
                throw BusinessException.badRequest(ErrorCode.APPROVAL_INVALID,
                        "Yêu cầu " + req.getId() + " không thuộc sản phẩm " + item.getProductId(), item.getProductId());
            }
            if (!req.isPending()) {
                throw BusinessException.conflict(ErrorCode.INVALID_STATUS_TRANSITION,
                        "Yêu cầu " + req.getId() + " không còn chờ duyệt (" + req.getStatus() + ")", req.getId());
            }
            if (loaded.stream().anyMatch(r -> r.getId().equals(req.getId()))) {
                throw BusinessException.badRequest(ErrorCode.APPROVAL_INVALID, "Yêu cầu " + req.getId() + " bị lặp");
            }
            requireProduct(req.getProductId());
            if (approve) {
                productService.assertPublishable(req.getProductId());
            }
            loaded.add(req);
        }
        Date now = new Date();
        List<Map<String, Object>> results = new ArrayList<>();
        for (ProductApprovalRequestEntity req : loaded) {
            req.setStatus(approve ? ProductApprovalRequestEntity.APPROVED : ProductApprovalRequestEntity.REJECTED);
            req.setReviewedBy(actorId);
            req.setReviewedAt(now);
            if (!approve) {
                req.setRejectedReason(dto.getReason().trim());
            }
            req.touch();
            requests.update(req);
            if (approve) {
                productService.publishApproved(req.getProductId());
            }
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("productId", String.valueOf(req.getProductId()));
            r.put("approvalRequestId", String.valueOf(req.getId()));
            r.put("status", req.getStatus());
            results.add(r);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("decision", decision);
        out.put("processed", results.size());
        out.put("items", results);
        return out;
    }

    @Transactional
    @Override
    public Map<String, Object> cancel(Long requestId) {
        ProductApprovalRequestEntity req = requestId == null ? null : requests.findOne(requestId);
        if (req == null) {
            throw new ResourceNotFoundException("Yêu cầu duyệt", "ProductApprovalRequest", requestId);
        }
        if (!req.isPending()) {
            throw BusinessException.conflict(ErrorCode.INVALID_STATUS_TRANSITION,
                    "Chỉ huỷ được yêu cầu đang chờ duyệt (hiện là " + req.getStatus() + ")", req.getStatus());
        }
        req.setStatus(ProductApprovalRequestEntity.CANCELLED);
        req.setReviewedAt(new Date());
        req.touch();
        requests.update(req);
        ProductEntity p = products.findOne(req.getProductId());
        ProductApprovalSummaryDto summary = support.summary(requests.findByProductId(req.getProductId()));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("success", true);
        out.put("productStatus", p == null ? null : p.getStatus());
        out.put("approval", summary);
        out.put("hasPendingApproval", summary.isHasPending());
        return out;
    }

    @Transactional(readOnly = true)
    @Override
    public PageResponse<ProductApprovalRequestResponseDto> list(String status, String search, int page, int take) {
        String wanted = status == null || status.isBlank() ? null : status.trim().toUpperCase(Locale.ROOT);
        if (wanted != null && !STATUSES.contains(wanted)) {
            throw BusinessException.badRequest(ErrorCode.VALIDATION_FAILED,
                    "status không hợp lệ: " + status + " (PENDING | APPROVED | REJECTED | CANCELLED)", status);
        }
        String needle = search == null || search.isBlank() ? null : search.trim().toLowerCase(Locale.ROOT);
        List<ProductApprovalRequestResponseDto> out = new ArrayList<>();
        List<ProductApprovalRequestEntity> all = new ArrayList<>(requests.findAll(Sort.unsorted()));
        all.sort(Comparator.comparing(ProductApprovalRequestEntity::getId).reversed());
        for (ProductApprovalRequestEntity r : all) {
            if (wanted != null && !wanted.equals(r.getStatus())) {
                continue;
            }
            ProductEntity p = products.findOne(r.getProductId());
            if (p == null || p.getDeletedDate() != null) {
                continue;
            }
            if (needle != null && !(p.getName() != null && p.getName().toLowerCase(Locale.ROOT).contains(needle))
                    && !(p.getCode() != null && p.getCode().toLowerCase(Locale.ROOT).contains(needle))) {
                continue;
            }
            out.add(toDto(r, p));
        }
        return PageResponse.paginate(out, page, take);
    }

    // ------------------------------------------------------------------ helpers

    private ProductEntity requireProduct(Long id) {
        ProductEntity p = id == null ? null : products.findOne(id);
        if (p == null || p.getDeletedDate() != null) {
            throw new ResourceNotFoundException("Sản phẩm", "Product", id);
        }
        return p;
    }

    private ProductMutationResponseDto mutation(ProductEntity p, ProductApprovalRequestEntity req, String message) {
        ProductMutationResponseDto dto = new ProductMutationResponseDto();
        dto.setId(String.valueOf(p.getId()));
        dto.setUuid(String.valueOf(p.getId()));
        dto.setStatus(p.getStatus());
        ProductApprovalSummaryDto summary = support.summary(requests.findByProductId(p.getId()));
        dto.setApproval(summary);
        dto.setHasPendingApproval(summary.isHasPending());
        dto.setApprovalRequestId(req == null ? null : String.valueOf(req.getId()));
        dto.setMessage(message);
        return dto;
    }

    private static ProductApprovalRequestResponseDto toDto(ProductApprovalRequestEntity r, ProductEntity p) {
        ProductApprovalRequestResponseDto d = new ProductApprovalRequestResponseDto();
        d.setId(String.valueOf(r.getId()));
        d.setProductId(String.valueOf(r.getProductId()));
        d.setProductCode(p.getCode());
        d.setProductName(p.getName());
        d.setProductStatus(p.getStatus());
        d.setStatus(r.getStatus());
        d.setActionType(r.getActionType());
        d.setSubmittedAt(ProductApprovalSupport.iso(r.getSubmittedAt()));
        d.setCreatedAt(d.getSubmittedAt());
        d.setSubmittedBy(r.getSubmittedBy() == null ? null : String.valueOf(r.getSubmittedBy()));
        d.setReviewedAt(ProductApprovalSupport.iso(r.getReviewedAt()));
        d.setReviewedBy(r.getReviewedBy() == null ? null : String.valueOf(r.getReviewedBy()));
        d.setRejectedReason(r.getRejectedReason());
        return d;
    }
}
