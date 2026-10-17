package com.pk.core.test.business;

import com.fasterxml.jackson.databind.JsonNode;
import com.pk.core.model.constant.UrlConstant;
import com.pk.core.model.constant.admin.UrlAdminConstant;
import com.pk.core.test.common.IntegrationTestBase;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** CMS Navigation: cây nháp/xuất bản, giới hạn 4 cấp, validate target, nhãn có lịch + xung đột, reorder, lưu trữ, API công khai + plp-rail. */
class NavigationIT extends IntegrationTestBase {

    private static final String V1 = UrlConstant.Common.API + UrlConstant.Common.VERSION;
    private static final String NAV = V1 + UrlAdminConstant.Cms.NAVIGATIONS;
    private static final String PUBLIC = V1 + UrlConstant.Cms.NAVIGATION;

    private long node(String admin, String label, String type, Long parentId, Map<String, Object> extra) throws Exception {
        Map<String, Object> m = new HashMap<>();
        m.put("label", label);
        m.put("type", type);
        if (parentId != null) {
            m.put("parentId", parentId);
        }
        if (extra != null) {
            m.putAll(extra);
        }
        return body(mvc.perform(jsonRequest(post(NAV), m).header("Authorization", admin)).andExpect(status().isCreated())).get("id").asLong();
    }

    private static JsonNode find(JsonNode items, String label) {
        for (JsonNode n : items) {
            if (label.equals(n.get("label").asText())) {
                return n;
            }
            JsonNode inner = n.has("children") ? find(n.get("children"), label) : null;
            if (inner != null) {
                return inner;
            }
        }
        return null;
    }

    private String code(org.springframework.test.web.servlet.ResultActions r) throws Exception {
        return body(r).get("errors").get(0).get("code").asText();
    }

