package com.pk.core.test.business;

import com.fasterxml.jackson.databind.JsonNode;
import com.pk.core.business.repository.BannerPlacementRepo;
import com.pk.core.model.constant.UrlConstant;
import com.pk.core.model.constant.admin.UrlAdminConstant;
import com.pk.core.model.entity.BannerPlacementEntity;
import com.pk.core.test.common.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** FE v7: sửa đơn admin song song với huỷ/sửa khác; ORDER_MANAGER tra khách theo SĐT; xoá placement đang được CMS dùng. */
class OmsAdminHardeningIT extends IntegrationTestBase {

    private static final String V1 = UrlConstant.Common.API + UrlConstant.Common.VERSION;
    private static final String CATALOG = V1 + UrlAdminConstant.Catalog.PRODUCTS;
    private static final String ORDERS = V1 + UrlAdminConstant.Order.BASE;

    @Autowired BannerPlacementRepo placements;

    private String admin;
    private long productId;
    private long skuId;

    private void setUp() throws Exception {
        String t = UUID.randomUUID().toString().substring(0, 8);
        admin = bearer(adminAccessToken());
        long categoryId = body(mvc.perform(jsonRequest(post(V1 + UrlAdminConstant.Catalog.CATEGORIES),
                Map.of("name", "DM " + t, "slug", "dm-" + t, "sortOrder", 0)).header("Authorization", admin))
                .andExpect(status().isCreated())).get("id").asLong();
        productId = body(mvc.perform(jsonRequest(post(CATALOG), Map.of(
                "categoryId", categoryId, "code", "P-" + t, "name", "Nhẫn " + t, "slug", "nhan-" + t,
                "variants", List.of(Map.of("skuCode", "SKU-" + t, "listPrice", 1_000_000, "onHand", 20,
                        "isDefault", true, "status", "PUBLISHED")))).header("Authorization", admin))
                .andExpect(status().isCreated())).get("id").asLong();
        skuId = body(mvc.perform(get(CATALOG + "/" + productId + "/variants").header("Authorization", admin)))
                .get(0).get("id").asLong();
        mvc.perform(patch(CATALOG + "/" + productId + "/publish").header("Authorization", admin)).andExpect(status().isOk());
    }

    private int availableStock() throws Exception {
        return body(mvc.perform(get(CATALOG + "/" + productId + "/variants").header("Authorization", admin)))
                .get(0).get("availableStock").asInt();
    }

    private Map<String, Object> orderBody(int qty) {
        Map<String, Object> m = new HashMap<>();
        m.put("contextVersion", 2);
        m.put("receiverPhone", "0912345678");
        m.put("firstName", "Khách Test");
        m.put("provinceName", "Hồ Chí Minh");
        m.put("wardCode", 1);
        m.put("wardName", "Phường 1");
        m.put("addressLine", "1 Lê Lợi");
        m.put("shippingMethod", "STANDARD");
        m.put("paymentMethod", "COD");
        m.put("items", List.of(Map.of("variationId", String.valueOf(skuId), "quantity", qty)));
        return m;
    }

    private String createOrder(int qty) throws Exception {
        Map<String, Object> req = orderBody(qty);
        req.put("idempotencyKey", "idem-" + UUID.randomUUID());
        return body(mvc.perform(jsonRequest(post(ORDERS), req).header("Authorization", admin))
                .andExpect(status().isCreated())).get("code").asText();
    }

    private int statusOf(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder b) throws Exception {
        return mvc.perform(b.header("Authorization", admin)).andReturn().getResponse().getStatus();
    }

    @Test
    void parallelAdminUpdatesAreSerializedAndStockStaysConsistent() throws Exception {
        setUp();
        String code = createOrder(2);
        assertEquals(18, availableStock());
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch go = new CountDownLatch(1);
        Future<Integer> a = pool.submit(() -> {
            go.await();
            return statusOf(jsonRequest(put(ORDERS + "/" + code), orderBody(5)));
        });
        Future<Integer> b = pool.submit(() -> {
            go.await();
            return statusOf(jsonRequest(put(ORDERS + "/" + code), orderBody(7)));
        });
        go.countDown();
        int sa = a.get();
        int sb = b.get();
        pool.shutdown();
        assertTrue(sa == 200 && sb == 200, "hai lần sửa xếp hàng, đều thành công: " + sa + "," + sb);
        JsonNode o = body(mvc.perform(get(ORDERS + "/" + code).header("Authorization", admin)).andExpect(status().isOk()));
        assertEquals(1, o.get("items").size());
        int qty = o.get("items").get(0).get("quantity").asInt();
        assertTrue(qty == 5 || qty == 7, "số lượng cuối là của một trong hai lần sửa: " + qty);
        assertEquals(20 - qty, availableStock(), "tồn giữ chỗ khớp số lượng cuối, không nhả/giữ thừa");
    }

