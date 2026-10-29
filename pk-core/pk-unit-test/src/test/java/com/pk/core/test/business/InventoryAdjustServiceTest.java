package com.pk.core.test.business;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import com.pk.core.business.repository.InventoryMovementRepo;
import com.pk.core.business.repository.ProductSkuRepo;
import com.pk.core.business.service.impl.InventoryServiceImpl;
import com.pk.core.common.exception.BusinessException;
import com.pk.core.common.exception.ErrorCode;
import com.pk.core.model.dto.request.AdminStockAdjustmentRequestDto;
import com.pk.core.model.dto.response.StockLevelResponseDto;
import com.pk.core.model.entity.InventoryMovementEntity;
import com.pk.core.model.entity.ProductSkuEntity;

/** Quy tắc điều chỉnh kho (INV-01): delta 0, dấu theo lý do, tràn INT, âm tồn, giảm dưới phần đang giữ chỗ, race. */
@ExtendWith(MockitoExtension.class)
class InventoryAdjustServiceTest {

    private static final long SKU = 7L;

    private static final long ACTOR = 3L;

    @Mock ProductSkuRepo productSkus;
    @Mock InventoryMovementRepo movements;
    InventoryServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new InventoryServiceImpl(productSkus, movements);
    }

    private static ProductSkuEntity sku(int onHand, int reserved) {
        ProductSkuEntity s = new ProductSkuEntity("SKU-7", "M", BigDecimal.TEN, onHand);
        s.setId(SKU);
        s.setReserved(reserved);
        return s;
    }

    private static AdminStockAdjustmentRequestDto req(String reason, int delta) {
        return new AdminStockAdjustmentRequestDto(SKU, delta, reason, null, "PN-01", "Nhập lô mới");
    }

    private BusinessException rejected(String reason, int delta) {
        return assertThrows(BusinessException.class, () -> service.adjust(req(reason, delta), ACTOR, null));
    }

    private void neverWrites() {
        verify(productSkus, never()).adjustOnHandChecked(anyLong(), anyInt());
        verify(movements, never()).create(any(InventoryMovementEntity.class));
    }

    @Test
    void zeroDeltaIsRejectedBeforeAnyRead() {
        BusinessException ex = rejected("RECEIPT", 0);
        assertEquals(400, ex.getStatus());
        assertEquals(ErrorCode.STOCK_ADJUSTMENT_INVALID, ex.getCode());
        assertEquals("quantityDelta", ex.getField());
        neverWrites();
    }

    @Test
    void signMustMatchReason() {
        assertEquals(ErrorCode.STOCK_ADJUSTMENT_INVALID, rejected("RECEIPT", -1).getCode());
        assertEquals(ErrorCode.STOCK_ADJUSTMENT_INVALID, rejected("DAMAGE", 1).getCode());
        assertEquals(ErrorCode.VALIDATION_FAILED, rejected("OTHER", 1).getCode());
        neverWrites();
    }

    @Test
    void correctionAcceptsBothSigns() {
        when(productSkus.findOne(SKU)).thenReturn(sku(10, 0), sku(12, 0));
        when(productSkus.adjustOnHandChecked(SKU, 2)).thenReturn(1);
        StockLevelResponseDto out = service.adjust(req("CORRECTION", 2), ACTOR, null);
        assertEquals(12, out.getOnHand());
    }

    @Test
    void receiptIncreasesAndKeepsReserved() {
        when(productSkus.findOne(SKU)).thenReturn(sku(10, 4), sku(15, 4));
        when(productSkus.adjustOnHandChecked(SKU, 5)).thenReturn(1);
        StockLevelResponseDto out = service.adjust(req("RECEIPT", 5), ACTOR, null);
        assertEquals(15, out.getOnHand());
        assertEquals(4, out.getReserved());
        assertEquals(11, out.getAvailable());
    }

    @Test
    void overflowAndNegativeAreUnprocessable() {
        when(productSkus.findOne(SKU)).thenReturn(sku(Integer.MAX_VALUE - 1, 0));
        BusinessException over = rejected("RECEIPT", 5);
        assertEquals(422, over.getStatus());
        assertEquals(ErrorCode.STOCK_OVERFLOW, over.getCode());
        when(productSkus.findOne(SKU)).thenReturn(sku(3, 0));
        assertEquals(ErrorCode.NEGATIVE_STOCK, rejected("DAMAGE", -4).getCode());
        neverWrites();
    }

    @Test
    void cannotReduceBelowReservedForNormalStock() {
        when(productSkus.findOne(SKU)).thenReturn(sku(10, 6));
        BusinessException ex = rejected("DAMAGE", -5);
        assertEquals(422, ex.getStatus());
        assertEquals(ErrorCode.STOCK_BELOW_RESERVED, ex.getCode());
        neverWrites();
    }

    @Test
    void backorderedSkuCannotBeReducedFurtherButCanBeReceived() {
        when(productSkus.findOne(SKU)).thenReturn(sku(2, 5), sku(2, 5), sku(5, 5));
        assertEquals(ErrorCode.STOCK_BELOW_RESERVED, rejected("DAMAGE", -1).getCode());
        when(productSkus.adjustOnHandChecked(SKU, 3)).thenReturn(1);
        assertEquals(5, service.adjust(req("RECEIPT", 3), ACTOR, null).getOnHand());
    }

    @Test
    void reduceUpToReservedIsAllowed() {
        when(productSkus.findOne(SKU)).thenReturn(sku(10, 6), sku(6, 6));
        when(productSkus.adjustOnHandChecked(SKU, -4)).thenReturn(1);
        assertEquals(6, service.adjust(req("DAMAGE", -4), ACTOR, null).getOnHand());
    }

    @Test
    void raceLostReportsPreciseReasonOrConflict() {
        // Đọc ban đầu hợp lệ, nhưng lúc ghi tồn đã đổi: đọc lại thấy vi phạm -> báo đúng lý do.
        when(productSkus.findOne(SKU)).thenReturn(sku(10, 0), sku(10, 9));
        when(productSkus.adjustOnHandChecked(SKU, -5)).thenReturn(0);
        assertEquals(ErrorCode.STOCK_BELOW_RESERVED, rejected("DAMAGE", -5).getCode());
        // Đọc lại vẫn hợp lệ (thua race nhưng điều kiện hết vi phạm) -> 409 STOCK_CONFLICT để thử lại.
        when(productSkus.findOne(SKU)).thenReturn(sku(10, 0), sku(10, 0));
        BusinessException conflict = rejected("DAMAGE", -5);
        assertEquals(409, conflict.getStatus());
        assertEquals(ErrorCode.STOCK_CONFLICT, conflict.getCode());
    }

    @Test
    void successfulAdjustmentWritesLedgerRowWithBeforeAfter() {
        when(productSkus.findOne(SKU)).thenReturn(sku(10, 4), sku(15, 4));
        when(productSkus.adjustOnHandChecked(SKU, 5)).thenReturn(1);
        service.adjust(req("RECEIPT", 5), ACTOR, null);
        ArgumentCaptor<InventoryMovementEntity> saved = ArgumentCaptor.forClass(InventoryMovementEntity.class);
        verify(movements).create(saved.capture());
        InventoryMovementEntity m = saved.getValue();
        assertEquals(SKU, m.getSkuId());
        assertEquals("RECEIPT", m.getReason());
        assertEquals(5, m.getQuantityDelta());
        assertEquals(10, m.getOnHandBefore());
        assertEquals(15, m.getOnHandAfter());
        assertEquals(4, m.getReservedAfter());
        assertEquals("MAIN", m.getWarehouseId());
        assertEquals("PN-01", m.getReference());
        assertEquals(ACTOR, m.getCreatedId());
        assertNull(m.getIdemKey());
    }

    @Test
    void rejectedAdjustmentWritesNoLedgerRow() {
        when(productSkus.findOne(SKU)).thenReturn(sku(3, 0));
        assertEquals(ErrorCode.NEGATIVE_STOCK, rejected("DAMAGE", -4).getCode());
        verify(movements, never()).create(any(InventoryMovementEntity.class));
    }

    @Test
    void sameKeyAndBodyReplaysWithoutAddingTwice() {
        AdminStockAdjustmentRequestDto body = req("RECEIPT", 5);
        // Lần 1: chưa có khoá -> cộng và ghi sổ kèm khoá + hash.
        when(movements.findByActorAndKey(ACTOR, "key-12345678")).thenReturn(null);
        when(productSkus.findOne(SKU)).thenReturn(sku(10, 0), sku(15, 0));
        when(productSkus.adjustOnHandChecked(SKU, 5)).thenReturn(1);
        service.adjust(body, ACTOR, " key-12345678 ");
        ArgumentCaptor<InventoryMovementEntity> saved = ArgumentCaptor.forClass(InventoryMovementEntity.class);
        verify(movements).create(saved.capture());
        InventoryMovementEntity first = saved.getValue();
        assertEquals("key-12345678", first.getIdemKey());
        // Lần 2: cùng khoá + cùng nội dung -> trả tồn hiện tại, KHÔNG cộng lần nữa.
        when(movements.findByActorAndKey(ACTOR, "key-12345678")).thenReturn(first);
        when(productSkus.findOne(SKU)).thenReturn(sku(15, 0));
        StockLevelResponseDto again = service.adjust(body, ACTOR, "key-12345678");
        assertEquals(15, again.getOnHand());
        verify(productSkus).adjustOnHandChecked(SKU, 5);
        verify(movements).create(any(InventoryMovementEntity.class));
    }

    @Test
    void sameKeyDifferentBodyIsConflict() {
        InventoryMovementEntity stored = new InventoryMovementEntity();
        stored.setSkuId(SKU);
        stored.setRequestHash("khac-hoan-toan");
        when(movements.findByActorAndKey(ACTOR, "key-12345678")).thenReturn(stored);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.adjust(req("RECEIPT", 9), ACTOR, "key-12345678"));
        assertEquals(409, ex.getStatus());
        assertEquals(ErrorCode.IDEMPOTENCY_KEY_REUSED, ex.getCode());
        neverWrites();
    }

    @Test
    void malformedKeyIsRejectedBeforeAnyWork() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.adjust(req("RECEIPT", 1), ACTOR, "ngan"));
        assertEquals(400, ex.getStatus());
        assertEquals(ErrorCode.IDEMPOTENCY_KEY_INVALID, ex.getCode());
        neverWrites();
    }

    @Test
    void concurrentSameKeyLosesOnUniqueViolationAndReportsRetry() {
        when(movements.findByActorAndKey(ACTOR, "key-12345678")).thenReturn(null);
        when(productSkus.findOne(SKU)).thenReturn(sku(10, 0), sku(15, 0));
        when(productSkus.adjustOnHandChecked(SKU, 5)).thenReturn(1);
        doThrow(new DuplicateKeyException("uq_inventory_movements_idem")).when(movements)
                .create(any(InventoryMovementEntity.class));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.adjust(req("RECEIPT", 5), ACTOR, "key-12345678"));
        assertEquals(409, ex.getStatus());
        assertEquals(ErrorCode.STOCK_CONFLICT, ex.getCode());
    }

    @Test
    void movementFiltersAreValidated() {
        BusinessException badReason = assertThrows(BusinessException.class,
                () -> service.movements(null, "RANDOM", null, null, 1, 20));
        assertEquals("reason", badReason.getField());
        BusinessException badDate = assertThrows(BusinessException.class,
                () -> service.movements(null, null, "hom-qua", null, 1, 20));
        assertEquals("from", badDate.getField());
    }
}