    @Test
    void treeDepthValidationPublishAndPublicView() throws Exception {
        String t = UUID.randomUUID().toString().substring(0, 6);
        String admin = bearer(adminAccessToken());
        long categoryId = body(mvc.perform(jsonRequest(post(V1 + UrlAdminConstant.Catalog.CATEGORIES),
                Map.of("name", "DM " + t, "slug", "dm-" + t, "sortOrder", 0)).header("Authorization", admin))
                .andExpect(status().isCreated())).get("id").asLong();

        // validate
        assertEquals("NAVIGATION_TARGET_INVALID", code(mvc.perform(jsonRequest(post(NAV),
                Map.of("label", "X", "type", "CATEGORY", "targetId", "99999999")).header("Authorization", admin))
                .andExpect(status().isBadRequest())));
        assertEquals("NAVIGATION_TARGET_INVALID", code(mvc.perform(jsonRequest(post(NAV),
                Map.of("label", "X", "type", "PAGE", "targetId", "abc")).header("Authorization", admin))
                .andExpect(status().isBadRequest())));
        mvc.perform(jsonRequest(post(NAV), Map.of("label", "X", "type", "LINK", "url", "javascript:alert(1)")).header("Authorization", admin))
                .andExpect(status().isBadRequest());
        mvc.perform(jsonRequest(post(NAV), Map.of("label", " ", "type", "NONE")).header("Authorization", admin)).andExpect(status().isBadRequest());
        mvc.perform(jsonRequest(post(NAV), Map.of("label", "X", "type", "WEIRD")).header("Authorization", admin)).andExpect(status().isBadRequest());
        mvc.perform(get(NAV + "/999999999").header("Authorization", admin)).andExpect(status().isNotFound());

        // cây 4 cấp
        long root = node(admin, "Gốc " + t, "CATEGORY", null, Map.of("targetId", String.valueOf(categoryId), "url", "/c/dm-" + t));
        long l2 = node(admin, "Cấp2 " + t, "LINK", root, Map.of("url", "https://example.com/a", "openInNewTab", true));
        long l3 = node(admin, "Cấp3 " + t, "NONE", l2, null);
        long l4 = node(admin, "Cấp4 " + t, "LINK", l3, Map.of("url", "/x", "showOnMenuPopup", true));
        assertCode(admin, l4, t);
        // cấp 5 -> 400
        assertEquals("NAVIGATION_DEPTH_EXCEEDED", code(mvc.perform(jsonRequest(post(NAV),
                Map.of("label", "Cấp5", "type", "NONE", "parentId", l4)).header("Authorization", admin)).andExpect(status().isBadRequest())));

        JsonNode d4 = body(mvc.perform(get(NAV + "/" + l4).header("Authorization", admin)).andExpect(status().isOk()));
        assertFalse(d4.get("showOnMenuPopup").asBoolean(), "cấp 4 không hiện trong menu popup");
        assertEquals(l3, d4.get("parentId").asLong());
        assertEquals("DRAFT", d4.get("layer").asText());

        JsonNode tree = body(mvc.perform(get(NAV).header("Authorization", admin)).andExpect(status().isOk())).get("items");
        assertNotNull(find(tree, "Cấp4 " + t));

        // chưa xuất bản: công khai không thấy; có thay đổi chờ xuất bản
        assertNull(find(body(mvc.perform(get(PUBLIC)).andExpect(status().isOk())).get("items"), "Gốc " + t));
        assertTrue(body(mvc.perform(get(NAV + "/publish-status").header("Authorization", admin))).get("hasPendingPublish").asBoolean());

        JsonNode pub = body(mvc.perform(post(NAV + "/publish").header("Authorization", admin)).andExpect(status().isOk()));
        assertTrue(pub.get("success").asBoolean());
        assertFalse(pub.get("hasPendingPublish").asBoolean());
        assertFalse(body(mvc.perform(get(NAV + "/publish-status").header("Authorization", admin))).get("hasPendingPublish").asBoolean());

        JsonNode items = body(mvc.perform(get(PUBLIC)).andExpect(status().isOk())).get("items");
        JsonNode pRoot = find(items, "Gốc " + t);
        assertNotNull(pRoot);
        assertEquals(String.valueOf(root), pRoot.get("id").asText(), "id công khai = id nút nháp");
        assertEquals("category", pRoot.get("linkTarget").get("kind").asText());
        assertEquals(String.valueOf(categoryId), pRoot.get("targetId").asText());
        assertTrue(pRoot.get("hasChildren").asBoolean());
        assertEquals("Xem tất cả", pRoot.get("viewAllLabel").asText());
        JsonNode p2 = find(items, "Cấp2 " + t);
        assertEquals("url", p2.get("linkTarget").get("kind").asText());
        assertTrue(p2.get("openInNewTab").asBoolean());
        assertEquals(String.valueOf(root), p2.get("parentId").asText());
        JsonNode p3 = find(items, "Cấp3 " + t);
        assertTrue(p3.get("expandOnly").asBoolean(), "NONE có con -> chỉ mở rộng");
        assertEquals("none", p3.get("linkTarget").get("kind").asText());

        // sửa sau xuất bản: chờ xuất bản, công khai chưa đổi
        mvc.perform(jsonRequest(patch(NAV + "/" + l2), Map.of("label", "Cấp2 mới " + t)).header("Authorization", admin)).andExpect(status().isOk());
        assertTrue(body(mvc.perform(get(NAV + "/publish-status").header("Authorization", admin))).get("hasPendingPublish").asBoolean());
        assertNotNull(find(body(mvc.perform(get(PUBLIC))).get("items"), "Cấp2 " + t));
        assertNull(find(body(mvc.perform(get(PUBLIC))).get("items"), "Cấp2 mới " + t));

        // reorder: gốc có 2 con
        long l2b = node(admin, "Anh em " + t, "NONE", root, null);
        mvc.perform(jsonRequest(patch(NAV + "/reorder"), Map.of("items", List.of(Map.of("id", l2b, "sortOrder", 0), Map.of("id", l2, "sortOrder", 1))))
                .header("Authorization", admin)).andExpect(status().isOk());
        JsonNode kids = find(body(mvc.perform(get(NAV).header("Authorization", admin))).get("items"), "Gốc " + t).get("children");
        assertEquals("Anh em " + t, kids.get(0).get("label").asText());
        mvc.perform(jsonRequest(patch(NAV + "/reorder"), Map.of("items", List.of(Map.of("id", 999999999L, "sortOrder", 0)))).header("Authorization", admin))
                .andExpect(status().isNotFound());

        // lưu trữ cấp 2 -> mất cả cấp 2,3,4 khỏi cây nháp; sau xuất bản mất khỏi công khai
        JsonNode arch = body(mvc.perform(patch(NAV + "/" + l2 + "/archive").header("Authorization", admin)).andExpect(status().isOk()));
        assertTrue(arch.get("success").asBoolean());
        JsonNode tree2 = body(mvc.perform(get(NAV).header("Authorization", admin))).get("items");
        assertNull(find(tree2, "Cấp3 " + t));
        assertNotNull(find(tree2, "Anh em " + t));
        mvc.perform(post(NAV + "/publish").header("Authorization", admin)).andExpect(status().isOk());
        JsonNode items2 = body(mvc.perform(get(PUBLIC))).get("items");
        assertNull(find(items2, "Cấp3 " + t));
        assertNotNull(find(items2, "Anh em " + t));
        // nút đã lưu trữ không sửa được
        mvc.perform(jsonRequest(patch(NAV + "/" + l2), Map.of("label", "Y")).header("Authorization", admin)).andExpect(status().isBadRequest());

        // cache
        assertTrue(body(mvc.perform(delete(NAV + "/cache").header("Authorization", admin)).andExpect(status().isOk())).get("success").asBoolean());
    }

