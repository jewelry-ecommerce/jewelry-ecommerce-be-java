package com.pk.core.business.service.impl;

import lombok.RequiredArgsConstructor;

import com.pk.core.business.repository.ProductRepo;
import com.pk.core.business.repository.PromotionRepo;
import com.pk.core.business.service.PromotionService;
import com.pk.core.common.exception.ResourceNotFoundException;
import com.pk.core.model.dto.request.AdminPromotionRequestDto;
import com.pk.core.model.dto.response.PromotionResponseDto;
import com.pk.core.model.entity.PromotionEntity;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class PromotionServiceImpl implements PromotionService {

    private final PromotionRepo promotions;
    private final ProductRepo products;

    @Transactional(readOnly = true)
    @Override
    public List<PromotionResponseDto> findAll() {
        // findAll() (PkRepo/ScannableRepository) không tự lọc deleted_date - lọc tay ở đây (chưa có
        // tiền lệ SQL tự lọc soft-delete trong dự án, xem ProductRepo cho cách làm ở tầng SQL thay
        // vì đợt này giữ đơn giản ở tầng Java vì danh sách promotion không lớn).
        return promotions.findAll(Sort.unsorted()).stream()
                .filter(p -> p.getDeletedDate() == null)
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    @Override
    public PromotionResponseDto findById(Long promotionId) {
        return toDto(requirePromotion(promotionId));
    }

    @Transactional
    @Override
    public PromotionResponseDto create(AdminPromotionRequestDto req) {
        PromotionEntity p = new PromotionEntity();
        applyRequest(p, req);
        promotions.create(p);
        linkProducts(p.getId(), req.getProductIds());
        return toDto(p);
    }

    @Transactional
    @Override
    public PromotionResponseDto update(Long promotionId, AdminPromotionRequestDto req) {
        PromotionEntity p = requirePromotion(promotionId);
        applyRequest(p, req);
        p.touch();
        promotions.update(p);
        // Thay toàn bộ danh sách sản phẩm (xoá hết liên kết cũ rồi ghi lại) thay vì diff thêm/bớt -
        // đơn giản hơn, đủ dùng cho phạm vi đồ án (xem PromotionRepo javadoc).
        promotions.deleteAllByPromotionId(promotionId);
        promotions.deleteScope(promotionId);
        linkProducts(promotionId, req.getProductIds());
        return toDto(p);
    }

    @Transactional
    @Override
    public void delete(Long promotionId) {
        // Xoá mềm (set deletedDate), giống ProductServiceImpl.softDelete - bảng promotions có sẵn
        // cột deleted_id/deleted_date (extends BaseEntity) nên dùng đúng cột đó thay vì xoá thật.
        PromotionEntity p = requirePromotion(promotionId);
        p.setDeletedDate(new java.util.Date());
        p.touch();
        promotions.update(p);
    }

    private void linkProducts(Long promotionId, List<Long> productIds) {
        for (Long productId : productIds) {
            promotions.addProduct(promotionId, productId);
        }
    }

    private void applyRequest(PromotionEntity p, AdminPromotionRequestDto req) {
        String type = DiscountRules.type(req.getDiscountType(), java.util.Set.of("PERCENT", "FIXED", "FLAT_PRICE"), "chương trình khuyến mãi");
        DiscountRules.value(type, req.getDiscountValue(), req.getMaxDiscountAmount());
        DiscountRules.period(req.getStartsAt(), req.getEndsAt());
        String status = DiscountRules.status(req.getStatus());
        validateProducts(req.getProductIds());
        p.setName(req.getName().trim());
        p.setDiscountType(type);
        p.setDiscountValue(req.getDiscountValue());
        p.setMaxDiscountAmount("PERCENT".equals(type) ? req.getMaxDiscountAmount() : null);
        p.setStartsAt(req.getStartsAt());
        p.setEndsAt(req.getEndsAt());
        if (status != null) {
            p.setStatus(status);
        }
    }

    /** Sản phẩm áp dụng: không trùng, tối đa 100, phải tồn tại và chưa bị xoá (tránh lỗi khoá ngoại 500). */
    private void validateProducts(List<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            throw DiscountRules.invalid("Phải áp dụng cho ít nhất 1 sản phẩm", "productIds");
        }
        if (productIds.size() > DiscountRules.MAX_PRODUCTS_PER_PROMOTION) {
            throw DiscountRules.invalid("Tối đa " + DiscountRules.MAX_PRODUCTS_PER_PROMOTION + " sản phẩm mỗi chương trình", "productIds");
        }
        if (new java.util.HashSet<>(productIds).size() != productIds.size()) {
            throw DiscountRules.invalid("Danh sách sản phẩm bị trùng", "productIds");
        }
        for (Long id : productIds) {
            var product = id == null ? null : products.findOne(id);
            if (product == null || product.getDeletedDate() != null) {
                throw DiscountRules.invalid("Sản phẩm không tồn tại: " + id, "productIds");
            }
        }
    }

    private PromotionEntity requirePromotion(Long promotionId) {
        PromotionEntity p = promotions.findOne(promotionId);
        if (p == null || p.getDeletedDate() != null) {
            throw new ResourceNotFoundException("Chương trình khuyến mãi", "Promotion", promotionId);
        }
        return p;
    }

    private PromotionResponseDto toDto(PromotionEntity p) {
        return PromotionResponseDto.from(p, promotions.findProductIdsByPromotionId(p.getId()));
    }
}
