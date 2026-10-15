package com.pk.core.test.business;

import com.fasterxml.jackson.databind.JsonNode;
import com.pk.core.model.constant.UrlConstant;
import com.pk.core.model.constant.admin.UrlAdminConstant;
import com.pk.core.test.common.IntegrationTestBase;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** P0 FE: GET /admin/orders - bỏ query / để rỗng / từng bộ lọc đều phải trả đơn đúng, không bị NULL loại hết dòng. */
class OmsOrderListFilterIT extends IntegrationTestBase {

    private static final String V1 = UrlConstant.Common.API + UrlConstant.Common.VERSION;
    private static final String CATALOG = V1 + UrlAdminConstant.Catalog.PRODUCTS;
    private static final String ORDERS = V1 + UrlAdminConstant.Order.BASE;

    private JsonNode list(String admin, String... kv) throws Exception {
        var req = get(ORDERS).header("Authorization", admin);
        for (int i = 0; i < kv.length; i += 2) {
            req = req.param(kv[i], kv[i + 1]);
        }
        return body(mvc.perform(req).andExpect(status().isOk()));
    }

    private boolean contains(JsonNode page, String code) {
        for (JsonNode o : page.get("content")) {
            if (code.equals(o.get("code").asText())) {
                return true;
            }
        }
        return false;
    }

    @Test
    void adminListWorksWithoutFiltersWithEmptyFiltersAndEachFilter() throws Exception {
        String t = UUID.randomUUID().toString().substring(0, 8);
        String admin = bearer(adminAccessToken());
        long categoryId = body(mvc.perform(jsonRequest(post(V1 + UrlAdminConstant.Catalog.CATEGORIES),
                        Map.of("name", "DM " + t, "slug", "dm-" + t, "sortOrder", 0)).header("Authorization", admin))
                .andExpect(status().isCreated())).get("id").asLong();
        long productId = body(mvc.perform(jsonRequest(post(CATALOG), Map.of(
                        "categoryId", categoryId, "code", "P-" + t, "name", "Nhẫn " + t, "slug", "nhan-" + t,
                        "variants", List.of(Map.of("skuCode", "SKU-" + t, "listPrice", 500_000, "onHand", 20,
                                "isDefault", true, "status", "PUBLISHED")))).header("Authorization", admin))
                .andExpect(status().isCreated())).get("id").asLong();
        long skuId = body(mvc.perform(get(CATALOG + "/" + productId + "/variants").header("Authorization", admin)))
                .get(0).get("id").asLong();
        mvc.perform(patch(CATALOG + "/" + productId + "/publish").header("Authorization", admin)).andExpect(status().isOk());

        // Đơn khách đăng nhập.
        String customer = bearer(customerAccessToken());
        long addressId = body(mvc.perform(jsonRequest(post(V1 + UrlConstant.Me.ADDRESSES), Map.of("recipientName", "Nguyễn A",
                        "phone", "0901234567", "province", "HCM", "district", "Q1", "ward", "P1", "addressLine", "1 Lê Lợi"))
                        .header("Authorization", customer)).andExpect(status().isCreated())).get("id").asLong();
        String customerOrder = body(mvc.perform(jsonRequest(post(V1 + UrlConstant.Order.BASE), Map.of("addressId", addressId,
                        "shippingMethod", "STANDARD", "paymentMethod", "COD",
                        "items", List.of(Map.of("skuId", skuId, "quantity", 1)))).header("Authorization", customer))
                .andExpect(status().isCreated())).get("code").asText();

        // Đơn khách vãng lai (tên người nhận có dấu để thử search).
        String guestName = "Khách " + t;
        String guestOrder = body(mvc.perform(jsonRequest(post(V1 + UrlConstant.Order.GUEST), Map.of("recipientName", guestName,
                        "phone", "0912345678", "province", "HCM", "addressLine", "2 Lê Lợi", "shippingMethod", "STANDARD",
                        "paymentMethod", "COD", "items", List.of(Map.of("skuId", skuId, "quantity", 1)))))
                .andExpect(status().isCreated())).get("code").asText();

        // 1) Không query -> thấy cả hai.
        JsonNode all = list(admin);
        assertTrue(contains(all, customerOrder) && contains(all, guestOrder), "không filter phải thấy cả 2 đơn");
        // 2) Tham số có nhưng rỗng (FE cũ gửi ?status=&paymentStatus=&search=) -> y hệt.
        JsonNode empty = list(admin, "status", "", "paymentStatus", "", "search", "", "fromDate", "", "toDate", "");
        assertTrue(contains(empty, customerOrder) && contains(empty, guestOrder), "filter rỗng phải thấy cả 2 đơn");
        assertEquals(all.get("totalElements").asLong(), empty.get("totalElements").asLong());
        // 3) Từng filter.
        assertTrue(contains(list(admin, "status", "PENDING"), customerOrder));
        assertTrue(contains(list(admin, "status", "pending"), customerOrder), "status không phân biệt hoa/thường");
        assertTrue(contains(list(admin, "paymentStatus", "UNPAID"), guestOrder));
        assertEquals(0, list(admin, "status", "DELIVERED", "search", guestName).get("totalElements").asLong());
        JsonNode bySearch = list(admin, "search", guestName);
        assertTrue(contains(bySearch, guestOrder) && !contains(bySearch, customerOrder));
        assertTrue(contains(list(admin, "search", customerOrder), customerOrder));
        assertTrue(contains(list(admin, "search", "0912345678"), guestOrder));
        // 4) Khoảng ngày: hôm nay thấy, ngày quá khứ xa / tương lai thì không.
        String today = LocalDate.now().toString();
        assertTrue(contains(list(admin, "fromDate", today, "toDate", today), customerOrder));
        assertEquals(0, list(admin, "toDate", "2000-01-01").get("totalElements").asLong());
        assertEquals(0, list(admin, "fromDate", LocalDate.now().plusDays(2).toString()).get("totalElements").asLong());
        // 5) Phân trang: take=1 trả đúng 1 dòng, tổng không đổi.
        JsonNode p1 = list(admin, "take", "1", "page", "1");
        assertEquals(1, p1.get("content").size());
        assertEquals(all.get("totalElements").asLong(), p1.get("totalElements").asLong());
        // 6) Sai định dạng ngày -> 400 (không 500, không 201).
        mvc.perform(get(ORDERS).header("Authorization", admin).param("fromDate", "31/12/2026")).andExpect(status().isBadRequest());
    }

    @Test
    void errorsAreNotReturnedAsHttp201() throws Exception {
        String admin = bearer(adminAccessToken());
        mvc.perform(get(ORDERS + "/KHONG-TON-TAI").header("Authorization", admin)).andExpect(status().isNotFound());
        mvc.perform(get(ORDERS)).andExpect(status().isUnauthorized());
        mvc.perform(get(ORDERS).header("Authorization", bearer(customerAccessToken()))).andExpect(status().isForbidden());
        // POST (có @ResponseStatus(CREATED)) lỗi nghiệp vụ -> 4xx chứ không 201.
        String customer = bearer(customerAccessToken());
        mvc.perform(jsonRequest(post(V1 + UrlConstant.Order.BASE), Map.of("addressId", 999_999_999L, "shippingMethod", "STANDARD",
                        "paymentMethod", "COD", "items", List.of(Map.of("skuId", 999_999_999L, "quantity", 1))))
                        .header("Authorization", customer))
                .andExpect(result -> assertTrue(result.getResponse().getStatus() >= 400, "phải là 4xx, nhận " + result.getResponse().getStatus()));
    }
}