    private void assertCode(String admin, long id, String t) throws Exception {
        assertEquals("Cấp4 " + t, body(mvc.perform(get(NAV + "/" + id).header("Authorization", admin))).get("label").asText());
    }

    @Test
    void badgeScheduleConflictAndActiveBadgeOnPublicView() throws Exception {
        String t = UUID.randomUUID().toString().substring(0, 6);
        String admin = bearer(adminAccessToken());
        long id = node(admin, "Có nhãn " + t, "NONE", null, null);
        Instant now = Instant.now();

        // validate nhãn
        mvc.perform(jsonRequest(patch(NAV + "/" + id), Map.of("badge", Map.of("designType", "TEXT", "displayText", "QUÁDÀI",
                "startTime", now.minus(1, ChronoUnit.DAYS).toString()))).header("Authorization", admin)).andExpect(status().isBadRequest());
        mvc.perform(jsonRequest(patch(NAV + "/" + id), Map.of("badge", Map.of("designType", "IMAGE",
                "startTime", now.toString()))).header("Authorization", admin)).andExpect(status().isBadRequest());
        mvc.perform(jsonRequest(patch(NAV + "/" + id), Map.of("badge", Map.of("designType", "TEXT", "displayText", "HOT",
                "startTime", now.toString(), "endTime", now.minus(1, ChronoUnit.HOURS).toString()))).header("Authorization", admin))
                .andExpect(status().isBadRequest());

        // nhãn A: từ hôm qua, không hết hạn
        Map<String, Object> a = Map.of("designType", "TEXT", "displayText", "HOT", "styleConfig", Map.of("color", "#fff"),
                "startTime", now.minus(1, ChronoUnit.DAYS).toString());
        JsonNode saved = body(mvc.perform(jsonRequest(patch(NAV + "/" + id), Map.of("badge", a)).header("Authorization", admin)).andExpect(status().isOk()));
        assertEquals(1, saved.get("badges").size());
        long badgeA = saved.get("badges").get(0).get("id").asLong();

        // nhãn B trùng lịch -> 409 + dependencies; xác nhận -> thay thế A
        Map<String, Object> b = new HashMap<>(Map.of("designType", "TEXT", "displayText", "NEW",
                "startTime", now.minus(1, ChronoUnit.HOURS).toString()));
        var conflict = mvc.perform(jsonRequest(patch(NAV + "/" + id), Map.of("badge", b)).header("Authorization", admin)).andExpect(status().isConflict());
        JsonNode err = body(conflict);
        assertEquals("NAVIGATION_BADGE_SCHEDULE_CONFLICT", err.get("errors").get(0).get("code").asText());
        assertEquals(String.valueOf(badgeA), err.get("dependencies").get(0).get("id").asText());
        assertEquals("HOT", err.get("dependencies").get(0).get("displayText").asText());
        b.put("confirmReplaceSchedule", true);
        JsonNode replaced = body(mvc.perform(jsonRequest(patch(NAV + "/" + id), Map.of("badge", b)).header("Authorization", admin)).andExpect(status().isOk()));
        assertEquals(1, replaced.get("badges").size());
        assertEquals("NEW", replaced.get("badges").get(0).get("displayText").asText());

        // công khai chỉ thấy nhãn đang hiệu lực
        mvc.perform(post(NAV + "/publish").header("Authorization", admin)).andExpect(status().isOk());
        JsonNode pub = find(body(mvc.perform(get(PUBLIC))).get("items"), "Có nhãn " + t);
        assertEquals("NEW", pub.get("badge").get("displayText").asText());

        // nhãn trong tương lai không hiện
        long id2 = node(admin, "Nhãn tương lai " + t, "NONE", null, null);
        mvc.perform(jsonRequest(patch(NAV + "/" + id2), Map.of("badge", Map.of("designType", "TEXT", "displayText", "SOON",
                "startTime", now.plus(2, ChronoUnit.DAYS).toString()))).header("Authorization", admin)).andExpect(status().isOk());
        mvc.perform(post(NAV + "/publish").header("Authorization", admin)).andExpect(status().isOk());
        JsonNode future = find(body(mvc.perform(get(PUBLIC))).get("items"), "Nhãn tương lai " + t);
        assertTrue(future.get("badge") == null || future.get("badge").isNull());

        // clearBadge
        JsonNode cleared = body(mvc.perform(jsonRequest(patch(NAV + "/" + id), Map.of("clearBadge", true)).header("Authorization", admin)).andExpect(status().isOk()));
        assertEquals(0, cleared.get("badges").size());
    }

