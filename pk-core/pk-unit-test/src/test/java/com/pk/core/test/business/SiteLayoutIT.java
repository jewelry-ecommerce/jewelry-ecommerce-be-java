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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Header (thanh thông báo) / footer / logo của site: CRUD admin, quy tắc trùng lịch, singleton footer, dữ liệu công khai. */
class SiteLayoutIT extends IntegrationTestBase {

    private static final String V1 = UrlConstant.Common.API + UrlConstant.Common.VERSION;
    private static final String HEADERS = V1 + UrlAdminConstant.Cms.HEADERS;
    private static final String FOOTER = V1 + UrlAdminConstant.Cms.FOOTER;
    private static final String LOGOS = V1 + UrlAdminConstant.Cms.LOGOS;
    private static final String PUBLIC = V1 + UrlConstant.Cms.SITE_LAYOUT;

    private Map<String, Object> header(String name, Instant start, Instant end, String status) {
        Map<String, Object> m = new HashMap<>(Map.of("bannerName", name, "bannerDisplayStyle", "TEXT",
                "bannerContent", "Miễn phí vận chuyển " + name, "bannerBgColor", "#111111", "bannerTextColor", "#ffffff",
                "bannerStartTime", start.toString(), "status", status));
        if (end != null) {
            m.put("bannerEndTime", end.toString());
        }
        return m;
    }

