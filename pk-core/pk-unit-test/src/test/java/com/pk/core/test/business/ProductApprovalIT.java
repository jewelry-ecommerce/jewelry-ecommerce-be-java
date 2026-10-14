package com.pk.core.test.business;

import com.fasterxml.jackson.databind.JsonNode;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** FE v5 (PIM): gửi duyệt -> khoá chờ duyệt -> huỷ / từ chối / duyệt (publish). Mặc định publish trực tiếp vẫn dùng được. */
class ProductApprovalIT extends IntegrationTestBase {

    private static final String V1 = UrlConstant.Common.API + UrlConstant.Common.VERSION;
    private static final String PRODUCTS = V1 + UrlAdminConstant.Catalog.PRODUCTS;
    private static final String APPROVALS = V1 + UrlAdminConstant.Catalog.PRODUCT_APPROVALS;

    @Autowired CategoryRepo categories;
    @Autowired ProductRepo products;
    @Autowired ProductSkuRepo productSkus;

    private ProductEntity draftProduct(boolean withSku) {
        String t = UUID.randomUUID().toString().substring(0, 8);
        CategoryEntity category = new CategoryEntity("DM " + t, "dm-" + t, null);
        categories.create(category);
        ProductEntity product = new ProductEntity(category.getId(), "P-" + t, "SP " + t, "sp-" + t);
        product.setBasePrice(BigDecimal.valueOf(1_000_000));
        products.create(product);
        if (withSku) {
            ProductSkuEntity sku = new ProductSkuEntity("SKU-" + t, "M", BigDecimal.valueOf(1_000_000), 10);
            sku.setProductId(product.getId());
            sku.setName("Nhẫn " + t);
            sku.setDefault(true);
            productSkus.create(sku);
        }
        return product;
    }

    private JsonNode submit(long productId, String auth) throws Exception {
        return body(mvc.perform(post(PRODUCTS + "/" + productId + "/submit").header("Authorization", auth))
                .andExpect(status().isOk()));
    }

    private JsonNode review(Map<String, Object> req, String auth, int expectedStatus) throws Exception {
        return body(mvc.perform(jsonRequest(post(APPROVALS + "/review"), req).header("Authorization", auth))
                .andExpect(status().is(expectedStatus)));
    }

    @Test
    void submitLocksProductAndCancelUnlocksIt() throws Exception {
        String auth = bearer(adminAccessToken());
        ProductEntity p = draftProduct(true);

        JsonNode sent = submit(p.getId(), auth);
        assertTrue(sent.get("hasPendingApproval").asBoolean());
        assertEquals("PENDING", sent.get("approval").get("status").asText());
        assertEquals("CREATE_PRODUCT", sent.get("approval").get("action").asText());
        String requestId = sent.get("approvalRequestId").asText();

        // Khoá: không publish trực tiếp, không archive, không gửi lần hai.
        mvc.perform(patch(PRODUCTS + "/" + p.getId() + "/publish").header("Authorization", auth))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.errors[0].code").value("PRODUCT_PENDING_APPROVAL"));
        mvc.perform(patch(PRODUCTS + "/" + p.getId() + "/archive").header("Authorization", auth))
                .andExpect(status().isConflict());
        mvc.perform(post(PRODUCTS + "/" + p.getId() + "/submit").header("Authorization", auth))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.errors[0].code").value("PRODUCT_PENDING_APPROVAL"));

        // Chi tiết có bản chờ duyệt, danh sách + counts phản ánh.
        mvc.perform(get(PRODUCTS + "/" + p.getId()).header("Authorization", auth)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hasPendingApproval").value(true))
                .andExpect(jsonPath("$.data.pendingApproval.actionType").value("CREATE_PRODUCT"))
                .andExpect(jsonPath("$.data.pendingApproval.afterSnapshot.code").value(p.getCode()));
        mvc.perform(get(PRODUCTS).param("hasPendingApproval", "true").param("search", p.getName())
                        .header("Authorization", auth)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1));
        JsonNode counts = body(mvc.perform(get(PRODUCTS + "/counts").header("Authorization", auth)).andExpect(status().isOk()));
        assertTrue(counts.get("hasPendingApproval").asInt() >= 1);

        JsonNode cancelled = body(mvc.perform(post(APPROVALS + "/" + requestId + "/cancel").header("Authorization", auth))
                .andExpect(status().isOk()));
        assertFalse(cancelled.get("hasPendingApproval").asBoolean());
        assertEquals("DRAFT", cancelled.get("productStatus").asText());
        mvc.perform(post(APPROVALS + "/" + requestId + "/cancel").header("Authorization", auth))
                .andExpect(status().isConflict());
        // Hết khoá.
        mvc.perform(patch(PRODUCTS + "/" + p.getId() + "/unpublish").header("Authorization", auth)).andExpect(status().isOk());
    }