    @Test
    void updateVersusCancelLeavesConsistentState() throws Exception {
        setUp();
        String code = createOrder(3);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch go = new CountDownLatch(1);
        Future<Integer> upd = pool.submit(() -> {
            go.await();
            return statusOf(jsonRequest(put(ORDERS + "/" + code), orderBody(6)));
        });
        Future<Integer> can = pool.submit(() -> {
            go.await();
            return statusOf(jsonRequest(post(ORDERS + "/" + code + "/cancel"), Map.of("reason", "khách đổi ý")));
        });
        go.countDown();
        int su = upd.get();
        int sc = can.get();
        pool.shutdown();
        assertTrue(su == 200 || su == 409, "update: " + su);
        assertTrue(sc == 200 || sc == 400 || sc == 409, "cancel: " + sc);
        JsonNode o = body(mvc.perform(get(ORDERS + "/" + code).header("Authorization", admin)).andExpect(status().isOk()));
        if ("CANCELLED".equals(o.get("status").asText())) {
            assertEquals(20, availableStock(), "huỷ xong phải nhả hết tồn, kể cả khi sửa chen vào");
        } else {
            assertEquals(20 - o.get("items").get(0).get("quantity").asInt(), availableStock());
        }
        // sửa sau khi đã huỷ -> 409, không giữ lại tồn
        if ("CANCELLED".equals(o.get("status").asText())) {
            assertEquals(409, statusOf(jsonRequest(put(ORDERS + "/" + code), orderBody(2))));
            assertEquals(20, availableStock());
        }
    }

    @Test
    void orderManagerLooksUpCustomerByPhoneUnderOrdersPath() throws Exception {
        admin = bearer(adminAccessToken());
        String staffPhone = uniquePhone();
        mvc.perform(jsonRequest(post(V1 + UrlAdminConstant.Access.USERS), Map.of("fullName", "Nhân viên đơn", "phone", staffPhone,
                "password", PASSWORD, "roles", List.of("ORDER_MANAGER"))).header("Authorization", admin)).andExpect(status().isOk());
        String staff = bearer(body(postJson("/api/v1/auth/login", Map.of("phone", staffPhone, "password", PASSWORD))).get("accessToken").asText());
        String customerPhone = uniquePhone();
        registerCustomer(customerPhone);

        JsonNode found = body(mvc.perform(get(ORDERS + "/customer-audience/by-phone").param("phone", customerPhone)
                .header("Authorization", staff)).andExpect(status().isOk()));
        assertEquals(customerPhone, found.get("phone").asText());
        mvc.perform(get(ORDERS + "/customer-audience/by-phone").param("phone", "0900000000")
                .header("Authorization", staff)).andExpect(status().isNotFound());
        // đường dẫn promotions vẫn chỉ cho ADMIN/CATALOG_MANAGER
        mvc.perform(get(V1 + UrlAdminConstant.Promotion.CUSTOMER_BY_PHONE).param("phone", customerPhone)
                .header("Authorization", staff)).andExpect(status().isForbidden());
        mvc.perform(get(ORDERS + "/customer-audience/by-phone").param("phone", customerPhone)).andExpect(status().isUnauthorized());
    }

    @Test
    void deletingPlacementUsedByCmsBlockIsConflictUntilPageRemoved() throws Exception {
        admin = bearer(adminAccessToken());
        String code = "PL_" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        BannerPlacementEntity placement = new BannerPlacementEntity();
        placement.setCode(code);
        placement.setName("Vị trí dùng bởi CMS");
        placement.setDisplayType("GRID");
        placements.create(placement);
        String slug = "pl-" + UUID.randomUUID().toString().substring(0, 6);
        long pageId = body(mvc.perform(jsonRequest(post(V1 + UrlAdminConstant.Cms.PAGES), Map.of("name", "Trang", "slug", slug,
                "status", "PUBLISHED", "blocks", List.of(Map.of("type", "BANNER", "sortOrder", 1, "config", Map.of("placementCode", code)))))
                .header("Authorization", admin)).andExpect(status().isCreated())).get("id").asLong();

        String url = V1 + UrlAdminConstant.Banner.PLACEMENTS + "/" + placement.getId();
        JsonNode err = body(mvc.perform(delete(url).header("Authorization", admin)).andExpect(status().isConflict()));
        assertEquals("PLACEMENT_IN_USE_BY_CMS", err.get("errors").get(0).get("code").asText());

        mvc.perform(delete(V1 + UrlAdminConstant.Cms.PAGES + "/" + pageId).header("Authorization", admin)).andExpect(status().is2xxSuccessful());
        mvc.perform(delete(url).header("Authorization", admin)).andExpect(status().is2xxSuccessful());
    }
}
