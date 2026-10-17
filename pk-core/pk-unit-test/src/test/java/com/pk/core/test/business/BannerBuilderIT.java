package com.pk.core.test.business;

import com.fasterxml.jackson.databind.JsonNode;
import com.pk.core.model.constant.UrlConstant;
import com.pk.core.model.constant.admin.UrlAdminConstant;
import com.pk.core.test.common.IntegrationTestBase;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Banner builder qua API: GRID (toạ độ/span), DOUBLE_BANNER (2 slot độc lập), SLIDER (1 slot, thứ tự priority). */
class BannerBuilderIT extends IntegrationTestBase {

    private static final String V1 = UrlConstant.Common.API + UrlConstant.Common.VERSION;
    private static final String PLACEMENTS = V1 + UrlAdminConstant.Banner.PLACEMENTS;
    private static final String BANNERS = V1 + UrlAdminConstant.Banner.BANNERS;

    private String auth() throws Exception {
        return bearer(adminAccessToken());
    }

    private long banner(String auth, String name, String status) throws Exception {
        return body(mvc.perform(jsonRequest(post(BANNERS), Map.of("internalName", name, "status", status,
                        "mediaUrl", "/x.jpg")).header("Authorization", auth)).andExpect(status().isOk())).get("id").asLong();
    }

    private JsonNode placement(String auth, Map<String, Object> req) throws Exception {
        return body(mvc.perform(jsonRequest(post(PLACEMENTS), req).header("Authorization", auth)).andExpect(status().isOk()));
    }

    private static Map<String, Object> item(String slot, long bannerId, int priority, Map<String, Object> meta) {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("slotKey", slot);
        m.put("bannerId", bannerId);
        m.put("priority", priority);
        if (meta != null) {
            m.put("displayMetadata", meta);
        }
        return m;
    }

    private JsonNode render(String code, String platform) throws Exception {
        var req = get(V1 + UrlConstant.Banner.PLACEMENTS_BY_CODE + "/" + code + "/render");
        if (platform != null) {
            req = req.param("platform", platform);
        }
        return body(mvc.perform(req).andExpect(status().isOk()));
    }

    private static JsonNode slot(JsonNode render, String key) {
        for (JsonNode s : render.get("slots")) {
            if (key.equals(s.get("slot_key").asText())) {
                return s;
            }
        }
        throw new AssertionError("không có slot " + key);
    }

