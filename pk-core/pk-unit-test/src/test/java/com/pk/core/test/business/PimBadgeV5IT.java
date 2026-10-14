package com.pk.core.test.business;

import com.pk.core.model.constant.UrlConstant;
import com.pk.core.model.constant.admin.UrlAdminConstant;
import com.pk.core.test.common.IntegrationTestBase;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** FE v5: DELETE category/collection/attribute có bảo vệ ràng buộc; badge scope/guard/response mobile. */
class PimBadgeV5IT extends IntegrationTestBase {

    private static final String V1 = UrlConstant.Common.API + UrlConstant.Common.VERSION;

    private String auth() throws Exception {
        return bearer(adminAccessToken());
    }

    private static String tag() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private long createCategory(String name, Long parentId) throws Exception {
        String t = tag();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name + " " + t);
        m.put("slug", "dm-" + t);
        m.put("sortOrder", 0);
        if (parentId != null) {
            m.put("parentId", parentId);
        }
        return body(mvc.perform(jsonRequest(post(V1 + UrlAdminConstant.Catalog.CATEGORIES), m)
                        .header("Authorization", auth()))
                .andExpect(status().isCreated())).get("id").asLong();
    }

    // ------------------------------------------------------------------ PIM DELETE

    @Test
    void categoryWithChildOrProductCannotBeDeleted() throws Exception {
        long parent = createCategory("Cha", null);
        long child = createCategory("Con", parent);

        mvc.perform(delete(V1 + UrlAdminConstant.Catalog.CATEGORIES + "/" + parent).header("Authorization", auth()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].code").value("CATEGORY_IN_USE"));

        // Có sản phẩm trong danh mục con -> không xoá được con.
        String code = "P" + tag().toUpperCase();
        mvc.perform(jsonRequest(post(V1 + UrlAdminConstant.Catalog.PRODUCTS), Map.of(
                        "categoryId", child, "code", code, "name", "SP " + code, "slug", code.toLowerCase(),
                        "variants", List.of(Map.of("skuCode", code + "-1", "listPrice", 1_000_000, "onHand", 1,
                                "isDefault", true)))).header("Authorization", auth()))
                .andExpect(status().is2xxSuccessful());
        mvc.perform(delete(V1 + UrlAdminConstant.Catalog.CATEGORIES + "/" + child).header("Authorization", auth()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].code").value("CATEGORY_IN_USE"));
    }

    @Test
    void emptyCategoryCollectionAndUnusedAttributeCanBeDeleted() throws Exception {
        long cat = createCategory("Trống", null);
        mvc.perform(delete(V1 + UrlAdminConstant.Catalog.CATEGORIES + "/" + cat).header("Authorization", auth()))
                .andExpect(status().isNoContent());
        mvc.perform(delete(V1 + UrlAdminConstant.Catalog.CATEGORIES + "/" + cat).header("Authorization", auth()))
                .andExpect(status().isNotFound());

        long col = body(mvc.perform(jsonRequest(post(V1 + UrlAdminConstant.Catalog.COLLECTIONS),
                        Map.of("name", "BST " + tag())).header("Authorization", auth()))
                .andExpect(status().isCreated())).get("id").asLong();
        mvc.perform(delete(V1 + UrlAdminConstant.Catalog.COLLECTIONS + "/" + col).header("Authorization", auth()))
                .andExpect(status().isNoContent());

        long attr = body(mvc.perform(jsonRequest(post(V1 + UrlAdminConstant.Catalog.ATTRIBUTES),
                        Map.of("name", "Màu " + tag(), "code", "color_" + tag(), "type", "SELECT",
                                "options", List.of("Đỏ", "Xanh"))).header("Authorization", auth()))
                .andExpect(status().isCreated())).get("id").asLong();
        mvc.perform(delete(V1 + UrlAdminConstant.Catalog.ATTRIBUTES + "/" + attr).header("Authorization", auth()))
                .andExpect(status().isNoContent());
    }

    // ------------------------------------------------------------------ Badge

    private String createTemplate(Map<String, Object> extra) throws Exception {
        String code = "T_" + tag();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", "Mẫu " + code);
        m.put("code", code);
        m.put("type", "IMAGE");
        m.put("badgeType", "CAMPAIGN");
        m.put("status", "ACTIVE");
        m.putAll(extra);
        return body(mvc.perform(jsonRequest(post(V1 + UrlAdminConstant.Badge.TEMPLATES), m)
                        .header("Authorization", auth()))
                .andExpect(status().isOk())).get("id").asText();
    }

    private Map<String, Object> flowBody(String status, String ruleType, Map<String, Object> rule, String templateId) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", "Flow " + tag());
        m.put("status", status);
        m.put("ruleType", ruleType);
        if (rule != null) {
            m.put("ruleConfig", rule);
        }
        m.put("channel", "ALL");
        m.put("templates", List.of(Map.of("badgeTemplateId", templateId, "priorityWeight", 1, "isPinned", false)));
        return m;
    }

    @Test
    void templateResponseReturnsMobileAssetsAndPatchCanClearThem() throws Exception {
        String id = createTemplate(Map.of("image", "/d.png", "imageMobile", "/m.png", "iconMobile", "/mi.svg",
                "assetMeta", Map.of("w", 64)));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get(V1 + UrlAdminConstant.Badge.TEMPLATES + "/" + id).header("Authorization", auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.imageMobile").value("/m.png"))
                .andExpect(jsonPath("$.data.iconMobile").value("/mi.svg"))
                .andExpect(jsonPath("$.data.assetMeta.w").value(64));

        mvc.perform(jsonRequest(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .patch(V1 + UrlAdminConstant.Badge.TEMPLATES + "/" + id),
                        Map.of("imageMobile", "", "assetMeta", Map.of())).header("Authorization", auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.imageMobile").doesNotExist())
                .andExpect(jsonPath("$.data.assetMeta").doesNotExist())
                .andExpect(jsonPath("$.data.image").value("/d.png"));
    }

    @Test
    void activeFlowNeedsRealScopeAndPickerPayloadIsNormalized() throws Exception {
        String tpl = createTemplate(Map.of());
        // Scope rỗng + ACTIVE -> 400 BADGE_SCOPE_INVALID
        mvc.perform(jsonRequest(post(V1 + UrlAdminConstant.Badge.FLOW), flowBody("ACTIVE", "CATEGORY", null, tpl))
                        .header("Authorization", auth()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("BADGE_SCOPE_INVALID"));
        // id không tồn tại -> 400
        mvc.perform(jsonRequest(post(V1 + UrlAdminConstant.Badge.FLOW), flowBody("ACTIVE", "CATEGORY",
                        Map.of("categories", List.of(Map.of("categoryId", 987654321L, "name", "x"))), tpl))
                        .header("Authorization", auth()))
                .andExpect(status().isBadRequest());
        // Payload picker hợp lệ -> lưu categoryIds + giữ categories
        long cat = createCategory("Nhẫn", null);
        mvc.perform(jsonRequest(post(V1 + UrlAdminConstant.Badge.FLOW), flowBody("ACTIVE", "CATEGORY",
                        Map.of("categories", List.of(Map.of("categoryId", cat, "name", "Nhẫn"))), tpl))
                        .header("Authorization", auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ruleConfig.categoryIds[0]").value(cat))
                .andExpect(jsonPath("$.data.ruleConfig.categories[0].categoryId").value(cat))
                .andExpect(jsonPath("$.data.displayStatus").value("RUNNING"));
        // Nháp được phép scope rỗng
        mvc.perform(jsonRequest(post(V1 + UrlAdminConstant.Badge.FLOW), flowBody("DRAFT", "CATEGORY", null, tpl))
                        .header("Authorization", auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.displayStatus").value("DRAFT"));
    }

    @Test
    void runningFlowCannotBeDeletedUntilPausedAndTemplateInUseCannotBeDeleted() throws Exception {
        String tpl = createTemplate(Map.of("type", "TEXT"));
        String flowId = body(mvc.perform(jsonRequest(post(V1 + UrlAdminConstant.Badge.FLOW),
                        flowBody("ACTIVE", "ALL", null, tpl)).header("Authorization", auth()))
                .andExpect(status().isOk())).get("id").asText();

        mvc.perform(delete(V1 + UrlAdminConstant.Badge.FLOW + "/" + flowId).header("Authorization", auth()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].code").value("BADGE_FLOW_LOCKED"));

        mvc.perform(jsonRequest(put(V1 + UrlAdminConstant.Badge.FLOW + "/" + flowId),
                        flowBody("INACTIVE", "ALL", null, tpl)).header("Authorization", auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.displayStatus").value("PAUSED"));

        // Template còn nằm trong flow -> 409 BADGE_IN_USE (cả khi đã INACTIVE hóa template).
        mvc.perform(jsonRequest(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .patch(V1 + UrlAdminConstant.Badge.TEMPLATES + "/" + tpl), Map.of("status", "INACTIVE"))
                        .header("Authorization", auth()))
                .andExpect(status().isOk());
        mvc.perform(delete(V1 + UrlAdminConstant.Badge.TEMPLATES + "/" + tpl).header("Authorization", auth()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].code").value("BADGE_IN_USE"));

        // Xoá flow (đang PAUSED) rồi xoá template -> OK
        mvc.perform(delete(V1 + UrlAdminConstant.Badge.FLOW + "/" + flowId).header("Authorization", auth()))
                .andExpect(status().isOk());
        mvc.perform(delete(V1 + UrlAdminConstant.Badge.TEMPLATES + "/" + tpl).header("Authorization", auth()))
                .andExpect(status().isOk());
    }
}
