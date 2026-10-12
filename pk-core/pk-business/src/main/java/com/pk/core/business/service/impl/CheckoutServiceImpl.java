package com.pk.core.business.service.impl;

import lombok.RequiredArgsConstructor;

import com.pk.core.business.repository.CustomerAddressRepo;
import com.pk.core.business.repository.ProductRepo;
import com.pk.core.business.repository.ProductSkuRepo;
import com.pk.core.business.service.CheckoutService;
import com.pk.core.business.service.PromotionPricingService;
import com.pk.core.business.service.PreOrderService;
import com.pk.core.business.service.VoucherService;
import com.pk.core.common.exception.BusinessException;
import com.pk.core.common.exception.ErrorCode;
import com.pk.core.common.exception.ResourceNotFoundException;
import com.pk.core.model.dto.request.OrderItemRequestDto;
import com.pk.core.model.dto.request.QuoteRequestDto;
import com.pk.core.model.dto.response.OrderItemResponseDto;
import com.pk.core.model.dto.response.QuoteResponseDto;
import com.pk.core.model.dto.response.QuoteSummaryResponseDto;
import com.pk.core.model.entity.CustomerAddressEntity;
import com.pk.core.model.entity.ProductEntity;
import com.pk.core.model.entity.ProductSkuEntity;
import com.pk.core.model.entity.VoucherEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CheckoutServiceImpl implements CheckoutService {

    private final CustomerAddressRepo addresses;
    private final ProductRepo products;
    private final ProductSkuRepo productSkus;
    private final VoucherService voucherService;
    private final PromotionPricingService promotionPricing;
    private final PreOrderService preOrder;

    @Transactional(readOnly = true)
    @Override
    public QuoteResponseDto quote(Long userId, QuoteRequestDto req) {
        CustomerAddressEntity address = addresses.findByIdAndUserId(req.getAddressId(), userId);
        if (address == null) {
            throw new ResourceNotFoundException("Địa chỉ giao hàng", "Address", req.getAddressId());
        }
        return quoteItems(req.getItems(), req.getVoucherCode(), req.getShippingMethod());
    }

    @Transactional(readOnly = true)
    @Override
    public QuoteResponseDto quoteGuest(com.pk.core.model.dto.request.GuestOrderRequestDto req) {
        return quoteItems(req.getItems(), req.getVoucherCode(), req.getShippingMethod());
    }

    private QuoteResponseDto quoteItems(List<OrderItemRequestDto> requestItems, String voucherCode, String shippingMethod) {
        Map<Long, Integer> qtyBySku = new LinkedHashMap<>();
        for (OrderItemRequestDto line : requestItems) {
            qtyBySku.merge(line.getSkuId(), line.getQuantity(), Integer::sum);
        }

        List<OrderItemResponseDto> items = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal productDiscount = BigDecimal.ZERO;
        int itemCount = 0;
        for (Map.Entry<Long, Integer> line : qtyBySku.entrySet()) {
            Long skuId = line.getKey();
            int qty = line.getValue();

            ProductSkuEntity sku = productSkus.findOne(skuId);
            if (!sellable(sku)) {
                throw new ResourceNotFoundException("Sản phẩm", "ProductSku", skuId);
            }
            // Đặt trước đang bật thì SKU HẾT HÀNG vẫn báo giá được (đơn đặt trước).
            boolean preOrderLine = sku.available() <= 0 && preOrder.appliesTo(sku);
            if (sku.available() < qty && !preOrderLine) {
                throw BusinessException.badRequest(ErrorCode.INSUFFICIENT_STOCK,
                        "Sản phẩm \"" + sku.getName() + "\" không đủ hàng, chỉ còn " + sku.available(),
                        sku.available());
            }

            ProductEntity product = products.findOne(sku.getProductId());
            BigDecimal unitPrice = sku.effectivePrice();
            items.add(new OrderItemResponseDto(null, sku.getProductId(), skuId,
                    product != null ? product.getName() : sku.getName(),
                    product != null ? product.getThumbnailUrl() : null, qty, unitPrice,
                    unitPrice.multiply(BigDecimal.valueOf(qty))));
            subtotal = subtotal.add(unitPrice.multiply(BigDecimal.valueOf(qty)));
            productDiscount = productDiscount.add(
                    promotionPricing.unitDiscount(sku.getProductId(), unitPrice).multiply(BigDecimal.valueOf(qty)));
            itemCount += qty;
        }

        BigDecimal voucherDiscount = BigDecimal.ZERO;
        if (voucherCode != null && !voucherCode.isBlank()) {
            // Voucher tính trên phần còn lại sau khi đã trừ khuyến mãi sản phẩm.
            BigDecimal payable = subtotal.subtract(productDiscount);
            VoucherEntity voucher = voucherService.validate(voucherCode, payable);
            voucherDiscount = voucher.computeDiscount(payable);
        }

        // CHƯA có bảng phí ship theo phương thức/khu vực - giống OrderServiceImpl.create, để 0.
        BigDecimal shippingFee = BigDecimal.ZERO;
        BigDecimal grandTotal = subtotal.subtract(productDiscount).subtract(voucherDiscount).add(shippingFee);

        QuoteSummaryResponseDto summary = new QuoteSummaryResponseDto(subtotal, productDiscount, voucherDiscount,
                shippingFee, grandTotal, itemCount);

        return new QuoteResponseDto(items, summary, estimatedDeliveryDate(shippingMethod));
    }

    /** CHƯA có logistics thật (thời gian giao theo khu vực/đơn vị vận chuyển) - ước lượng đơn giản:
     * EXPRESS +2 ngày, còn lại +5 ngày kể từ hôm nay. Chỉ mang tính tham khảo cho FE hiển thị. */
    private static Date estimatedDeliveryDate(String shippingMethod) {
        Calendar cal = Calendar.getInstance();
        int days = "EXPRESS".equalsIgnoreCase(shippingMethod) ? 2 : 5;
        cal.add(Calendar.DAY_OF_MONTH, days);
        return cal.getTime();
    }

    /** SKU bán được: SKU PUBLISHED và sản phẩm cha còn tồn tại, chưa xóa, đang PUBLISHED. */
    private boolean sellable(ProductSkuEntity sku) {
        if (sku == null || !ProductSkuEntity.PUBLISHED.equals(sku.getStatus())) {
            return false;
        }
        ProductEntity product = products.findOne(sku.getProductId());
        return product != null && product.isSellable();
    }
}