    @Test
    void rejectNeedsReasonAndKeepsProductDraft() throws Exception {
        String auth = bearer(adminAccessToken());
        ProductEntity p = draftProduct(true);
        String requestId = submit(p.getId(), auth).get("approvalRequestId").asText();

        review(Map.of("items", List.of(Map.of("productId", p.getId(), "approvalRequestId", Long.parseLong(requestId))),
                "decision", "REJECT"), auth, 400);
        review(Map.of("items", List.of(Map.of("productId", p.getId(), "approvalRequestId", Long.parseLong(requestId))),
                "decision", "MAYBE"), auth, 400);
        JsonNode done = review(Map.of("items", List.of(Map.of("productId", p.getId(),
                "approvalRequestId", Long.parseLong(requestId))), "decision", "REJECT", "reason", "Thiếu ảnh"), auth, 200);
        assertEquals(1, done.get("processed").asInt());

        mvc.perform(get(PRODUCTS + "/" + p.getId()).header("Authorization", auth)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.hasPendingApproval").value(false))
                .andExpect(jsonPath("$.data.approval.status").value("REJECTED"))
                .andExpect(jsonPath("$.data.approval.rejectedReason").value("Thiếu ảnh"));
        mvc.perform(get(APPROVALS).param("status", "REJECTED").param("search", p.getCode()).header("Authorization", auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].productCode").value(p.getCode()));
        // Duyệt lại yêu cầu đã xử lý -> 409.
        review(Map.of("items", List.of(Map.of("productId", p.getId(), "approvalRequestId", Long.parseLong(requestId))),
                "decision", "APPROVE"), auth, 409);
    }

    @Test
    void approvePublishesTheProductAtomically() throws Exception {
        String auth = bearer(adminAccessToken());
        ProductEntity ok = draftProduct(true);
        ProductEntity other = draftProduct(true);
        long okReq = submit(ok.getId(), auth).get("approvalRequestId").asLong();
        long otherReq = submit(other.getId(), auth).get("approvalRequestId").asLong();

        // Một mục sai (productId không khớp yêu cầu) -> cả lô không đổi gì.
        review(Map.of("items", List.of(Map.of("productId", ok.getId(), "approvalRequestId", okReq),
                Map.of("productId", ok.getId(), "approvalRequestId", otherReq)), "decision", "APPROVE"), auth, 400);
        mvc.perform(get(PRODUCTS + "/" + ok.getId()).header("Authorization", auth))
                .andExpect(jsonPath("$.data.status").value("DRAFT"));

        JsonNode done = review(Map.of("items", List.of(Map.of("productId", ok.getId(), "approvalRequestId", okReq),
                Map.of("productId", other.getId(), "approvalRequestId", otherReq)), "decision", "APPROVE"), auth, 200);
        assertEquals(2, done.get("processed").asInt());
        for (ProductEntity p : List.of(ok, other)) {
            mvc.perform(get(PRODUCTS + "/" + p.getId()).header("Authorization", auth))
                    .andExpect(jsonPath("$.data.status").value("PUBLISHED"))
                    .andExpect(jsonPath("$.data.approval.status").value("APPROVED"));
        }
        mvc.perform(get(V1 + UrlConstant.Product.BASE + "/" + ok.getSlug())).andExpect(status().isOk());
        // Sản phẩm đã PUBLISHED thì không gửi duyệt nữa.
        mvc.perform(post(PRODUCTS + "/" + ok.getId() + "/submit").header("Authorization", auth))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.errors[0].code").value("INVALID_STATUS_TRANSITION"));
    }

    @Test
    void submitChecksPublishableOnTheSpot() throws Exception {
        String auth = bearer(adminAccessToken());
        ProductEntity noSku = draftProduct(false);
        mvc.perform(post(PRODUCTS + "/" + noSku.getId() + "/submit").header("Authorization", auth))
                .andExpect(status().isBadRequest());
        mvc.perform(post(PRODUCTS + "/999999999/submit").header("Authorization", auth)).andExpect(status().isNotFound());
    }
}
