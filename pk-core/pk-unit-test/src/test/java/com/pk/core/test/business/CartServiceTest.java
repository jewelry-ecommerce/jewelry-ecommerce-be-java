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
import com.pk.core.common.exception.ResourceNotFoundException;
import com.pk.core.model.dto.request.CartSyncItemRequestDto;
import com.pk.core.model.dto.request.CartSyncRequestDto;
import com.pk.core.model.dto.request.CartTotalItemRequestDto;
import com.pk.core.model.dto.request.CartTotalRequestDto;
import com.pk.core.model.dto.response.CartRecommendationResponseDto;
import com.pk.core.model.dto.response.CartSyncResponseDto;
import com.pk.core.model.dto.response.CartTotalResponseDto;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {

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
        // Mặc định không có khuyến mãi; test riêng ghi đè.
        lenient().when(promotionPricing.unitDiscount(any(), any())).thenReturn(BigDecimal.ZERO);
        // Mặc định mọi sản phẩm cha đang PUBLISHED (SKU chỉ bán được khi sản phẩm cha bán được); test riêng ghi đè.
        lenient().when(products.findOne(anyLong())).thenAnswer(inv -> product(inv.getArgument(0, Long.class), 1L));
    }

    // ------------------------------------------------------------------ fixtures

    private static ProductSkuEntity sku(long id, long productId, int onHand, long listPrice, Long salePrice) {
        ProductSkuEntity s = new ProductSkuEntity("SKU-" + id, "M", BigDecimal.valueOf(listPrice), onHand);
        s.setId(id);
        s.setProductId(productId);
        s.setName("Nhẫn " + id);
        s.setStatus(ProductSkuEntity.PUBLISHED);
        s.setDefault(true);
        if (salePrice != null) {
            s.setSalePrice(BigDecimal.valueOf(salePrice));
        }
        return s;
    }

    private static ProductEntity product(long id, long categoryId) {
        ProductEntity p = new ProductEntity(categoryId, "P-" + id, "Sản phẩm " + id, "san-pham-" + id);
        p.setId(id);
        p.setStatus(ProductEntity.PUBLISHED);
        return p;
    }

    private static CartEntity userCart(long cartId) {
        CartEntity c = CartEntity.forUser(USER_ID);
        c.setId(cartId);
        return c;
    }

    private static CartItemEntity item(long id, long cartId, long skuId, int qty) {
        CartItemEntity i = new CartItemEntity(cartId, skuId, qty);
        i.setId(id);
        return i;
    }

    private static CartSyncRequestDto sync(boolean clearAll, String variationId, int qty) {
        return new CartSyncRequestDto(clearAll, List.of(new CartSyncItemRequestDto(variationId, null, qty, null)));
    }

    // ------------------------------------------------------------------ addOrSync

    @Test
    void guestWithoutGuestIdGetsNewGuestIdAndItemIsAdded() {
        when(carts.create(any(CartEntity.class))).thenAnswer(inv -> {
            CartEntity c = inv.getArgument(0);
            c.setId(10L);
            return c;
        });
        ProductSkuEntity s = sku(5L, 100L, 10, 1_000_000, null);
        when(productSkus.findOne(5L)).thenReturn(s);
        when(cartItems.findByCartIdAndSkuId(10L, 5L)).thenReturn(null);
        when(cartItems.findByCartId(10L)).thenReturn(List.of(item(1L, 10L, 5L, 2)));
        when(products.findOne(100L)).thenReturn(product(100L, 1L));

        CartSyncResponseDto res = service.addOrSync(null, null, sync(false, "5", 2));

        assertTrue(res.isSuccess());
        assertNotNull(res.getGuestId());
        assertEquals(1, res.getItems().size());
        assertEquals(2, res.getItems().get(0).getQuantity());
        assertEquals("5", res.getItems().get(0).getVariationId());
        assertEquals(1_000_000L, res.getItems().get(0).getSellingPriceAfterTaxMinor());
        ArgumentCaptor<CartItemEntity> created = ArgumentCaptor.forClass(CartItemEntity.class);
        verify(cartItems).create(created.capture());
        assertEquals(2, created.getValue().getQuantity());
        assertEquals(10L, created.getValue().getCartId());
    }

    @Test
    void quantityIsCappedByStockAndReasonIsReported() {
        when(carts.findByUserId(USER_ID)).thenReturn(userCart(20L));
        ProductSkuEntity s = sku(5L, 100L, 4, 1_000_000, null);
        when(productSkus.findOne(5L)).thenReturn(s);
        CartItemEntity existing = item(1L, 20L, 5L, 3);
        when(cartItems.findByCartIdAndSkuId(20L, 5L)).thenReturn(existing);
        when(cartItems.findByCartId(20L)).thenReturn(List.of(existing));
        when(products.findOne(100L)).thenReturn(product(100L, 1L));

        CartSyncResponseDto res = service.addOrSync(USER_ID, null, sync(false, "5", 2));

        assertFalse(res.isSuccess());
        assertEquals("INSUFFICIENT_STOCK", res.getReason());
        assertEquals(4, res.getItems().get(0).getQuantity());
        assertNull(res.getGuestId());
        verify(cartItems).update(existing);
        verify(cartItems, never()).create(any(CartItemEntity.class));
    }

    @Test
    void outOfStockSkuIsSkippedWithReason() {
        when(carts.findByUserId(USER_ID)).thenReturn(userCart(20L));
        when(productSkus.findOne(5L)).thenReturn(sku(5L, 100L, 0, 1_000_000, null));
        when(cartItems.findByCartId(20L)).thenReturn(List.of());

        CartSyncResponseDto res = service.addOrSync(USER_ID, null, sync(false, "5", 1));

        assertFalse(res.isSuccess());
        assertEquals("OUT_OF_STOCK", res.getReason());
        assertTrue(res.getItems().isEmpty());
        verify(cartItems, never()).create(any(CartItemEntity.class));
    }

    @Test
    void unknownSkuIsSkippedAsNotFound() {
        when(carts.findByUserId(USER_ID)).thenReturn(userCart(20L));
        when(productSkus.findOne(99L)).thenReturn(null);
        when(cartItems.findByCartId(20L)).thenReturn(List.of());

        CartSyncResponseDto res = service.addOrSync(USER_ID, null, sync(false, "99", 1));

        assertFalse(res.isSuccess());
        assertEquals("NOT_FOUND", res.getReason());
    }

    @Test
    void clearAllDeletesExistingLinesFirst() {
        when(carts.findByUserId(USER_ID)).thenReturn(userCart(20L));
        when(productSkus.findOne(5L)).thenReturn(sku(5L, 100L, 10, 1_000_000, null));
        when(cartItems.findByCartIdAndSkuId(20L, 5L)).thenReturn(null);
        when(cartItems.findByCartId(20L)).thenReturn(List.of(item(2L, 20L, 5L, 1)));
        when(products.findOne(100L)).thenReturn(product(100L, 1L));

        service.addOrSync(USER_ID, null, sync(true, "5", 1));

        verify(cartItems).deleteByCartId(20L);
        verify(cartItems).create(any(CartItemEntity.class));
    }

    @Test
    void nonNumericVariationIdIsRejectedBeforeAnyWrite() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.addOrSync(USER_ID, null, sync(false, "abc", 1)));

        assertEquals(ErrorCode.CART_ITEM_INVALID, ex.getCode());
        verify(carts, never()).create(any(CartEntity.class));
    }

    @Test
    void setLinesAreRejected() {
        CartSyncRequestDto req = new CartSyncRequestDto(false,
                List.of(new CartSyncItemRequestDto(null, "3", 1, null)));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.addOrSync(USER_ID, null, req));

        assertEquals(ErrorCode.CART_ITEM_INVALID, ex.getCode());
        verify(carts, never()).create(any(CartEntity.class));
    }

    // ------------------------------------------------------------------ get / merge

    @Test
    void getWithoutCartReturnsEmptyList() {
        CartSyncResponseDto res = service.get(null, null);

        assertTrue(res.isSuccess());
        assertTrue(res.getItems().isEmpty());
        assertNull(res.getGuestId());
    }

    @Test
    void mergeMovesGuestLinesIntoUserCartAndDeletesGuestCart() {
        when(carts.findByUserId(USER_ID)).thenReturn(userCart(20L));
        CartEntity guest = CartEntity.forGuest("g-1");
        guest.setId(30L);
        when(carts.findByGuestId("g-1")).thenReturn(guest);
        when(cartItems.findByCartId(30L)).thenReturn(List.of(item(1L, 30L, 5L, 2)));
        when(productSkus.findOne(5L)).thenReturn(sku(5L, 100L, 10, 1_000_000, null));
        when(cartItems.findByCartIdAndSkuId(20L, 5L)).thenReturn(null);
        when(cartItems.findByCartId(20L)).thenReturn(List.of(item(9L, 20L, 5L, 2)));
        when(products.findOne(100L)).thenReturn(product(100L, 1L));

        CartSyncResponseDto res = service.merge(USER_ID, "g-1");

        assertTrue(res.isSuccess());
        assertEquals(1, res.getItems().size());
        ArgumentCaptor<CartItemEntity> created = ArgumentCaptor.forClass(CartItemEntity.class);
        verify(cartItems).create(created.capture());
        assertEquals(20L, created.getValue().getCartId());
        assertEquals(2, created.getValue().getQuantity());
        verify(cartItems).deleteByCartId(30L);
        verify(carts).deleteCart(30L);
    }

    @Test
    void mergeWithoutGuestIdJustReturnsUserCart() {
        when(carts.findByUserId(USER_ID)).thenReturn(userCart(20L));
        when(cartItems.findByCartId(20L)).thenReturn(List.of());

        CartSyncResponseDto res = service.merge(USER_ID, null);

        assertTrue(res.isSuccess());
        verify(carts, never()).findByGuestId(anyString());
        verify(carts, never()).deleteCart(any(Long.class));
    }

    // ------------------------------------------------------------------ calculateTotal

    @Test
    void calculateTotalSumsListPriceAndReportsSaleAsDiscount() {
        when(productSkus.findOne(1L)).thenReturn(sku(1L, 100L, 10, 1_000_000, 900_000L));
        when(productSkus.findOne(2L)).thenReturn(sku(2L, 101L, 10, 500_000, null));
        CartTotalRequestDto req = new CartTotalRequestDto(List.of(
                new CartTotalItemRequestDto("1", 2, null),
                new CartTotalItemRequestDto("2", 1, null)));

        CartTotalResponseDto res = service.calculateTotal(req);

        assertEquals(2_500_000L, res.getSubTotal());
        assertEquals(200_000L, res.getDiscountTotal());
        assertEquals(1, res.getDiscounts().size());
        assertEquals(0L, res.getShippingFee());
        assertEquals(2_300_000L, res.getTotalAmount());
    }

    @Test
    void calculateTotalAppliesProductPromotionOnTopOfSalePrice() {
        when(productSkus.findOne(1L)).thenReturn(sku(1L, 100L, 10, 1_000_000, 900_000L));
        when(promotionPricing.unitDiscount(100L, BigDecimal.valueOf(900_000L))).thenReturn(BigDecimal.valueOf(90_000L));
        CartTotalRequestDto req = new CartTotalRequestDto(List.of(new CartTotalItemRequestDto("1", 2, null)));

        CartTotalResponseDto res = service.calculateTotal(req);

        assertEquals(2_000_000L, res.getSubTotal());
        assertEquals(380_000L, res.getDiscountTotal()); // 2*100k (sale) + 2*90k (khuyến mãi)
        assertEquals(2, res.getDiscounts().size());
        assertEquals(1_620_000L, res.getTotalAmount());
    }

    @Test
    void calculateTotalUnknownSkuIs404() {
        when(productSkus.findOne(99L)).thenReturn(null);
        CartTotalRequestDto req = new CartTotalRequestDto(List.of(new CartTotalItemRequestDto("99", 1, null)));

        assertThrows(ResourceNotFoundException.class, () -> service.calculateTotal(req));
    }

    // ------------------------------------------------------------------ recommend

    @Test
    void recommendationPutsSameCategoryFirstAndSkipsProductsAlreadyInCart() {
        when(carts.findByUserId(USER_ID)).thenReturn(userCart(20L));
        when(cartItems.findByCartId(20L)).thenReturn(List.of(item(1L, 20L, 5L, 1)));
        when(productSkus.findOne(5L)).thenReturn(sku(5L, 100L, 10, 1_000_000, null));
        when(products.findOne(100L)).thenReturn(product(100L, 1L));
        when(products.searchPublished(eq(""), eq(""), eq(""), eq(""), any(BigDecimal.class), any(BigDecimal.class)))
                .thenReturn(List.of(product(100L, 1L), product(101L, 2L), product(102L, 1L)));
        when(productSkus.findByProductId(102L)).thenReturn(List.of(sku(52L, 102L, 3, 700_000, null)));
        when(productSkus.findByProductId(101L)).thenReturn(List.of(sku(51L, 101L, 3, 800_000, null)));

        CartRecommendationResponseDto res = service.recommend(USER_ID, null, 1, 20);

        assertEquals(2, res.getTotal());
        assertEquals(2, res.getList().size());
        assertEquals("52", res.getList().get(0).getId());
        assertEquals("102", res.getList().get(0).getProductId());
        assertEquals("700000", res.getList().get(0).getSellingPriceAfterTaxMinor());
        assertEquals("51", res.getList().get(1).getId());
        assertFalse(res.getPagination().isHasNextPage());
        assertEquals(Boolean.FALSE, res.getPagination().getNextPage());
    }
}
