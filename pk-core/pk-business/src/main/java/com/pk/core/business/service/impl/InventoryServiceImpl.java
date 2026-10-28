package com.pk.core.business.service.impl;

import lombok.RequiredArgsConstructor;

import com.pk.core.business.repository.InventoryMovementRepo;
import com.pk.core.business.repository.ProductSkuRepo;
import com.pk.core.business.service.InventoryService;
import com.pk.core.common.exception.BusinessException;
import com.pk.core.common.exception.ErrorCode;
import com.pk.core.common.exception.ResourceNotFoundException;
import com.pk.core.common.web.PageResponse;
import com.pk.core.model.dto.request.AdminStockAdjustmentRequestDto;
import com.pk.core.model.dto.response.InventoryMovementResponseDto;
import com.pk.core.model.dto.response.StockLevelResponseDto;
import com.pk.core.model.entity.InventoryMovementEntity;
import com.pk.core.model.entity.ProductSkuEntity;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class InventoryServiceImpl implements InventoryService {

    /** Kho ngầm định duy nhất - xem javadoc InventoryService. */
    public static final String MAIN_WAREHOUSE_ID = "MAIN";

    private static final Set<String> REASONS = Set.of("RECEIPT", "DAMAGE", "CORRECTION");
    private static final int DEFAULT_TAKE = 20;
    private static final int MAX_TAKE = 100;

    private final ProductSkuRepo productSkus;
    private final InventoryMovementRepo movements;

    @Transactional(readOnly = true)
    @Override
    public List<StockLevelResponseDto> stockLevels(String keyword) {
        String kw = keyword != null ? keyword.trim().toLowerCase() : "";
        return productSkus.findAll(Sort.unsorted()).stream()
                .filter(s -> kw.isBlank()
                        || s.getSkuCode().toLowerCase().contains(kw)
                        || (s.getName() != null && s.getName().toLowerCase().contains(kw)))
                .map(s -> StockLevelResponseDto.from(s, MAIN_WAREHOUSE_ID))
                .toList();
    }

    /**
     * Chính sách dấu: RECEIPT chỉ tăng (delta > 0), DAMAGE chỉ giảm (delta < 0), CORRECTION tăng hoặc giảm (delta != 0).
     * delta = 0, vượt INT, âm tồn hoặc giảm xuống dưới phần đang giữ chỗ đều bị từ chối với mã lỗi ổn định
     * (400 STOCK_ADJUSTMENT_INVALID | 422 NEGATIVE_STOCK / STOCK_OVERFLOW / STOCK_BELOW_RESERVED). reserved không bị sửa.
     * Thành công thì ghi sổ kho cùng transaction; có Idempotency-Key thì gửi lại cùng nội dung không cộng lần hai.
     */
    @Transactional
    @Override
    public StockLevelResponseDto adjust(AdminStockAdjustmentRequestDto req, Long actorId, String idempotencyKey) {
        int delta = req.getQuantityDelta();
        validateRequest(req.getReason(), delta);
        String key = InventoryMovementSupport.normalizeKey(idempotencyKey);
        String hash = InventoryMovementSupport.requestHash(req);
        StockLevelResponseDto replay = replayIfDuplicate(actorId, key, hash);
        if (replay != null) {
            return replay;
        }
        ProductSkuEntity sku = productSkus.findOne(req.getSkuId());
        if (sku == null) {
            throw new ResourceNotFoundException("Biến thể sản phẩm", "ProductSku", req.getSkuId());
        }
        checkBounds(sku, delta);
        // UPDATE có điều kiện nguyên tử là chốt chặn cuối chống race giữa lúc đọc và lúc ghi.
        if (productSkus.adjustOnHandChecked(req.getSkuId(), delta) == 0) {
            ProductSkuEntity fresh = productSkus.findOne(req.getSkuId());
            checkBounds(fresh, delta);
            throw BusinessException.conflict(ErrorCode.STOCK_CONFLICT, "Tồn kho vừa thay đổi, vui lòng thử lại");
        }
        ProductSkuEntity updated = productSkus.findOne(req.getSkuId());
        writeLedger(req, updated, actorId, key, hash);
        return StockLevelResponseDto.from(updated, MAIN_WAREHOUSE_ID);
    }

    /** Cùng người làm + cùng khoá: cùng nội dung -> trả tồn hiện tại (không cộng lại); khác nội dung -> 409. */
    private StockLevelResponseDto replayIfDuplicate(Long actorId, String key, String hash) {
        if (key == null) {
            return null;
        }
        InventoryMovementEntity existing = movements.findByActorAndKey(actorId, key);
        if (existing == null) {
            return null;
        }
        if (!hash.equals(existing.getRequestHash())) {
            throw BusinessException.conflict(ErrorCode.IDEMPOTENCY_KEY_REUSED,
                    "Idempotency-Key này đã được dùng cho một yêu cầu khác với nội dung khác");
        }
        return StockLevelResponseDto.from(productSkus.findOne(existing.getSkuId()), MAIN_WAREHOUSE_ID);
    }

    /**
     * Ghi 1 dòng sổ. `updated` đọc sau UPDATE trong cùng transaction nên hàng đang bị khoá: tồn trước = tồn sau - delta.
     * Hai request cùng khoá chạy đồng thời: request chậm vi phạm UNIQUE -> rollback cả điều chỉnh, báo 409 để gửi lại.
     */
    private void writeLedger(AdminStockAdjustmentRequestDto req, ProductSkuEntity updated, Long actorId, String key,
                             String hash) {
        InventoryMovementEntity m = new InventoryMovementEntity();
        m.setSkuId(updated.getId());
        m.setWarehouseId(MAIN_WAREHOUSE_ID);
        m.setReason(req.getReason());
        m.setQuantityDelta(req.getQuantityDelta());
        m.setOnHandAfter(updated.getOnHand());
        m.setOnHandBefore(updated.getOnHand() - req.getQuantityDelta());
        m.setReservedAfter(updated.getReserved());
        m.setReference(InventoryMovementSupport.clean(req.getReference()));
        m.setNote(InventoryMovementSupport.clean(req.getNote()));
        m.setIdemKey(key);
        m.setRequestHash(key == null ? null : hash);
        m.setCreatedId(actorId);
        m.setCreatedDate(new Date());
        try {
            movements.create(m);
        } catch (DataIntegrityViolationException ex) {
            throw BusinessException.conflict(ErrorCode.STOCK_CONFLICT,
                    "Yêu cầu cùng Idempotency-Key đang được xử lý, vui lòng gửi lại");
        }
    }

    @Transactional(readOnly = true)
    @Override
    public PageResponse<InventoryMovementResponseDto> movements(Long skuId, String reason, String from, String to,
                                                                int page, int take) {
        String reasonFilter = InventoryMovementSupport.clean(reason);
        if (reasonFilter != null && !REASONS.contains(reasonFilter.toUpperCase(Locale.ROOT))) {
            throw BusinessException.badRequest(ErrorCode.VALIDATION_FAILED,
                    "reason không hợp lệ (chỉ nhận RECEIPT/DAMAGE/CORRECTION): " + reason, reason).withField("reason");
        }
        reasonFilter = reasonFilter == null ? "" : reasonFilter.toUpperCase(Locale.ROOT);
        Date[] range = InventoryMovementSupport.range(from, to);
        long sku = skuId == null ? 0L : skuId;
        int p = page > 0 ? page : 1;
        int size = take > 0 ? Math.min(take, MAX_TAKE) : DEFAULT_TAKE;
        Long total = movements.countSearch(sku, reasonFilter, range[0], range[1]);
        List<InventoryMovementEntity> rows = movements.search(sku, reasonFilter, range[0], range[1], size, (p - 1) * size);
        long count = total == null ? 0L : total;
        return new PageResponse<>(toDtos(rows), p, size, count, (int) Math.ceil(count / (double) size));
    }

    @Transactional(readOnly = true)
    @Override
    public InventoryMovementResponseDto movement(Long id) {
        InventoryMovementEntity m = id == null ? null : movements.findOne(id);
        if (m == null) {
            throw new ResourceNotFoundException("Dòng sổ kho", "InventoryMovement", id);
        }
        return toDtos(List.of(m)).get(0);
    }

    private List<InventoryMovementResponseDto> toDtos(List<InventoryMovementEntity> rows) {
        Map<Long, ProductSkuEntity> skus = new HashMap<>();
        for (InventoryMovementEntity r : rows) {
            skus.computeIfAbsent(r.getSkuId(), id -> productSkus.findOne(id));
        }
        return rows.stream().map(r -> {
            ProductSkuEntity s = skus.get(r.getSkuId());
            return InventoryMovementResponseDto.from(r, s == null ? null : s.getSkuCode(), s == null ? null : s.getName());
        }).toList();
    }

    private static void validateRequest(String reason, int delta) {
        if (!REASONS.contains(reason)) {
            throw BusinessException.badRequest(ErrorCode.VALIDATION_FAILED,
                    "reason không hợp lệ (chỉ nhận RECEIPT/DAMAGE/CORRECTION): " + reason, reason);
        }
        String problem = null;
        if (delta == 0) {
            problem = "quantityDelta không được bằng 0";
        } else if ("RECEIPT".equals(reason) && delta < 0) {
            problem = "RECEIPT (nhập hàng) chỉ nhận quantityDelta dương; muốn giảm hãy dùng DAMAGE hoặc CORRECTION";
        } else if ("DAMAGE".equals(reason) && delta > 0) {
            problem = "DAMAGE (hư hỏng/mất) chỉ nhận quantityDelta âm; muốn tăng hãy dùng RECEIPT hoặc CORRECTION";
        }
        if (problem != null) {
            throw BusinessException.badRequest(ErrorCode.STOCK_ADJUSTMENT_INVALID,
                    "Điều chỉnh tồn kho không hợp lệ: " + problem, problem).withField("quantityDelta");
        }
    }

    /** Kiểm tra theo số liệu hiện tại (dùng long để không tràn INT); ném đúng lỗi nghiệp vụ nếu vi phạm. */
    private static void checkBounds(ProductSkuEntity sku, int delta) {
        long after = (long) sku.getOnHand() + delta;
        if (after < 0) {
            throw BusinessException.unprocessable(ErrorCode.NEGATIVE_STOCK,
                    "Điều chỉnh tồn kho không hợp lệ: số lượng sau điều chỉnh sẽ âm (" + after + ")", after);
        }
        if (after > Integer.MAX_VALUE) {
            throw BusinessException.unprocessable(ErrorCode.STOCK_OVERFLOW,
                    "Số lượng sau điều chỉnh (" + after + ") vượt giới hạn cho phép", after);
        }
        long held = Math.min(sku.getReserved(), sku.getOnHand());
        if (delta < 0 && after < held) {
            throw BusinessException.unprocessable(ErrorCode.STOCK_BELOW_RESERVED,
                    "Không thể giảm tồn xuống " + after + ": " + held + " sản phẩm đang được giữ cho đơn hàng",
                    after, held);
        }
    }
}