    @Test
    void headerLifecycleScheduleConflictAndPublicRender() throws Exception {
        String admin = bearer(adminAccessToken());
        String t = UUID.randomUUID().toString().substring(0, 6);
        Instant now = Instant.now();

        // Bản nháp không gây xung đột và không hiển thị công khai.
        long draft = body(mvc.perform(jsonRequest(post(HEADERS), header("Nháp " + t, now.minus(1, ChronoUnit.DAYS), null, "DRAFT"))
                        .header("Authorization", admin)).andExpect(status().isCreated())).get("id").asLong();

        // Bật header A (dọn các header ACTIVE có sẵn nếu DB dùng chung).
        Map<String, Object> aBody = header("A " + t, now.minus(1, ChronoUnit.DAYS), null, "ACTIVE");
        aBody.put("confirmReplaceActive", true);
        JsonNode a = body(mvc.perform(jsonRequest(post(HEADERS), aBody).header("Authorization", admin))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.displayStatus").value("ACTIVE")));
        long aId = a.get("id").asLong();

        // Header B ACTIVE trùng lịch -> 409; xác nhận thay thế -> A bị DEACTIVE, B hiển thị.
        mvc.perform(jsonRequest(post(HEADERS), header("B " + t, now.minus(1, ChronoUnit.HOURS), null, "ACTIVE"))
                        .header("Authorization", admin))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].code").value("HEADER_SCHEDULE_CONFLICT"))
                .andExpect(jsonPath("$.dependencies[0].id").value(String.valueOf(aId)))
                .andExpect(jsonPath("$.dependencies[0].bannerName").value("A " + t))
                .andExpect(jsonPath("$.dependencies[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.dependencies[0].bannerStartTime").exists());
        Map<String, Object> bBody = header("B " + t, now.minus(1, ChronoUnit.HOURS), null, "ACTIVE");
        bBody.put("confirmReplaceActive", true);
        long bId = body(mvc.perform(jsonRequest(post(HEADERS), bBody).header("Authorization", admin))
                .andExpect(status().isCreated())).get("id").asLong();
        mvc.perform(get(HEADERS + "/" + aId).header("Authorization", admin))
                .andExpect(jsonPath("$.data.status").value("DEACTIVE"));

        // Công khai (không cần token): thấy B.
        mvc.perform(get(PUBLIC)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.header.id").value(bId))
                .andExpect(jsonPath("$.data.header.bannerContent").value("Miễn phí vận chuyển B " + t));

        // Đổi trạng thái B -> DEACTIVE rồi bật lại A không cần xác nhận; lên lịch trong tương lai thì displayStatus = SCHEDULED.
        mvc.perform(jsonRequest(patch(HEADERS + "/" + bId + "/status"), Map.of("status", "DEACTIVE")).header("Authorization", admin))
                .andExpect(status().isOk());
        mvc.perform(jsonRequest(patch(HEADERS + "/" + aId + "/status"), Map.of("status", "SCHEDULED")).header("Authorization", admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.displayStatus").value("ACTIVE"));
        mvc.perform(jsonRequest(patch(HEADERS + "/" + aId + "/status"), Map.of("status", "DEACTIVE")).header("Authorization", admin))
                .andExpect(status().isOk());
        long future = body(mvc.perform(jsonRequest(post(HEADERS), header("Tương lai " + t, now.plus(30, ChronoUnit.DAYS), now.plus(31, ChronoUnit.DAYS), "SCHEDULED"))
                        .header("Authorization", admin))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.displayStatus").value("SCHEDULED"))).get("id").asLong();

        // PATCH một phần giữ nguyên field còn lại; xoá hạn bằng chuỗi rỗng; end <= start -> 400.
        mvc.perform(jsonRequest(patch(HEADERS + "/" + future), Map.of("bannerName", "Tương lai sửa " + t)).header("Authorization", admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.bannerContent").value("Miễn phí vận chuyển Tương lai " + t));
        mvc.perform(jsonRequest(patch(HEADERS + "/" + future), Map.of("bannerEndTime", now.plus(29, ChronoUnit.DAYS).toString()))
                        .header("Authorization", admin)).andExpect(status().isBadRequest());
        mvc.perform(jsonRequest(patch(HEADERS + "/" + future), Map.of("bannerEndTime", "")).header("Authorization", admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.bannerEndTime").doesNotExist());

        // Danh sách: lọc theo status + search + phân trang.
        JsonNode list = body(mvc.perform(get(HEADERS).param("search", t).param("take", "2").header("Authorization", admin))
                .andExpect(status().isOk()));
        assertEquals(2, list.get("content").size());
        assertTrue(list.get("totalElements").asLong() >= 4);
        JsonNode drafts = body(mvc.perform(get(HEADERS).param("status", "DRAFT").param("search", t).header("Authorization", admin)));
        assertEquals(1, drafts.get("totalElements").asLong());

        // Dữ liệu sai -> 400 (không 201/500).
        mvc.perform(jsonRequest(post(HEADERS), Map.of("bannerName", "X")).header("Authorization", admin)).andExpect(status().isBadRequest());
        mvc.perform(jsonRequest(post(HEADERS), header("Sai giờ", now, null, "DRAFT")).header("Authorization", admin)).andExpect(status().isCreated());
        Map<String, Object> badTime = header("Sai", now, null, "DRAFT");
        badTime.put("bannerStartTime", "ngày mai");
        mvc.perform(jsonRequest(post(HEADERS), badTime).header("Authorization", admin)).andExpect(status().isBadRequest());
        Map<String, Object> badStyle = header("Sai", now, null, "DRAFT");
        badStyle.put("bannerDisplayStyle", "VIDEO");
        mvc.perform(jsonRequest(post(HEADERS), badStyle).header("Authorization", admin)).andExpect(status().isBadRequest());

        // Xoá mềm: sau đó 404.
        mvc.perform(delete(HEADERS + "/" + draft).header("Authorization", admin)).andExpect(status().isOk());
        mvc.perform(get(HEADERS + "/" + draft).header("Authorization", admin)).andExpect(status().isNotFound());
        mvc.perform(delete(HEADERS + "/" + draft).header("Authorization", admin)).andExpect(status().isNotFound());

        // Phân quyền.
        mvc.perform(get(HEADERS)).andExpect(status().isUnauthorized());
        mvc.perform(get(HEADERS).header("Authorization", bearer(customerAccessToken()))).andExpect(status().isForbidden());
    }

    @Test
    void footerIsSingletonAndKeepsOrderAndGeneratesIds() throws Exception {
        String admin = bearer(adminAccessToken());
        Map<String, Object> req = Map.of(
                "copyrightText", "© 2026 PK Jewelry",
                "footerLogoUrl", "/files/logo.png",
                "socialLinks", List.of(
                        Map.of("platform", "Zalo", "displayName", "Zalo OA", "url", "https://zalo.me/pk", "orderIndex", 2),
                        Map.of("platform", "Facebook", "displayName", "Facebook", "url", "https://fb.com/pk", "orderIndex", 1)),
                "menuColumns", List.of(Map.of("title", "Hỗ trợ", "orderIndex", 1, "items", List.of(
                        Map.of("label", "Đổi trả", "url", "/doi-tra", "orderIndex", 2),
                        Map.of("label", "Bảo hành", "url", "/bao-hanh", "orderIndex", 1)))));
        JsonNode saved = body(mvc.perform(jsonRequest(put(FOOTER), req).header("Authorization", admin)).andExpect(status().isOk()));
        assertNotNull(saved.get("id").asText());
        assertEquals("Facebook", saved.get("socialLinks").get(0).get("platform").asText(), "sắp theo orderIndex");
        assertTrue(saved.get("socialLinks").get(0).get("id").asText().length() > 8, "id tự sinh");
        assertEquals("Bảo hành", saved.get("menuColumns").get(0).get("items").get(0).get("label").asText());

        // Lưu lần hai vẫn cùng một dòng (singleton) và GET trả đúng nội dung.
        JsonNode again = body(mvc.perform(jsonRequest(put(FOOTER), Map.of("copyrightText", "© 2027", "newsletterHeading", "  Đăng ký nhận tin ", "newsletterSubHeading", "Ưu đãi mới nhất",
                "socialLinks", List.of(), "menuColumns", List.of()))
                .header("Authorization", admin)).andExpect(status().isOk()));
        assertEquals(saved.get("id").asLong(), again.get("id").asLong());
        JsonNode read = body(mvc.perform(get(FOOTER).header("Authorization", admin)).andExpect(status().isOk()));
        assertEquals("© 2027", read.get("copyrightText").asText());
        assertEquals(0, read.get("socialLinks").size());
        assertEquals("Đăng ký nhận tin", read.get("newsletterHeading").asText());
        assertEquals("Ưu đãi mới nhất", read.get("newsletterSubHeading").asText());

        // Công khai thấy footer đã lưu.
        mvc.perform(get(PUBLIC)).andExpect(status().isOk()).andExpect(jsonPath("$.data.footer.copyrightText").value("© 2027"))
                .andExpect(jsonPath("$.data.footer.newsletterHeading").value("Đăng ký nhận tin"))
                .andExpect(jsonPath("$.data.footer.newsletterSubHeading").value("Ưu đãi mới nhất"));

        // Nền tảng sai / thiếu url -> 400.
        mvc.perform(jsonRequest(put(FOOTER), Map.of("socialLinks", List.of(Map.of("platform", "MySpace", "displayName", "x", "url", "https://x"))))
                .header("Authorization", admin)).andExpect(status().isBadRequest());
        mvc.perform(jsonRequest(put(FOOTER), Map.of("menuColumns", List.of(Map.of("title", "Cột", "items", List.of(Map.of("label", "Không url"))))))
                .header("Authorization", admin)).andExpect(status().isBadRequest());
        mvc.perform(jsonRequest(put(FOOTER), Map.of()).header("Authorization", bearer(customerAccessToken()))).andExpect(status().isForbidden());
    }

    @Test
    void logoOneActivePerTypeArchiveAndPublic() throws Exception {
        String admin = bearer(adminAccessToken());
        String t = UUID.randomUUID().toString().substring(0, 6);
        long a = body(mvc.perform(jsonRequest(post(LOGOS), Map.of("name", "Logo A " + t, "type", "FAVICON", "status", "ACTIVE",
                        "logoUrl", "/files/a.png")).header("Authorization", admin)).andExpect(status().isCreated())).get("id").asLong();
        long b = body(mvc.perform(jsonRequest(post(LOGOS), Map.of("name", "Logo B " + t, "type", "FAVICON", "status", "DRAFT",
                        "logoUrl", "/files/b.png")).header("Authorization", admin)).andExpect(status().isCreated())).get("id").asLong();
        mvc.perform(jsonRequest(patch(LOGOS + "/" + b + "/status"), Map.of("status", "ACTIVE")).header("Authorization", admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("ACTIVE"));
        mvc.perform(get(LOGOS + "/" + a).header("Authorization", admin)).andExpect(jsonPath("$.data.status").value("DEACTIVE"));
        mvc.perform(get(PUBLIC)).andExpect(jsonPath("$.data.logos.FAVICON.id").value(b));

        JsonNode byType = body(mvc.perform(get(LOGOS).param("type", "FAVICON").param("search", t).header("Authorization", admin)));
        assertEquals(2, byType.get("totalElements").asLong());
        JsonNode active = body(mvc.perform(get(LOGOS).param("status", "ACTIVE").param("search", t).header("Authorization", admin)));
        assertEquals(1, active.get("totalElements").asLong());

        // Sửa một phần giữ nguyên field khác; thiếu logoUrl khi tạo hoặc type sai -> 400.
        mvc.perform(jsonRequest(patch(LOGOS + "/" + b), Map.of("name", "Logo B2 " + t)).header("Authorization", admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.logoUrl").value("/files/b.png"));
        mvc.perform(jsonRequest(post(LOGOS), Map.of("name", "thiếu url")).header("Authorization", admin)).andExpect(status().isBadRequest());
        mvc.perform(jsonRequest(post(LOGOS), Map.of("name", "x", "type", "BANNER", "logoUrl", "/f.png")).header("Authorization", admin))
                .andExpect(status().isBadRequest());

        // Lưu trữ (xoá mềm): biến mất khỏi danh sách/detail/công khai.
        mvc.perform(patch(LOGOS + "/" + b + "/archive").header("Authorization", admin)).andExpect(status().isOk());
        mvc.perform(get(LOGOS + "/" + b).header("Authorization", admin)).andExpect(status().isNotFound());
        JsonNode pub = body(mvc.perform(get(PUBLIC)).andExpect(status().isOk()));
        assertNull(pub.get("logos").get("FAVICON"), "logo đã archive không còn công khai");
    }
}