    @Test
    void gridLayoutPersistsSlotsCoordinatesAndRendersPerPlatformAndStatus() throws Exception {
        String auth = auth();
        String t = UUID.randomUUID().toString().substring(0, 6);
        // code bỏ trống -> tự sinh từ tên; settings + platform lưu lại.
        JsonNode p = placement(auth, Map.of("name", "Lưới KM " + t, "displayType", "GRID", "platform", "WEB",
                "settings", Map.of("columns", 4, "rows", 2, "gap", 8, "mobileOddFullWidthItem", "LAST")));
        long pid = p.get("id").asLong();
        String code = p.get("code").asText();
        assertTrue(code.startsWith("LUOI_KM_"), code);
        assertEquals(4, p.get("settings").get("columns").asInt());
        assertEquals("LAST", p.get("settings").get("mobileOddFullWidthItem").asText());
        assertEquals("WEB", p.get("platform").asText());

        long b1 = banner(auth, "G1 " + t, "ACTIVE");
        long b2 = banner(auth, "G2 " + t, "ACTIVE");
        long b3 = banner(auth, "G3 " + t, "ACTIVE");
        String layouts = PLACEMENTS + "/" + pid + "/layouts";

        // GRID thiếu toạ độ -> 400; vượt số cột -> 400; trùng (slot, banner) -> 400; banner không tồn tại -> 404.
        mvc.perform(jsonRequest(post(layouts), Map.of("name", "L", "items", List.of(item("A", b1, 0, null))))
                .header("Authorization", auth)).andExpect(status().isBadRequest());
        mvc.perform(jsonRequest(post(layouts), Map.of("name", "L", "items", List.of(
                        item("A", b1, 0, Map.of("x", 3, "y", 0, "width", 2, "height", 1)))))
                .header("Authorization", auth)).andExpect(status().isBadRequest());
        mvc.perform(jsonRequest(post(layouts), Map.of("name", "L", "items", List.of(
                        item("A", b1, 0, Map.of("x", 0, "y", 0, "width", 1, "height", 1)),
                        item("A", b1, 1, null)))).header("Authorization", auth)).andExpect(status().isBadRequest());
        mvc.perform(jsonRequest(post(layouts), Map.of("name", "L", "items", List.of(
                        item("A", 999999999L, 0, Map.of("x", 0, "y", 0, "width", 1, "height", 1)))))
                .header("Authorization", auth)).andExpect(status().isNotFound());

        // Chưa có layout nào -> render theo cách cũ (không có banner), không lỗi.
        assertEquals(0, render(code, "WEB").get("banners").size());

        Map<String, Object> req = new java.util.LinkedHashMap<>();
        req.put("name", "Layout mùa thu");
        req.put("status", "ACTIVE");
        req.put("items", List.of(
                item("A", b1, 0, Map.of("x", 0, "y", 0, "width", 2, "height", 2, "order", 0)),
                item("B", b2, 0, Map.of("x", 2, "y", 0, "width", 2, "height", 1, "order", 1)),
                item("B", b3, 1, null)));
        JsonNode layout = body(mvc.perform(jsonRequest(post(layouts), req).header("Authorization", auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(3)));
        long lid = layout.get("id").asLong();

        // Đọc lại: toạ độ/span/order lưu đúng, kèm banner để xem trước.
        JsonNode detail = body(mvc.perform(get(layouts + "/" + lid).header("Authorization", auth)).andExpect(status().isOk()));
        assertEquals("ACTIVE", detail.get("status").asText());
        JsonNode first = detail.get("items").get(0);
        assertEquals("A", first.get("slotKey").asText());
        assertEquals(2, first.get("displayMetadata").get("width").asInt());
        assertTrue(first.get("isActive").asBoolean());
        assertEquals("G1 " + t, first.get("banner").get("internalName").asText());
        assertEquals(1, body(mvc.perform(get(layouts).header("Authorization", auth)).andExpect(status().isOk())).size());

        // revision: layout đầu = 1; layout tạo sau = 2 (tăng dần theo vị trí); sửa layout không đổi revision.
        assertEquals(1, detail.get("revision").asInt());
        Map<String, Object> req2 = new java.util.LinkedHashMap<>();
        req2.put("name", "Layout bản nháp");
        req2.put("status", "DRAFT");
        req2.put("items", List.of(item("A", b1, 0, Map.of("x", 0, "y", 0, "width", 1, "height", 1, "order", 0))));
        JsonNode second = body(mvc.perform(jsonRequest(post(layouts), req2).header("Authorization", auth)).andExpect(status().isOk()));
        assertEquals(2, second.get("revision").asInt());
        JsonNode patched = body(mvc.perform(jsonRequest(patch(layouts + "/" + second.get("id").asLong()),
                Map.of("name", "Đổi tên")).header("Authorization", auth)).andExpect(status().isOk()));
        assertEquals(2, patched.get("revision").asInt());
        assertEquals(1, body(mvc.perform(get(layouts + "/" + lid).header("Authorization", auth))).get("revision").asInt());

        // Render: 2 slot thật, toạ độ giữ nguyên, slot B có 2 banner theo priority.
        JsonNode r = render(code, "WEB");
        assertEquals(2, r.get("slots").size());
        JsonNode sa = slot(r, "A");
        assertEquals("SINGLE_IMAGE", sa.get("slot_type").asText());
        assertEquals(2, sa.get("layout_metadata").get("width").asInt());
        assertEquals(2, sa.get("layout_metadata").get("height").asInt());
        JsonNode sb = slot(r, "B");
        assertEquals("MULTIPLE_IMAGE", sb.get("slot_type").asText());
        assertEquals(2, sb.get("layout_metadata").get("x").asInt());
        assertEquals(2, sb.get("banners").size());
        assertEquals("G3 " + t, sb.get("banners").get(1).get("banner").get("internal_name").asText());
        assertEquals(3, r.get("banners").size());
        assertEquals(4, r.get("settings").get("columns").asInt());

        // Platform: placement WEB -> APP không thấy gì, không truyền platform thì thấy.
        assertEquals(0, render(code, "APP").get("slots").size());
        assertEquals(2, render(code, null).get("slots").size());

        // PATCH item: tắt b3 -> slot B còn 1 banner (SINGLE_IMAGE); đổi toạ độ item.
        long b3Item = 0;
        long aItem = 0;
        for (JsonNode it : detail.get("items")) {
            if (it.get("bannerId").asLong() == b3) {
                b3Item = it.get("id").asLong();
            }
            if (it.get("bannerId").asLong() == b1) {
                aItem = it.get("id").asLong();
            }
        }
        mvc.perform(jsonRequest(patch(layouts + "/" + lid + "/items/" + b3Item), Map.of("isActive", false))
                        .header("Authorization", auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.isActive").value(false));
        assertEquals("SINGLE_IMAGE", slot(render(code, "WEB"), "B").get("slot_type").asText());
        mvc.perform(jsonRequest(patch(layouts + "/" + lid + "/items/" + aItem),
                        Map.of("displayMetadata", Map.of("x", 0, "y", 1, "width", 4, "height", 1))).header("Authorization", auth))
                .andExpect(status().isOk());
        assertEquals(4, slot(render(code, "WEB"), "A").get("layout_metadata").get("width").asInt());
        // PATCH sai: vượt cột -> 400; item không thuộc layout -> 404.
        mvc.perform(jsonRequest(patch(layouts + "/" + lid + "/items/" + aItem),
                        Map.of("displayMetadata", Map.of("x", 2, "y", 0, "width", 4, "height", 1))).header("Authorization", auth))
                .andExpect(status().isBadRequest());
        mvc.perform(jsonRequest(patch(layouts + "/" + lid + "/items/999999999"), Map.of("priority", 1))
                .header("Authorization", auth)).andExpect(status().isNotFound());

        // Banner ARCHIVED biến mất khỏi render nhưng item vẫn còn trong layout.
        mvc.perform(jsonRequest(patch(BANNERS + "/" + b2), Map.of("status", "ARCHIVED")).header("Authorization", auth))
                .andExpect(status().isOk());
        assertEquals(1, render(code, "WEB").get("slots").size());
        assertEquals(3, body(mvc.perform(get(layouts + "/" + lid).header("Authorization", auth))).get("items").size());

        // Gỡ item khỏi layout: banner KHÔNG bị xoá.
        mvc.perform(delete(layouts + "/" + lid + "/items/" + aItem).header("Authorization", auth)).andExpect(status().isOk());
        mvc.perform(get(BANNERS + "/" + b1).header("Authorization", auth)).andExpect(status().isOk());
        assertEquals(2, body(mvc.perform(get(layouts + "/" + lid).header("Authorization", auth))).get("items").size());

        // Layout ACTIVE thứ hai -> layout đầu chuyển INACTIVE; layout có activeFrom tương lai không hiển thị.
        long l2 = body(mvc.perform(jsonRequest(post(layouts), Map.of("name", "Layout 2", "status", "ACTIVE", "items", List.of(
                        item("C", b1, 0, Map.of("x", 0, "y", 0, "width", 1, "height", 1))))).header("Authorization", auth))
                .andExpect(status().isOk())).get("id").asLong();
        mvc.perform(get(layouts + "/" + lid).header("Authorization", auth)).andExpect(jsonPath("$.data.status").value("INACTIVE"));
        assertEquals("C", render(code, "WEB").get("slots").get(0).get("slot_key").asText());
        mvc.perform(jsonRequest(patch(layouts + "/" + l2), Map.of("activeFrom", "2099-01-01T00:00:00Z"))
                .header("Authorization", auth)).andExpect(status().isOk());
        assertEquals(0, render(code, "WEB").get("slots").size());

        // Vị trí INACTIVE -> render rỗng; layout vẫn nguyên.
        mvc.perform(jsonRequest(patch(PLACEMENTS + "/" + pid), Map.of("status", "INACTIVE")).header("Authorization", auth))
                .andExpect(status().isOk());
        assertEquals(0, render(code, "WEB").get("slots").size());
    }

    @Test
    void doubleBannerHasTwoIndependentSlotsAndSliderHasOne() throws Exception {
        String auth = auth();
        String t = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        long b1 = banner(auth, "D1 " + t, "ACTIVE");
        long b2 = banner(auth, "D2 " + t, "ACTIVE");
        long b3 = banner(auth, "D3 " + t, "ACTIVE");

        JsonNode dbl = placement(auth, Map.of("code", "DBL_" + t, "name", "Hai khung", "displayType", "DOUBLE_BANNER"));
        String dl = PLACEMENTS + "/" + dbl.get("id").asLong() + "/layouts";
        mvc.perform(jsonRequest(post(dl), Map.of("name", "ba slot", "items", List.of(
                        item("LEFT", b1, 0, null), item("RIGHT", b2, 0, null), item("THIRD", b3, 0, null))))
                .header("Authorization", auth)).andExpect(status().isBadRequest());
        mvc.perform(jsonRequest(post(dl), Map.of("name", "hai slot", "status", "ACTIVE", "items", List.of(
                        item("LEFT", b1, 0, Map.of("order", 0, "slotType", "SINGLE_IMAGE")),
                        item("RIGHT", b2, 0, Map.of("order", 1)),
                        item("RIGHT", b3, 1, null)))).header("Authorization", auth)).andExpect(status().isOk());
        JsonNode r = render("DBL_" + t, null);
        assertEquals(2, r.get("slots").size());
        assertEquals("LEFT", r.get("slots").get(0).get("slot_key").asText());
        assertEquals(1, slot(r, "LEFT").get("banners").size());
        assertEquals(2, slot(r, "RIGHT").get("banners").size());
        // SINGLE_IMAGE mà nhét 2 banner -> 400.
        mvc.perform(jsonRequest(post(dl), Map.of("name", "sai", "items", List.of(
                        item("LEFT", b1, 0, Map.of("slotType", "SINGLE_IMAGE")), item("LEFT", b2, 1, null))))
                .header("Authorization", auth)).andExpect(status().isBadRequest());

        JsonNode sld = placement(auth, Map.of("code", "SLD_" + t, "name", "Slider", "displayType", "SLIDER"));
        String sl = PLACEMENTS + "/" + sld.get("id").asLong() + "/layouts";
        mvc.perform(jsonRequest(post(sl), Map.of("name", "hai slot", "items", List.of(
                        item("main", b1, 0, null), item("other", b2, 0, null)))).header("Authorization", auth))
                .andExpect(status().isBadRequest());
        List<Map<String, Object>> three = new ArrayList<>(List.of(item("main", b1, 2, null), item("main", b2, 0, null),
                item("main", b3, 1, null)));
        mvc.perform(jsonRequest(post(sl), Map.of("name", "slider", "status", "ACTIVE", "items", three))
                .header("Authorization", auth)).andExpect(status().isOk());
        JsonNode sr = render("SLD_" + t, null);
        assertEquals("SLIDER", sr.get("type").asText());
        JsonNode main = slot(sr, "main");
        assertEquals("D2 " + t, main.get("banners").get(0).get("banner").get("internal_name").asText());
        assertEquals("D1 " + t, main.get("banners").get(2).get("banner").get("internal_name").asText());
        assertEquals(2, main.get("banners").get(2).get("order_index").asInt());

        // Layout/ vị trí không tồn tại -> 404; displayType cũ CAROUSEL vẫn được nhận.
        mvc.perform(get(PLACEMENTS + "/999999999/layouts").header("Authorization", auth)).andExpect(status().isNotFound());
        mvc.perform(get(sl + "/999999999").header("Authorization", auth)).andExpect(status().isNotFound());
        placement(auth, Map.of("code", "CAR_" + t, "name", "Carousel cũ", "displayType", "CAROUSEL"));
    }
}
