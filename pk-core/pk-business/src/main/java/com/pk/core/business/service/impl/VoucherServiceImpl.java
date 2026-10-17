package com.pk.core.business.service.impl;

import lombok.RequiredArgsConstructor;

import com.pk.core.business.repository.VoucherRepo;
import com.pk.core.business.service.VoucherService;
import com.pk.core.common.exception.BusinessException;
import com.pk.core.common.exception.ErrorCode;
import com.pk.core.common.exception.ResourceNotFoundException;
import com.pk.core.model.dto.request.AdminVoucherRequestDto;
import com.pk.core.model.dto.response.VoucherResponseDto;
import com.pk.core.model.entity.VoucherEntity;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class VoucherServiceImpl implements VoucherService {

    private final VoucherRepo vouchers;

    // noRollbackFor: computeQuote bắt BusinessException để báo REJECTED cho từng mã; nếu không, ngoại lệ đi qua proxy
    // giao dịch tham gia sẽ đánh dấu giao dịch ngoài rollback-only -> UnexpectedRollbackException (500) khi commit.
    @Transactional(readOnly = true, noRollbackFor = BusinessException.class)
    @Override
    public VoucherEntity validate(String code, BigDecimal subtotal) {
        VoucherEntity v = vouchers.findByCode(code.trim().toUpperCase(Locale.ROOT));
        if (v == null || !VoucherEntity.PUBLISHED.equals(v.getStatus())) {
            throw new ResourceNotFoundException("Voucher", "Voucher", code);
        }
        Date now = new Date();
        // Truyền `args` khớp placeholder {0}/{1} trong messages_vi.properties/messages_en.properties
        // (đã có sẵn từ 2026-09-23) - nếu không truyền, RestErrorHandleImpl vẫn dịch được message
        // nhưng {0}/{1} sẽ hiện nguyên văn ra FE thay vì được thay giá trị thật.
        if (now.before(v.getStartsAt())) {
            throw BusinessException.badRequest(ErrorCode.VOUCHER_NOT_STARTED,
                    "Voucher " + v.getCode() + " chưa tới thời gian áp dụng", v.getCode());
        }
        if (now.after(v.getEndsAt())) {
            throw BusinessException.badRequest(ErrorCode.VOUCHER_EXPIRED,
                    "Voucher " + v.getCode() + " đã hết hạn", v.getCode());
        }
        if (!v.isUsageAvailable()) {
            throw BusinessException.badRequest(ErrorCode.VOUCHER_USAGE_LIMIT_REACHED,
                    "Voucher " + v.getCode() + " đã hết lượt sử dụng", v.getCode());
        }
        if (subtotal.compareTo(v.getMinOrderValue()) < 0) {
            throw BusinessException.badRequest(ErrorCode.VOUCHER_MIN_ORDER_NOT_MET,
                    "Đơn hàng chưa đạt giá trị tối thiểu " + v.getMinOrderValue() + " để áp mã " + v.getCode(),
                    v.getMinOrderValue(), v.getCode());
        }
        return v;
    }

    @Transactional(readOnly = true, noRollbackFor = BusinessException.class)
    @Override
    public Applied apply(String code, List<Line> lines) {
        VoucherEntity v = vouchers.findByCode(code == null ? "" : code.trim().toUpperCase(Locale.ROOT));
        if (v == null || !VoucherEntity.PUBLISHED.equals(v.getStatus())) {
            throw new ResourceNotFoundException("Voucher", "Voucher", code);
        }
        Set<Long> include = new HashSet<>(vouchers.findScopeSkuIds(v.getId(), "INCLUDE"));
        Set<Long> exclude = new HashSet<>(vouchers.findScopeSkuIds(v.getId(), "EXCLUDE"));
        boolean scoped = !include.isEmpty() || !exclude.isEmpty();
        BigDecimal base = BigDecimal.ZERO;
        int eligible = 0;
        for (Line l : lines) {
            boolean ok = (include.isEmpty() || include.contains(l.skuId())) && !exclude.contains(l.skuId());
            if (ok) {
                base = base.add(l.net());
                eligible++;
            }
        }
        if (scoped && eligible == 0) {
            throw BusinessException.badRequest("VOUCHER_NO_ELIGIBLE_ITEMS",
                    "Voucher " + v.getCode() + " không áp dụng cho sản phẩm nào trong đơn hàng", v.getCode());
        }
        validate(v.getCode(), base); // thời gian, lượt dùng, giá trị tối thiểu (trên phần đủ điều kiện)
        return new Applied(v, v.computeDiscount(base));
    }

    @Transactional
    @Override
    public void applyUsage(Long voucherId, String code) {
        if (vouchers.incrementUsage(voucherId) == 0) {
            throw BusinessException.badRequest(ErrorCode.VOUCHER_USAGE_LIMIT_REACHED,
                    "Voucher " + code + " vừa hết lượt sử dụng, vui lòng thử lại", code);
        }
    }

    @Transactional
    @Override
    public void releaseUsage(String code) {
        VoucherEntity v = code == null || code.isBlank() ? null : vouchers.findByCode(code.trim().toUpperCase(Locale.ROOT));
        if (v != null) {
            vouchers.decrementUsage(v.getId());
        }
    }

    // ===================== ADMIN (mục K spec) =====================

    @Transactional(readOnly = true)
    @Override
    public List<VoucherResponseDto> findAllForAdmin() {
        return vouchers.findAll(Sort.unsorted()).stream().map(VoucherResponseDto::from).toList();
    }

    @Transactional
    @Override
    public VoucherResponseDto create(AdminVoucherRequestDto req) {
        String code = DiscountRules.voucherCode(req.getCode());
        if (vouchers.findByCode(code) != null) {
            throw BusinessException.conflict(ErrorCode.DUPLICATE_CODE, "Mã voucher đã tồn tại: " + code, code);
        }
        VoucherEntity v = new VoucherEntity();
        v.setCode(code);
        applyRequest(v, req);
        vouchers.create(v);
        return VoucherResponseDto.from(v);
    }

    @Transactional
    @Override
    public VoucherResponseDto update(Long voucherId, AdminVoucherRequestDto req) {
        VoucherEntity v = requireVoucher(voucherId);
        String code = DiscountRules.voucherCode(req.getCode());
        if (!code.equals(v.getCode()) && vouchers.findByCode(code) != null) {
            throw BusinessException.conflict(ErrorCode.DUPLICATE_CODE, "Mã voucher đã tồn tại: " + code, code);
        }
        v.setCode(code);
        applyRequest(v, req);
        v.touch();
        vouchers.update(v);
        return VoucherResponseDto.from(v);
    }

    @Transactional
    @Override
    public void delete(Long voucherId) {
        vouchers.delete(requireVoucher(voucherId));
    }

    private void applyRequest(VoucherEntity v, AdminVoucherRequestDto req) {
        String rawType = req.getDiscountType() == null ? "" : req.getDiscountType().trim().toUpperCase(Locale.ROOT);
        if ("FLAT_PRICE".equals(rawType)) {
            throw DiscountRules.invalid("Voucher không hỗ trợ giảm đồng giá (FLAT_PRICE); dùng PERCENT hoặc FIXED", "discountType");
        }
        String type = DiscountRules.type(rawType, java.util.Set.of("PERCENT", "FIXED"), "voucher");
        DiscountRules.value(type, req.getDiscountValue(), req.getMaxDiscountAmount());
        DiscountRules.period(req.getStartsAt(), req.getEndsAt());
        String status = DiscountRules.status(req.getStatus());
        if (req.getUsageLimit() != null && req.getUsageLimit() < 1) {
            throw DiscountRules.invalid("Giới hạn lượt dùng phải từ 1 trở lên (bỏ trống nếu không giới hạn)", "usageLimit");
        }
        if (req.getUsageLimit() != null && req.getUsageLimit() < v.getUsedCount()) {
            throw DiscountRules.invalid("Giới hạn lượt dùng không được nhỏ hơn số lượt đã dùng (" + v.getUsedCount() + ")", "usageLimit");
        }
        v.setDiscountType(type);
        v.setDiscountValue(req.getDiscountValue());
        v.setMaxDiscountAmount("PERCENT".equals(type) ? req.getMaxDiscountAmount() : null);
        v.setMinOrderValue(req.getMinOrderValue() != null ? req.getMinOrderValue() : BigDecimal.ZERO);
        v.setUsageLimit(req.getUsageLimit());
        v.setStartsAt(req.getStartsAt());
        v.setEndsAt(req.getEndsAt());
        if (status != null) {
            v.setStatus(status);
        }
    }

    private VoucherEntity requireVoucher(Long voucherId) {
        VoucherEntity v = vouchers.findOne(voucherId);
        if (v == null) {
            throw new ResourceNotFoundException("Voucher", "Voucher", voucherId);
        }
        return v;
    }
}
