package com.pk.core.test.business;

import com.fasterxml.jackson.databind.JsonNode;
import com.pk.core.model.constant.UrlConstant;
import com.pk.core.model.constant.admin.UrlAdminConstant;
import com.pk.core.test.common.IntegrationTestBase;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** FE v5 (Banner): slotType (SINGLE_IMAGE|MULTIPLE_IMAGE) tách khỏi layoutType (GRID|SLIDER|DOUBLE_BANNER); slotKey mặc định "main". */
class BannerContractV5IT extends IntegrationTestBase {

    private static final String V1 = UrlConstant.Common.API + UrlConstant.Common.VERSION;
    private static final String PLACEMENTS = V1 + UrlAdminConstant.Banner.PLACEMENTS;
    private static final String BANNERS = V1 + UrlAdminConstant.Banner.BANNERS;

    private String auth() throws Exception {
        return bearer(adminAccessToken());
    }

    private long banner(String auth, String name) throws Exception {
        return body(mvc.perform(jsonRequest(post(BANNERS), Map.of("internalName", name, "status", "ACTIVE",
                        "mediaUrl", "/x.jpg")).header("Authorization", auth)).andExpect(status().isOk())).get("id").asLong();
    }

    private long placement(String auth, String code, String displayType) throws Exception {
        return body(mvc.perform(jsonRequest(post(PLACEMENTS), Map.of("code", code, "name", code, "displayType", displayType))
                .header("Authorization", auth)).andExpect(status().isOk())).get("id").asLong();
    }

    private static Map<String, Object> item(String slot, long bannerId, int priority, Map<String, Object> meta) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (slot != null) {
            m.put("slotKey", slot);
        }
        m.put("bannerId", bannerId);
        m.put("priority", priority);
        if (meta != null) {
            m.put("displayMetadata", meta);
        }
        return m;
    }

    private JsonNode render(String code) throws Exception {
        return body(mvc.perform(get(V1 + UrlConstant.Banner.PLACEMENTS_BY_CODE + "/" + code + "/render"))
                .andExpect(status().isOk()));
    }

    @Test
    void sliderAcceptsLayoutNameInSlotTypeAndDefaultsSlotKey() throws Exception {
        String auth = auth();
        String t = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        long b1 = banner(auth, "S1 " + t);
        long b2 = banner(auth, "S2 " + t);
        long pid = placement(auth, "SLD_" + t, "SLIDER");

        // FE builder gửi slotType=SLIDER và không có slotKey.
        JsonNode created = body(mvc.perform(jsonRequest(post(PLACEMENTS + "/" + pid + "/layouts"),
                        Map.of("name", "slider", "status", "ACTIVE", "items", List.of(
                                item(null, b1, 0, Map.of("slotType", "SLIDER")),
                                item(null, b2, 1, null)))).header("Authorization", auth))
                .andExpect(status().isOk()));
        JsonNode first = created.get("items").get(0);
        assertEquals("main", first.get("slotKey").asText());
        assertEquals("SLIDER", first.get("displayMetadata").get("layoutType").asText());
        assertEquals("MULTIPLE_IMAGE", first.get("displayMetadata").get("slotType").asText());

        JsonNode r = render("SLD_" + t);
        assertEquals(1, r.get("slots").size());
        assertEquals("SLIDER", r.get("slots").get(0).get("layout_type").asText());
        assertEquals("MULTIPLE_IMAGE", r.get("slots").get(0).get("slot_type").asText());
        assertEquals(2, r.get("slots").get(0).get("banners").size());
    }

    @Test
    void gridAndDoubleBannerKeepLayoutTypeAndInferSlotType() throws Exception {
        String auth = auth();
        String t = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        long b1 = banner(auth, "G1 " + t);
        long b2 = banner(auth, "G2 " + t);
        long grid = placement(auth, "GRD_" + t, "GRID");
        long dbl = placement(auth, "DBL_" + t, "DOUBLE_BANNER");

        mvc.perform(jsonRequest(post(PLACEMENTS + "/" + grid + "/layouts"),
                        Map.of("name", "lưới", "status", "ACTIVE", "items", List.of(
                                item("A", b1, 0, Map.of("slotType", "GRID", "x", 0, "y", 0, "width", 2, "height", 1)),
                                item("B", b2, 0, Map.of("layoutType", "grid", "x", 2, "y", 0, "width", 1, "height", 1)))))
                        .header("Authorization", auth))
                .andExpect(status().isOk());
        JsonNode g = render("GRD_" + t);
        assertEquals(2, g.get("slots").size());
        assertEquals("GRID", g.get("slots").get(0).get("layout_type").asText());
        assertEquals("SINGLE_IMAGE", g.get("slots").get(0).get("slot_type").asText());

        mvc.perform(jsonRequest(post(PLACEMENTS + "/" + dbl + "/layouts"),
                        Map.of("name", "đôi", "status", "ACTIVE", "items", List.of(
                                item("LEFT", b1, 0, Map.of("slotType", "DOUBLE_BANNER")),
                                item("RIGHT", b2, 0, null)))).header("Authorization", auth))
                .andExpect(status().isOk());
        JsonNode d = render("DBL_" + t);
        assertEquals("DOUBLE_BANNER", d.get("slots").get(0).get("layout_type").asText());
        // slot không khai báo layoutType vẫn suy ra từ displayType của vị trí.
        assertEquals("DOUBLE_BANNER", d.get("slots").get(1).get("layout_type").asText());
    }

    @Test
    void unknownSlotTypeOrLayoutTypeIsRejected() throws Exception {
        String auth = auth();
        String t = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        long b1 = banner(auth, "X1 " + t);
        long pid = placement(auth, "SLD_" + t, "SLIDER");
        mvc.perform(jsonRequest(post(PLACEMENTS + "/" + pid + "/layouts"),
                        Map.of("name", "sai", "items", List.of(item("main", b1, 0, Map.of("slotType", "PANORAMA")))))
                        .header("Authorization", auth))
                .andExpect(status().isBadRequest());
        mvc.perform(jsonRequest(post(PLACEMENTS + "/" + pid + "/layouts"),
                        Map.of("name", "sai", "items", List.of(item("main", b1, 0, Map.of("layoutType", "MASONRY")))))
                        .header("Authorization", auth))
                .andExpect(status().isBadRequest());
    }
}
