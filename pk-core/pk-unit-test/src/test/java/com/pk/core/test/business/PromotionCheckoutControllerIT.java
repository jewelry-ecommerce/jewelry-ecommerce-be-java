package com.pk.core.test.business;

import com.pk.core.business.repository.CategoryRepo;
import com.pk.core.business.repository.ProductRepo;
import com.pk.core.business.repository.ProductSkuRepo;
import com.pk.core.model.constant.UrlConstant;
import com.pk.core.model.constant.admin.UrlAdminConstant;
import com.pk.core.model.entity.CategoryEntity;
import com.pk.core.model.entity.ProductEntity;
import com.pk.core.model.entity.ProductSkuEntity;
import com.pk.core.test.common.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Khuyến mãi sản phẩm được áp vào calculate-total của giỏ và báo giá checkout (productDiscount). */
class PromotionCheckoutControllerIT extends IntegrationTestBase {

    @Autowired CategoryRepo categories;
    @Autowired ProductRepo products;
    @Autowired ProductSkuRepo productSkus;

    /** [productId, skuId] - SKU giá 1.000.000, tồn 10. */
    private long[] newProduct() {
        String tag = UUID.randomUUID().toString().substring(0, 8);
        CategoryEntity category = new CategoryEntity("DM " + tag, "dm-" + tag, null);
        categories.create(category);
        ProductEntity product = new ProductEntity(category.getId(), "P-" + tag, "SP " + tag, "sp-" + tag);
        product.setBasePrice(BigDecimal.valueOf(1_000_000));
        product.publish();
        products.create(product);
        ProductSkuEntity sku = new ProductSkuEntity("SKU-" + tag, "M", BigDecimal.valueOf(1_000_000), 10);
        sku.setProductId(product.getId());
        sku.setName("Nhẫn " + tag);
        sku.setStatus(ProductSkuEntity.PUBLISHED);
        sku.setDefault(true);
        productSkus.create(sku);
        return new long[] {product.getId(), sku.getId()};
    }

    private void createPromotion(String type, long value, Long max, long productId, String status) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("name", "KM " + UUID.randomUUID().toString().substring(0, 6));
        body.put("discountType", type);
        body.put("discountValue", value);
        if (max != null) {
            body.put("maxDiscountAmount", max);
        }
        body.put("startsAt", new Date(System.currentTimeMillis() - 3_600_000));
        body.put("endsAt", new Date(System.currentTimeMillis() + 86_400_000));
        body.put("status", status);
        body.put("productIds", List.of(productId));
        mvc.perform(jsonRequest(post(UrlConstant.Common.API + UrlConstant.Common.VERSION + UrlAdminConstant.Promotion.BASE), body)
                        .header("Authorization", bearer(adminAccessToken())))
                .andExpect(status().isCreated());
    }

    @Test
    void publishedPercentPromotionReducesCartTotal() throws Exception {
        long[] p = newProduct();
        createPromotion("PERCENT", 10, null, p[0], "PUBLISHED");

        postJson(UrlConstant.Common.API + UrlConstant.Common.VERSION + UrlConstant.Cart.BASE + "/calculate-total",
                Map.of("items", List.of(Map.of("variationId", String.valueOf(p[1]), "quantity", 2))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.subTotal").value(2_000_000))
                .andExpect(jsonPath("$.data.discountTotal").value(200_000))
                .andExpect(jsonPath("$.data.totalAmount").value(1_800_000));
    }

    @Test
    void draftPromotionIsIgnored() throws Exception {
        long[] p = newProduct();
        createPromotion("PERCENT", 50, null, p[0], "DRAFT");

        postJson(UrlConstant.Common.API + UrlConstant.Common.VERSION + UrlConstant.Cart.BASE + "/calculate-total",
                Map.of("items", List.of(Map.of("variationId", String.valueOf(p[1]), "quantity", 1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.discountTotal").value(0))
                .andExpect(jsonPath("$.data.totalAmount").value(1_000_000));
    }

    @Test
    void checkoutQuoteAndOrderCarryProductDiscount() throws Exception {
        long[] p = newProduct();
        createPromotion("FIXED", 150_000, null, p[0], "PUBLISHED");

        String token = customerAccessToken();
        String addressBody = json.writeValueAsString(Map.of("recipientName", "Nguyễn A", "phone", "0901234567",
                "province", "HCM", "district", "Q1", "ward", "P1", "addressLine", "1 Lê Lợi"));
        long addressId = json.readTree(mvc.perform(post(UrlConstant.Common.API + UrlConstant.Common.VERSION + UrlConstant.Me.ADDRESSES)
                        .header("Authorization", bearer(token))
                        .contentType("application/json").content(addressBody))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8))
                .path("data").path("id").asLong();

        Map<String, Object> req = Map.of("addressId", addressId, "shippingMethod", "STANDARD",
                "paymentMethod", "COD", "items", List.of(Map.of("skuId", p[1], "quantity", 2)));

        mvc.perform(jsonRequest(post(UrlConstant.Common.API + UrlConstant.Common.VERSION + UrlConstant.Checkout.QUOTE), req).header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.summary.productDiscount").value(300_000))
                .andExpect(jsonPath("$.data.summary.grandTotal").value(1_700_000));

        mvc.perform(jsonRequest(post(UrlConstant.Common.API + UrlConstant.Common.VERSION + UrlConstant.Order.BASE), req).header("Authorization", bearer(token)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.productDiscount").value(300_000))
                .andExpect(jsonPath("$.data.grandTotal").value(1_700_000));
    }
}