    @Test
    void plpRailReturnsShowOnPlpChildrenOfMatchingRoot() throws Exception {
        String t = UUID.randomUUID().toString().substring(0, 6);
        String admin = bearer(adminAccessToken());
        long categoryId = body(mvc.perform(jsonRequest(post(V1 + UrlAdminConstant.Catalog.CATEGORIES),
                Map.of("name", "Rail " + t, "slug", "rail-" + t, "sortOrder", 0)).header("Authorization", admin))
                .andExpect(status().isCreated())).get("id").asLong();
        long root = node(admin, "RailGốc " + t, "CATEGORY", null, Map.of("targetId", String.valueOf(categoryId)));
        node(admin, "RailHiện " + t, "LINK", root, Map.of("url", "/hien", "showOnPlp", true));
        node(admin, "RailẨn " + t, "LINK", root, Map.of("url", "/an", "showOnPlp", false));
        mvc.perform(post(NAV + "/publish").header("Authorization", admin)).andExpect(status().isOk());

        JsonNode rail = body(mvc.perform(get(PUBLIC + "/plp-rail").param("categoryId", String.valueOf(categoryId))).andExpect(status().isOk())).get("items");
        assertEquals(1, rail.size());
        assertEquals("RailHiện " + t, rail.get(0).get("label").asText());
        JsonNode byTarget = body(mvc.perform(get(PUBLIC + "/plp-rail").param("rootTargetId", String.valueOf(categoryId)))).get("items");
        assertEquals(1, byTarget.size());
        // không khớp -> các nút gốc showOnPlp (có chứa gốc của test này)
        JsonNode fallback = body(mvc.perform(get(PUBLIC + "/plp-rail").param("categoryId", "123456789"))).get("items");
        assertNotNull(find(fallback, "RailGốc " + t));
        assertNull(find(fallback, "RailẨn " + t));
    }
}
