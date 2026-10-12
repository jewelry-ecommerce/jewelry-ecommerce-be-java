package com.pk.core.business.service.impl;

import lombok.RequiredArgsConstructor;

import com.pk.core.business.repository.CartItemRepo;
import com.pk.core.business.repository.CartRepo;
import com.pk.core.business.repository.ProductRepo;
import com.pk.core.business.repository.ProductSkuRepo;
import com.pk.core.business.service.CartService;
import com.pk.core.business.service.PromotionPricingService;
import com.pk.core.business.service.PreOrderService;
import com.pk.core.common.exception.BusinessException;
import com.pk.core.common.exception.ErrorCode;
import com.pk.core.common.exception.ResourceNotFoundException;
import com.pk.core.common.web.PageResponse;
import com.pk.core.model.dto.request.CartSyncItemRequestDto;
import com.pk.core.model.dto.request.CartItemQuantityRequestDto;
import com.pk.core.model.dto.request.CartSyncRequestDto;
import com.pk.core.model.dto.request.CartTotalItemRequestDto;
import com.pk.core.model.dto.request.CartTotalRequestDto;
import com.pk.core.model.dto.response.CartDiscountResponseDto;
import com.pk.core.model.dto.response.CartLineResponseDto;
import com.pk.core.model.dto.response.CartProductResponseDto;
import com.pk.core.model.dto.response.CartRecommendationItemResponseDto;
import com.pk.core.model.dto.response.CartRecommendationResponseDto;
import com.pk.core.model.dto.response.CartSyncResponseDto;
import com.pk.core.model.dto.response.CartTotalResponseDto;
import com.pk.core.model.dto.response.PaginationResponseDto;
import com.pk.core.model.entity.CartEntity;
import com.pk.core.model.entity.CartItemEntity;
import com.pk.core.model.entity.ProductEntity;
import com.pk.core.model.entity.ProductSkuEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CartServiceImpl implements CartService {

    private static final BigDecimal NO_MAX_PRICE = new BigDecimal("999999999999");

    private final CartRepo carts;
    private final CartItemRepo cartItems;
    private final ProductRepo products;
    private final ProductSkuRepo productSkus;
    private final PromotionPricingService promotionPricing;
    private final PreOrderService preOrder;

    /** Kết quả gộp dòng vào giỏ: giữ lý do của dòng bị ảnh hưởng ĐẦU TIÊN (đủ cho FE hiển thị 1 thông báo). */
    private static class Outcome {
        private String reason;
        private String message;

        void note(String reason, String message) {
            if (this.reason == null) {
                this.reason = reason;
                this.message = message;
            }
        }
    }

    @Transactional(readOnly = true)
    @Override
    public CartSyncResponseDto get(Long userId, String guestId) {
        CartEntity cart = findCart(userId, guestId);
        if (cart == null) {
            return new CartSyncResponseDto(true, null, new ArrayList<>(), null, null);
        }
        return respond(cart, new Outcome());
    }

    @Transactional
    @Override
    public CartSyncResponseDto addOrSync(Long userId, String guestId, CartSyncRequestDto req) {
        // Kiểm tra toàn bộ dòng TRƯỚC khi ghi để lỗi 400 không để lại giỏ ghi dở.
        Map<Long, Integer> qtyBySku = new LinkedHashMap<>();
        for (CartSyncItemRequestDto line : req.getItems()) {
            if (line.getSetId() != null && !line.getSetId().isBlank()) {
                throw BusinessException.badRequest(ErrorCode.CART_ITEM_INVALID,
                        "Dòng giỏ hàng không hợp lệ: chưa hỗ trợ bộ sản phẩm (setId)", "setId");
            }
            qtyBySku.merge(parseSkuId(line.getVariationId()), line.getQuantity(), Integer::sum);
        }

        CartEntity cart = findOrCreateCart(userId, guestId);
        if (req.isClearAll()) {
            cartItems.deleteByCartId(cart.getId());
        }

        Outcome outcome = new Outcome();
        for (Map.Entry<Long, Integer> line : qtyBySku.entrySet()) {
            ProductSkuEntity sku = productSkus.findOne(line.getKey());
            if (!sellable(sku)) {
                outcome.note("NOT_FOUND", "Sản phẩm không còn tồn tại");
                continue;
            }
            addCapped(cart.getId(), sku, line.getValue(), outcome);
        }
        return respond(cart, outcome);
    }

    @Transactional
    @Override
    public CartSyncResponseDto setQuantity(Long userId, String guestId, CartItemQuantityRequestDto req) {
        Long skuId = parseSkuId(req.getVariationId());
        if (req.getQuantity() == 0) {
            return removeItem(userId, guestId, req.getVariationId());
        }
        CartEntity cart = findOrCreateCart(userId, guestId);
        Outcome outcome = new Outcome();
        ProductSkuEntity sku = productSkus.findOne(skuId);
        if (!sellable(sku)) {
            outcome.note("NOT_FOUND", "Sản phẩm không còn tồn tại");
        } else {
            addCapped(cart.getId(), sku, req.getQuantity(), outcome, true);
        }
        return respond(cart, outcome);
    }

    @Transactional
    @Override
    public CartSyncResponseDto removeItem(Long userId, String guestId, String variationId) {
        Long skuId = parseSkuId(variationId);
        CartEntity cart = findCart(userId, guestId);
        if (cart == null) {
            return new CartSyncResponseDto(true, null, new ArrayList<>(), null, null);
        }
        cartItems.deleteByCartIdAndSkuId(cart.getId(), skuId);
        return respond(cart, new Outcome());
    }

    @Transactional
    @Override
    public CartSyncResponseDto merge(Long userId, String guestId) {
        CartEntity userCart = findOrCreateCart(userId, null);
        Outcome outcome = new Outcome();

        CartEntity guestCart = (guestId == null || guestId.isBlank()) ? null : carts.findByGuestId(guestId.trim());
        if (guestCart != null) {
            for (CartItemEntity line : cartItems.findByCartId(guestCart.getId())) {
                ProductSkuEntity sku = productSkus.findOne(line.getSkuId());
                if (!sellable(sku)) {
                    outcome.note("NOT_FOUND", "Sản phẩm không còn tồn tại");
                    continue;
                }
                addCapped(userCart.getId(), sku, line.getQuantity(), outcome);
            }
            cartItems.deleteByCartId(guestCart.getId());
            carts.deleteCart(guestCart.getId());
        }
        return respond(userCart, outcome);
    }

    @Transactional(readOnly = true)
    @Override
    public CartTotalResponseDto calculateTotal(CartTotalRequestDto req) {
        Map<Long, Integer> qtyBySku = new LinkedHashMap<>();
        for (CartTotalItemRequestDto line : req.getItems()) {
            qtyBySku.merge(parseSkuId(line.getVariationId()), line.getQuantity(), Integer::sum);
        }

        BigDecimal subTotal = BigDecimal.ZERO;
        BigDecimal productDiscount = BigDecimal.ZERO;
        BigDecimal promotionDiscount = BigDecimal.ZERO;
        for (Map.Entry<Long, Integer> line : qtyBySku.entrySet()) {
            ProductSkuEntity sku = productSkus.findOne(line.getKey());
            if (!sellable(sku)) {
                throw new ResourceNotFoundException("Sản phẩm", "ProductSku", line.getKey());
            }
            BigDecimal qty = BigDecimal.valueOf(line.getValue());
            subTotal = subTotal.add(sku.getListPrice().multiply(qty));
            productDiscount = productDiscount.add(sku.getListPrice().subtract(sku.effectivePrice()).multiply(qty));
            promotionDiscount = promotionDiscount.add(
                    promotionPricing.unitDiscount(sku.getProductId(), sku.effectivePrice()).multiply(qty));
        }

        List<CartDiscountResponseDto> discounts = new ArrayList<>();
        if (productDiscount.signum() > 0) {
            discounts.add(new CartDiscountResponseDto("Giảm giá sản phẩm", productDiscount.longValue()));
        }
        if (promotionDiscount.signum() > 0) {
            discounts.add(new CartDiscountResponseDto("Khuyến mãi", promotionDiscount.longValue()));
            productDiscount = productDiscount.add(promotionDiscount);
        }
        // CHƯA nối voucher/điểm thưởng/phí ship vào giỏ - giống CheckoutServiceImpl (ship = 0, điểm = 0).
        long shippingFee = 0;
        long total = subTotal.subtract(productDiscount).longValue() + shippingFee;
        return new CartTotalResponseDto(subTotal.longValue(), productDiscount.longValue(), discounts,
                shippingFee, total, 0);
    }

    @Transactional(readOnly = true)
    @Override
    public CartRecommendationResponseDto recommend(Long userId, String guestId, int page, int take) {
        Set<Long> inCartProductIds = new HashSet<>();
        Set<Long> inCartCategoryIds = new HashSet<>();
        CartEntity cart = findCart(userId, guestId);
        if (cart != null) {
            for (CartItemEntity line : cartItems.findByCartId(cart.getId())) {
                ProductSkuEntity sku = productSkus.findOne(line.getSkuId());
                ProductEntity product = sku == null ? null : products.findOne(sku.getProductId());
                if (product != null) {
                    inCartProductIds.add(product.getId());
                    inCartCategoryIds.add(product.getCategoryId());
                }
            }
        }

        List<ProductEntity> candidates = new ArrayList<>(
                products.searchPublished("", "", "", "", BigDecimal.ZERO, NO_MAX_PRICE));
        candidates.removeIf(p -> inCartProductIds.contains(p.getId()));
        // Cùng danh mục với hàng trong giỏ lên trước, sau đó mới nhất (id lớn) trước.
        candidates.sort(Comparator
                .comparing((ProductEntity p) -> inCartCategoryIds.contains(p.getCategoryId()) ? 0 : 1)
                .thenComparing(ProductEntity::getId, Comparator.reverseOrder()));

        PageResponse<ProductEntity> paged = PageResponse.paginate(candidates, page, take);
        List<CartRecommendationItemResponseDto> list = new ArrayList<>();
        for (ProductEntity p : paged.content()) {
            ProductSkuEntity sku = representativeSku(p.getId());
            if (sku == null) {
                continue; // sản phẩm chưa có SKU PUBLISHED thì không gợi ý được (hiếm)
            }
            list.add(new CartRecommendationItemResponseDto(String.valueOf(sku.getId()), p.getSlug(), p.getName(),
                    String.valueOf(p.getId()), String.valueOf(sku.effectivePrice().longValue()),
                    sku.available(), sku.getStatus(),
                    sku.getListPrice() != null && sku.getListPrice().compareTo(sku.effectivePrice()) > 0
                            ? String.valueOf(sku.getListPrice().longValue()) : "0",
                    p.getThumbnailUrl()));
        }

        boolean hasNext = paged.page() < paged.totalPages();
        boolean hasPrevious = paged.page() > 1;
        PaginationResponseDto pagination = new PaginationResponseDto(paged.totalElements(), paged.page(),
                hasNext ? (Object) (paged.page() + 1) : (Object) Boolean.FALSE,
                hasPrevious ? (Object) (paged.page() - 1) : (Object) Boolean.FALSE,
                hasNext, hasPrevious, paged.totalPages());
        return new CartRecommendationResponseDto(paged.totalElements(), list, pagination);
    }

    // ------------------------------------------------------------------ helpers

    /** Cộng `qty` của SKU vào giỏ, chặn ở tồn kho còn bán được; ghi lý do vào outcome nếu bị chặn/giảm. */
    private void addCapped(Long cartId, ProductSkuEntity sku, int qty, Outcome outcome) {
        addCapped(cartId, sku, qty, outcome, false);
    }

    /** absolute=true: qty là số lượng CUỐI của dòng (đặt lại); false: cộng dồn vào số đang có. */
    private void addCapped(Long cartId, ProductSkuEntity sku, int qty, Outcome outcome, boolean absolute) {
        int available = sku.available();
        // Đặt trước đang bật: SKU hết hàng vẫn thêm được vào giỏ (không chặn số lượng).
        boolean preOrderLine = available <= 0 && preOrder.appliesTo(sku);
        if (available <= 0 && !preOrderLine) {
            outcome.note("OUT_OF_STOCK", "Sản phẩm \"" + sku.getName() + "\" đã hết hàng");
            return;
        }
        CartItemEntity existing = cartItems.findByCartIdAndSkuId(cartId, sku.getId());
        int wanted = absolute ? qty : (existing == null ? 0 : existing.getQuantity()) + qty;
        int finalQty = wanted;
        if (!preOrderLine && wanted > available) {
            finalQty = available;
            outcome.note("INSUFFICIENT_STOCK",
                    "Sản phẩm \"" + sku.getName() + "\" chỉ còn " + available + " trong kho");
        }
        if (existing == null) {
            cartItems.create(new CartItemEntity(cartId, sku.getId(), finalQty));
        } else if (existing.getQuantity() != finalQty) {
            existing.setQuantity(finalQty);
            cartItems.update(existing);
        }
    }

    private CartSyncResponseDto respond(CartEntity cart, Outcome outcome) {
        List<CartLineResponseDto> lines = new ArrayList<>();
        for (CartItemEntity item : cartItems.findByCartId(cart.getId())) {
            lines.add(toLine(item));
        }
        return new CartSyncResponseDto(outcome.reason == null, cart.getGuestId(), lines,
                outcome.reason, outcome.message);
    }

    /** SKU bán được: SKU PUBLISHED và sản phẩm cha còn tồn tại, chưa xóa, đang PUBLISHED. */
    private boolean sellable(ProductSkuEntity sku) {
        if (sku == null || !ProductSkuEntity.PUBLISHED.equals(sku.getStatus())) {
            return false;
        }
        ProductEntity product = products.findOne(sku.getProductId());
        return product != null && product.isSellable();
    }

    private CartLineResponseDto toLine(CartItemEntity item) {
        ProductSkuEntity sku = productSkus.findOne(item.getSkuId());
        if (sku == null) {
            return new CartLineResponseDto(String.valueOf(item.getId()), false, null, null, null, null, null,
                    String.valueOf(item.getSkuId()), item.getQuantity(), null, null, null, null, null);
        }
        ProductEntity product = products.findOne(sku.getProductId());
        CartProductResponseDto productDto = product == null ? null : new CartProductResponseDto(
                String.valueOf(product.getId()), product.getName(), product.getSlug(), product.getThumbnailUrl());
        return new CartLineResponseDto(String.valueOf(item.getId()), true,
                product != null ? product.getName() : sku.getName(),
                product != null ? product.getSlug() : null,
                sku.getSkuCode(), sellable(sku) ? sku.getStatus() : "UNPUBLISHED", String.valueOf(sku.getProductId()),
                String.valueOf(sku.getId()), item.getQuantity(),
                sku.getListPrice().longValue(), sku.effectivePrice().longValue(),
                sku.available(), sku.stockStatus(), productDto);
    }

    /** SKU đại diện của sản phẩm: SKU PUBLISHED mặc định, không có thì SKU PUBLISHED đầu tiên. */
    private ProductSkuEntity representativeSku(Long productId) {
        ProductSkuEntity first = null;
        for (ProductSkuEntity sku : productSkus.findByProductId(productId)) {
            if (!ProductSkuEntity.PUBLISHED.equals(sku.getStatus())) {
                continue;
            }
            if (sku.isDefault()) {
                return sku;
            }
            if (first == null) {
                first = sku;
            }
        }
        return first;
    }

    private CartEntity findCart(Long userId, String guestId) {
        if (userId != null) {
            return carts.findByUserId(userId);
        }
        if (guestId != null && !guestId.isBlank()) {
            return carts.findByGuestId(guestId.trim());
        }
        return null;
    }

    /** Có giỏ rồi thì dùng; chưa có thì tạo. Khách vãng lai gửi guestId lạ/thiếu -> server cấp UUID mới (không cho
     * client tự chọn guestId). */
    private CartEntity findOrCreateCart(Long userId, String guestId) {
        CartEntity cart = findCart(userId, guestId);
        if (cart != null) {
            return cart;
        }
        CartEntity created = userId != null ? CartEntity.forUser(userId) : CartEntity.forGuest(UUID.randomUUID().toString());
        carts.create(created);
        return created;
    }

    private static Long parseSkuId(String variationId) {
        if (variationId == null || variationId.isBlank()) {
            throw BusinessException.badRequest(ErrorCode.CART_ITEM_INVALID,
                    "Dòng giỏ hàng không hợp lệ: thiếu variationId", "variationId");
        }
        try {
            return Long.valueOf(variationId.trim());
        } catch (NumberFormatException ex) {
            throw BusinessException.badRequest(ErrorCode.CART_ITEM_INVALID,
                    "Dòng giỏ hàng không hợp lệ: variationId phải là số", "variationId");
        }
    }
}
