package com.pk.core.test.business;

import com.pk.core.business.repository.CartItemRepo;
import com.pk.core.business.repository.CartRepo;
import com.pk.core.business.repository.ProductRepo;
import com.pk.core.business.repository.ProductSkuRepo;
import com.pk.core.business.service.PreOrderService;
import com.pk.core.business.service.PromotionPricingService;
import com.pk.core.business.service.impl.CartServiceImpl;
import com.pk.core.common.exception.BusinessException;
import com.pk.core.common.exception.ErrorCode;
import com.pk.core.model.dto.request.CartSyncItemRequestDto;
import com.pk.core.model.dto.request.CartSyncRequestDto;
import com.pk.core.model.dto.response.CartSyncResponseDto;
import com.pk.core.model.entity.CartEntity;
import com.pk.core.model.entity.CartItemEntity;
import com.pk.core.model.entity.ProductEntity;
import com.pk.core.model.entity.ProductSkuEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CartBatchAddTest {

    private static final Long USER_ID = 7L;

    @Mock CartRepo carts;
    @Mock CartItemRepo cartItems;
    @Mock ProductRepo products;
    @Mock ProductSkuRepo productSkus;
    @Mock PromotionPricingService promotionPricing;
    @Mock PreOrderService preOrder;

    private CartServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CartServiceImpl(carts, cartItems, products, productSkus, promotionPricing, preOrder);
        lenient().when(products.findOne(anyLong())).thenAnswer(inv -> product(inv.getArgument(0)));
    }

    @Test
    void addsEveryLineAndKeepsExistingCart() {
        when(carts.findByUserId(USER_ID)).thenReturn(userCart(20L));
        when(productSkus.findOne(5L)).thenReturn(sku(5L, 100L, 10));
        when(productSkus.findOne(6L)).thenReturn(sku(6L, 101L, 10));
        when(cartItems.findByCartIdAndSkuId(20L, 5L)).thenReturn(null);
        when(cartItems.findByCartIdAndSkuId(20L, 6L)).thenReturn(null);
        when(cartItems.findByCartId(20L)).thenReturn(List.of(item(1L, 20L, 5L, 1), item(2L, 20L, 6L, 2)));

        CartSyncResponseDto res = service.batchAddStrict(USER_ID, null, batch("5", 1, "6", 2));

        assertTrue(res.isSuccess());
        assertNull(res.getReason());
        verify(cartItems, times(2)).create(any(CartItemEntity.class));
        verify(cartItems, never()).deleteByCartId(anyLong());
        verify(carts, never()).create(any());
    }

    @Test
    void oneOutOfStockRejectsTheWholeBatch() {
        when(carts.findByUserId(USER_ID)).thenReturn(userCart(20L));
        when(productSkus.findOne(5L)).thenReturn(sku(5L, 100L, 10));
        when(productSkus.findOne(6L)).thenReturn(sku(6L, 101L, 0));
        when(cartItems.findByCartIdAndSkuId(20L, 5L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.batchAddStrict(USER_ID, null, batch("5", 1, "6", 1)));

        assertEquals(ErrorCode.CART_BATCH_FAILED, ex.getCode());
        verify(cartItems, never()).create(any());
        verify(cartItems, never()).update(any());
    }

    @Test
    void unknownSkuRejectsBeforeAnyWrite() {
        when(productSkus.findOne(99L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.batchAddStrict(USER_ID, null, batch("99", 1)));

        assertEquals(ErrorCode.CART_BATCH_FAILED, ex.getCode());
        verify(cartItems, never()).create(any());
    }

    @Test
    void insufficientCombinedStockDoesNotUpdateTheLine() {
        when(carts.findByUserId(USER_ID)).thenReturn(userCart(20L));
        when(productSkus.findOne(5L)).thenReturn(sku(5L, 100L, 5));
        CartItemEntity existing = item(1L, 20L, 5L, 4);
        when(cartItems.findByCartIdAndSkuId(20L, 5L)).thenReturn(existing);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.batchAddStrict(USER_ID, null, batch("5", 2)));

        assertEquals(ErrorCode.CART_BATCH_FAILED, ex.getCode());
        verify(cartItems, never()).update(any());
        verify(cartItems, never()).create(any());
    }

    @Test
    void duplicateSkusAreSummedIntoOneLine() {
        when(carts.findByUserId(USER_ID)).thenReturn(userCart(20L));
        when(productSkus.findOne(5L)).thenReturn(sku(5L, 100L, 10));
        when(cartItems.findByCartIdAndSkuId(20L, 5L)).thenReturn(null);
        when(cartItems.findByCartId(20L)).thenReturn(List.of(item(1L, 20L, 5L, 3)));

        service.batchAddStrict(USER_ID, null, batch("5", 1, "5", 2));

        ArgumentCaptor<CartItemEntity> created = ArgumentCaptor.forClass(CartItemEntity.class);
        verify(cartItems, times(1)).create(created.capture());
        assertEquals(3, created.getValue().getQuantity());
        assertEquals(5L, created.getValue().getSkuId());
    }

    private static CartSyncRequestDto batch(Object... pairs) {
        List<CartSyncItemRequestDto> items = new java.util.ArrayList<>();
        for (int i = 0; i < pairs.length; i += 2) {
            items.add(new CartSyncItemRequestDto(String.valueOf(pairs[i]), null, (Integer) pairs[i + 1], null));
        }
        return new CartSyncRequestDto(true, items);
    }

    private static ProductSkuEntity sku(long id, long productId, int onHand) {
        ProductSkuEntity entity = new ProductSkuEntity("SKU-" + id, "M", BigDecimal.valueOf(1_000_000L), onHand);
        entity.setId(id);
        entity.setProductId(productId);
        entity.setName("Nhẫn " + id);
        entity.setStatus(ProductSkuEntity.PUBLISHED);
        entity.setDefault(true);
        return entity;
    }

    private static ProductEntity product(long id) {
        ProductEntity entity = new ProductEntity(1L, "P-" + id, "Sản phẩm " + id, "san-pham-" + id);
        entity.setId(id);
        entity.setStatus(ProductEntity.PUBLISHED);
        return entity;
    }

    private static CartEntity userCart(long cartId) {
        CartEntity cart = CartEntity.forUser(USER_ID);
        cart.setId(cartId);
        return cart;
    }

    private static CartItemEntity item(long id, long cartId, long skuId, int qty) {
        CartItemEntity entity = new CartItemEntity(cartId, skuId, qty);
        entity.setId(id);
        return entity;
    }
}
