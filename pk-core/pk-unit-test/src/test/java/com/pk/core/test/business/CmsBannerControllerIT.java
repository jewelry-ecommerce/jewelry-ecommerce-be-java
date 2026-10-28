package com.pk.core.test.business;

import com.fasterxml.jackson.databind.JsonNode;
import com.pk.core.business.repository.BannerPlacementRepo;
import com.pk.core.model.constant.UrlConstant;
import com.pk.core.model.constant.admin.UrlAdminConstant;
import com.pk.core.model.entity.BannerPlacementEntity;
import com.pk.core.test.common.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** CMS storefront (công khai, chỉ PUBLISHED) và Banner (admin CRUD + render công khai theo mã vị trí). */
class CmsBannerControllerIT extends IntegrationTestBase {

    private static final String BANNERS = UrlConstant.Common.API + UrlConstant.Common.VERSION + UrlAdminConstant.Banner.BANNERS;

    @Autowired BannerPlacementRepo placements;

    private static String tag() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    // ------------------------------------------------------------------ CMS storefront

    @Test
    void publishedCmsPageIsPublicAndDraftIs404() throws Exception {
        String admin = bearer(adminAccessToken());
        String slug = "trang-" + tag();
        String draftSlug = "nhap-" + tag();

        mvc.perform(jsonRequest(post(UrlConstant.Common.API + UrlConstant.Common.VERSION + UrlAdminConstant.Cms.PAGES), Map.of("slug", slug, "name", "Trang công khai",
                        "status", "PUBLISHED",
                        "blocks", List.of(Map.of("type", "INFO_CARDS", "sortOrder", 0,
                                "config", Map.of("header", Map.of("title", "T"), "items", List.of(Map.of("imageUrl", "/a.jpg", "title", "A", "actionText", "Xem", "actionUrl", "/x")))))))
                        .header("Authorization", admin))
                .andExpect(status().is2xxSuccessful());
        mvc.perform(jsonRequest(post(UrlConstant.Common.API + UrlConstant.Common.VERSION + UrlAdminConstant.Cms.PAGES), Map.of("slug", draftSlug, "name", "Bản nháp",
                        "status", "DRAFT")).header("Authorization", admin))
                .andExpect(status().is2xxSuccessful());

        mvc.perform(get(UrlConstant.Common.API + UrlConstant.Common.VERSION + UrlConstant.Cms.PAGES + "/" + slug))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.slug").value(slug))
                .andExpect(jsonPath("$.data.blocks.length()").value(1))
                .andExpect(jsonPath("$.data.name").value("Trang công khai"))
                .andExpect(jsonPath("$.data.blocks[0].type").value("INFO_CARDS"))
                .andExpect(jsonPath("$.data.blocks[0].config.items[0].title").value("A"));
        mvc.perform(get(UrlConstant.Common.API + UrlConstant.Common.VERSION + UrlConstant.Cms.PAGES + "/" + draftSlug)).andExpect(status().isNotFound());
        mvc.perform(get(UrlConstant.Common.API + UrlConstant.Common.VERSION + UrlConstant.Cms.PAGES + "/khong-co-" + tag())).andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------ Banner admin

    @Test
    void adminBannerEndpointsRequireAdminRole() throws Exception {
        mvc.perform(get(BANNERS)).andExpect(status().isUnauthorized());
        mvc.perform(get(BANNERS).header("Authorization", bearer(customerAccessToken())))
                .andExpect(status().isForbidden());
    }

    @Test
    void createWithoutInternalNameReturns400() throws Exception {
        mvc.perform(jsonRequest(post(BANNERS), Map.of("title", "Không tên"))
                        .header("Authorization", bearer(adminAccessToken())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field=='internalName')]").exists());
    }

    @Test
    void createWithInvalidEnumReturns400() throws Exception {
        mvc.perform(jsonRequest(post(BANNERS), Map.of("internalName", "x", "mediaType", "AUDIO"))
                        .header("Authorization", bearer(adminAccessToken())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field=='mediaType')]").exists());
    }

    @Test
    void bannerCrudAndRenderFlow() throws Exception {
        String admin = bearer(adminAccessToken());
        String code = "IT_" + tag().toUpperCase();
        BannerPlacementEntity placement = new BannerPlacementEntity();
        placement.setCode(code);
        placement.setName("Vị trí test");
        placement.setDisplayType("CAROUSEL");
        placements.create(placement);

        // Tạo (mặc định DRAFT) - chưa lên render.
        JsonNode created = body(mvc.perform(jsonRequest(post(BANNERS), Map.of(
                        "internalName", "Banner IT " + code, "title", "Xin chào", "placementCode", code.toLowerCase(),
                        "overlayOpacity", 0.3,
                        "actions", List.of(Map.of("actionType", "URL", "actionTarget", "/sale", "ctaText", "Mua"))))
                        .header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.placementCode").value(code))
                .andExpect(jsonPath("$.data.actions[0].actionType").value("URL")));
        String id = created.get("id").asText();

        mvc.perform(get(UrlConstant.Common.API + UrlConstant.Common.VERSION + UrlConstant.Banner.PLACEMENTS_BY_CODE + "/" + code + "/render"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.code").value(code))
                .andExpect(jsonPath("$.data.displayType").value("CAROUSEL"))
                .andExpect(jsonPath("$.data.banners.length()").value(0));

        // PATCH một phần: chỉ đổi status -> lên render, các field khác giữ nguyên.
        mvc.perform(jsonRequest(patch(BANNERS + "/" + id), Map.of("status", "ACTIVE")).header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.title").value("Xin chào"));
        mvc.perform(get(UrlConstant.Common.API + UrlConstant.Common.VERSION + UrlConstant.Banner.PLACEMENTS_BY_CODE + "/" + code + "/render"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.banners.length()").value(1))
                .andExpect(jsonPath("$.data.banners[0].id").value(id));

        // Chi tiết + lọc danh sách.
        mvc.perform(get(BANNERS + "/" + id).header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.internalName").value("Banner IT " + code));
        mvc.perform(get(BANNERS).param("internalName", code).param("status", "ACTIVE").param("actionType", "LINK")
                        .header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(id))
                .andExpect(jsonPath("$.data.page").value(1));

        // Xoá mềm: 200 + {success,message}, sau đó 404 và biến mất khỏi render.
        mvc.perform(delete(BANNERS + "/" + id).header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.success").value(true));
        mvc.perform(get(BANNERS + "/" + id).header("Authorization", admin)).andExpect(status().isNotFound());
        mvc.perform(get(UrlConstant.Common.API + UrlConstant.Common.VERSION + UrlConstant.Banner.PLACEMENTS_BY_CODE + "/" + code + "/render"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.banners.length()").value(0));
    }

    @Test
    void renderUnknownPlacementIs404() throws Exception {
        mvc.perform(get(UrlConstant.Common.API + UrlConstant.Common.VERSION + UrlConstant.Banner.PLACEMENTS_BY_CODE + "/KHONG_CO_" + tag() + "/render"))
                .andExpect(status().isNotFound());
    }

    @Test
    void createWithUnknownPlacementIs404() throws Exception {
        mvc.perform(jsonRequest(post(BANNERS), Map.of("internalName", "x", "placementCode", "KHONG_CO_" + tag()))
                        .header("Authorization", bearer(adminAccessToken())))
                .andExpect(status().isNotFound());
    }
}
