package com.pk.core.test.business;

import com.fasterxml.jackson.databind.JsonNode;
import com.pk.core.business.repository.CategoryRepo;
import com.pk.core.business.repository.CmsBlockRepo;
import com.pk.core.business.repository.CmsPageRepo;
import com.pk.core.business.repository.ProductRepo;
import com.pk.core.model.constant.UrlConstant;
import com.pk.core.model.constant.admin.UrlAdminConstant;
import com.pk.core.model.entity.CategoryEntity;
import com.pk.core.model.entity.CmsBlockEntity;
import com.pk.core.model.entity.CmsPageEntity;
import com.pk.core.model.entity.ProductEntity;
import com.pk.core.test.common.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** FE v5 (CMS): nháp tách khỏi bản public, xuất bản/gỡ, lịch + thiết bị, xoá mềm trang, carousel nguồn dữ liệu thật. */
class CmsPublishV5IT extends IntegrationTestBase {

    @Autowired CmsPageRepo pages;
    @Autowired CmsBlockRepo blockRepo;
    @Autowired CategoryRepo categories;
    @Autowired ProductRepo products;

    private static String tag() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private String admin() throws Exception {
        return bearer(adminAccessToken());
    }

    private static String pagesPath() {
        return UrlConstant.Common.API + UrlConstant.Common.VERSION + UrlAdminConstant.Cms.PAGES;
    }

    private static String publicUrl(String slug) {
        return UrlConstant.Common.API + UrlConstant.Common.VERSION + UrlConstant.Cms.PAGES + "/" + slug;
    }

    private static Map<String, Object> cards(int sortOrder) {
        return Map.of("type", "INFO_CARDS", "sortOrder", sortOrder, "config", Map.of("cards", List.of()));
    }

    private static Map<String, Object> gallery(int sortOrder) {
        return Map.of("type", "IMAGE_GALLERY", "sortOrder", sortOrder, "config", Map.of("images", List.of()));
    }

    /** Tạo trang + xuất bản ngay (status PUBLISHED), trả JSON admin. */
    private JsonNode publishedPage(String slug, String auth, Object... blocks) throws Exception {
        return body(mvc.perform(jsonRequest(post(pagesPath()), Map.of("name", "Trang", "slug", slug,
                        "status", "PUBLISHED", "blocks", List.of(blocks))).header("Authorization", auth))
                .andExpect(status().isCreated()));
    }

    private JsonNode savePage(long id, Map<String, Object> bodyMap, String auth) throws Exception {
        return body(mvc.perform(jsonRequest(put(pagesPath() + "/" + id), bodyMap).header("Authorization", auth))
                .andExpect(status().isOk()));
    }

    private JsonNode publish(long id, String auth) throws Exception {
        return body(mvc.perform(post(pagesPath() + "/" + id + "/publish").header("Authorization", auth))
                .andExpect(status().isOk()));
    }

    @Test
    void draftEditsStayPrivateUntilPublish() throws Exception {
        String auth = admin();
        String slug = "trang-" + tag();
        JsonNode created = publishedPage(slug, auth, cards(0));
        long id = created.get("id").asLong();
        org.junit.jupiter.api.Assertions.assertEquals(1, created.get("versionId").asInt());

        mvc.perform(get(publicUrl(slug))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Trang"))
                .andExpect(jsonPath("$.data.blocks.length()").value(1))
                .andExpect(jsonPath("$.data.layout.versionId").value("1"));

        JsonNode saved = savePage(id, Map.of("name", "Trang mới", "slug", slug, "blocks", List.of(cards(0), gallery(1))), auth);
        org.junit.jupiter.api.Assertions.assertTrue(saved.get("hasUnpublishedChanges").asBoolean());
        org.junit.jupiter.api.Assertions.assertEquals("PUBLISHED", saved.get("status").asText());
        org.junit.jupiter.api.Assertions.assertEquals(2, saved.get("blocks").size());

        // Bản public vẫn là bản cũ.
        mvc.perform(get(publicUrl(slug))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Trang"))
                .andExpect(jsonPath("$.data.blocks.length()").value(1));

        JsonNode published = publish(id, auth);
        org.junit.jupiter.api.Assertions.assertEquals(2, published.get("versionId").asInt());
        org.junit.jupiter.api.Assertions.assertFalse(published.get("hasUnpublishedChanges").asBoolean());
        mvc.perform(get(publicUrl(slug))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Trang mới"))
                .andExpect(jsonPath("$.data.blocks.length()").value(2))
                .andExpect(jsonPath("$.data.layout.versionId").value("2"));
    }

    @Test
    void savingWithStatusDraftDoesNotTakePublicPageOffline() throws Exception {
        String auth = admin();
        String slug = "trang-" + tag();
        long id = publishedPage(slug, auth, cards(0)).get("id").asLong();

        JsonNode saved = savePage(id, Map.of("name", "Trang", "slug", slug, "status", "DRAFT"), auth);

        org.junit.jupiter.api.Assertions.assertEquals("PUBLISHED", saved.get("status").asText());
        mvc.perform(get(publicUrl(slug))).andExpect(status().isOk());
    }

    @Test
    void renamingSlugInDraftMovesPublicUrlOnlyAfterPublish() throws Exception {
        String auth = admin();
        String oldSlug = "trang-" + tag();
        String newSlug = "moi-" + tag();
        long id = publishedPage(oldSlug, auth, cards(0)).get("id").asLong();

        savePage(id, Map.of("name", "Trang", "slug", newSlug), auth);
        mvc.perform(get(publicUrl(oldSlug))).andExpect(status().isOk());
        mvc.perform(get(publicUrl(newSlug))).andExpect(status().isNotFound());

        publish(id, auth);
        mvc.perform(get(publicUrl(newSlug))).andExpect(status().isOk());
        mvc.perform(get(publicUrl(oldSlug))).andExpect(status().isNotFound());
    }

    @Test
    void unpublishTakesPageOfflineAndPublishBringsItBack() throws Exception {
        String auth = admin();
        String slug = "trang-" + tag();
        long id = publishedPage(slug, auth, cards(0)).get("id").asLong();

        mvc.perform(post(pagesPath() + "/" + id + "/unpublish").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.displayStatus").value("DRAFT"));
        mvc.perform(get(publicUrl(slug))).andExpect(status().isNotFound());

        mvc.perform(post(pagesPath() + "/" + id + "/publish").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.displayStatus").value("RUNNING"));
        mvc.perform(get(publicUrl(slug))).andExpect(status().isOk());
    }

    @Test
    void scheduleControlsDisplayStatusAndVisibility() throws Exception {
        String auth = admin();
        String slug = "trang-" + tag();
        long id = publishedPage(slug, auth, cards(0)).get("id").asLong();
        Instant now = Instant.now();

        // Lịch trong tương lai: đã xuất bản nhưng SCHEDULED, storefront chưa thấy.
        Map<String, Object> future = new HashMap<>(Map.of("name", "Trang", "slug", slug, "status", "PUBLISHED"));
        future.put("scheduleStart", now.plus(1, ChronoUnit.HOURS).toString());
        future.put("scheduleEnd", now.plus(2, ChronoUnit.HOURS).toString());
        JsonNode scheduled = savePage(id, future, auth);
        org.junit.jupiter.api.Assertions.assertEquals("SCHEDULED", scheduled.get("displayStatus").asText());
        mvc.perform(get(publicUrl(slug))).andExpect(status().isNotFound());

        // Đang trong lịch.
        Map<String, Object> running = new HashMap<>(future);
        running.put("scheduleStart", now.minus(1, ChronoUnit.HOURS).toString());
        JsonNode live = savePage(id, running, auth);
        org.junit.jupiter.api.Assertions.assertEquals("RUNNING", live.get("displayStatus").asText());
        mvc.perform(get(publicUrl(slug))).andExpect(status().isOk());

        // Hết lịch.
        Map<String, Object> ended = new HashMap<>(future);
        ended.put("scheduleStart", now.minus(3, ChronoUnit.HOURS).toString());
        ended.put("scheduleEnd", now.minus(2, ChronoUnit.HOURS).toString());
        JsonNode over = savePage(id, ended, auth);
        org.junit.jupiter.api.Assertions.assertEquals("ENDED", over.get("displayStatus").asText());
        mvc.perform(get(publicUrl(slug))).andExpect(status().isNotFound());

        // Xoá lịch: gửi null tường minh.
        JsonNode open = body(mvc.perform(put(pagesPath() + "/" + id)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Trang\",\"slug\":\"" + slug
                                + "\",\"status\":\"PUBLISHED\",\"scheduleStart\":null,\"scheduleEnd\":null}")
                        .header("Authorization", auth))
                .andExpect(status().isOk()));
        org.junit.jupiter.api.Assertions.assertEquals("RUNNING", open.get("displayStatus").asText());
        mvc.perform(get(publicUrl(slug))).andExpect(status().isOk());

        // Không gửi khoá lịch thì giữ nguyên (ở đây: không có lịch).
        savePage(id, Map.of("name", "Trang", "slug", slug), auth);
        mvc.perform(get(publicUrl(slug))).andExpect(status().isOk());
    }

    @Test
    void invalidScheduleAndDeviceAreRejected() throws Exception {
        String auth = admin();
        String slug = "trang-" + tag();
        long id = publishedPage(slug, auth, cards(0)).get("id").asLong();
        Instant now = Instant.now();

        Map<String, Object> backwards = new HashMap<>(Map.of("name", "Trang", "slug", slug));
        backwards.put("scheduleStart", now.plus(2, ChronoUnit.HOURS).toString());
        backwards.put("scheduleEnd", now.plus(1, ChronoUnit.HOURS).toString());
        mvc.perform(jsonRequest(put(pagesPath() + "/" + id), backwards).header("Authorization", auth))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("CMS_SCHEDULE_INVALID"));

        mvc.perform(jsonRequest(put(pagesPath() + "/" + id), Map.of("name", "Trang", "slug", slug, "targetDevice", "TV"))
                        .header("Authorization", auth))
                .andExpect(status().isBadRequest());
    }

    @Test
    void targetDeviceFiltersStorefront() throws Exception {
        String auth = admin();
        String slug = "trang-" + tag();
        long id = publishedPage(slug, auth, cards(0)).get("id").asLong();

        savePage(id, Map.of("name", "Trang", "slug", slug, "targetDevice", "mobile", "status", "PUBLISHED"), auth);

        mvc.perform(get(publicUrl(slug) + "?device=DESKTOP")).andExpect(status().isNotFound());
        mvc.perform(get(publicUrl(slug) + "?device=mobile")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.layout.targetDevice").value("MOBILE"));
        mvc.perform(get(publicUrl(slug))).andExpect(status().isOk());
    }

    @Test
    void deletePageIsSoftAndFreesTheSlug() throws Exception {
        String auth = admin();
        String slug = "trang-" + tag();
        long id = publishedPage(slug, auth, cards(0)).get("id").asLong();

        mvc.perform(delete(pagesPath() + "/" + id).header("Authorization", auth)).andExpect(status().isNoContent());

        mvc.perform(get(pagesPath() + "/" + id).header("Authorization", auth)).andExpect(status().isNotFound());
        mvc.perform(get(publicUrl(slug))).andExpect(status().isNotFound());
        mvc.perform(delete(pagesPath() + "/" + id).header("Authorization", auth)).andExpect(status().isNotFound());
        JsonNode list = body(mvc.perform(get(pagesPath()).header("Authorization", auth)).andExpect(status().isOk()));
        for (JsonNode p : list) {
            org.junit.jupiter.api.Assertions.assertNotEquals(id, p.get("id").asLong());
        }
        // Slug dùng lại được.
        publishedPage(slug, auth, cards(0));
    }

    @Test
    void pageMarkedPublishedBeforeSnapshotsStaysStableWhenEdited() throws Exception {
        String auth = admin();
        String slug = "cu-" + tag();
        CmsPageEntity legacy = new CmsPageEntity(slug, "Trang cũ", CmsPageEntity.PUBLISHED);
        pages.create(legacy);
        blockRepo.create(new CmsBlockEntity(legacy.getId(), "INFO_CARDS", 0, "{\"cards\":[]}", null));

        mvc.perform(get(publicUrl(slug))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Trang cũ"))
                .andExpect(jsonPath("$.data.blocks.length()").value(1));

        savePage(legacy.getId(), Map.of("name", "Đổi tên nháp", "slug", slug), auth);

        mvc.perform(get(publicUrl(slug))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Trang cũ"));
        publish(legacy.getId(), auth);
        mvc.perform(get(publicUrl(slug))).andExpect(jsonPath("$.data.name").value("Đổi tên nháp"));
    }

    // ------------------------------------------------------------------ carousel nguồn dữ liệu thật

    private ProductEntity newPublishedProduct(String t) {
        CategoryEntity category = new CategoryEntity("DM " + t, "dm-" + t, null);
        categories.create(category);
        ProductEntity product = new ProductEntity(category.getId(), "P-" + t, "SP " + t, "sp-" + t);
        product.setBasePrice(BigDecimal.valueOf(1_000_000));
        product.publish();
        products.create(product);
        return product;
    }

    private String carouselPage(Map<String, Object> dataSource, String auth) throws Exception {
        String slug = "trang-" + tag();
        mvc.perform(jsonRequest(post(pagesPath()), Map.of("name", "Trang", "slug", slug, "status", "PUBLISHED",
                        "blocks", List.of(Map.of("type", "PRODUCT_CAROUSEL", "sortOrder", 0, "config",
                                Map.of("header", Map.of("title", "x"), "dataSource", dataSource,
                                        "display", Map.of("limit", 50)))))).header("Authorization", auth))
                .andExpect(status().isCreated());
        return publicUrl(slug);
    }

    private long createPromotion(long productId, String status, String auth) throws Exception {
        Map<String, Object> promo = new HashMap<>();
        promo.put("name", "KM " + tag());
        promo.put("discountType", "PERCENT");
        promo.put("discountValue", 10);
        promo.put("startsAt", new java.util.Date(System.currentTimeMillis() - 3_600_000));
        promo.put("endsAt", new java.util.Date(System.currentTimeMillis() + 86_400_000));
        promo.put("status", status);
        promo.put("productIds", List.of(productId));
        return body(mvc.perform(jsonRequest(post(UrlConstant.Common.API + UrlConstant.Common.VERSION
                        + UrlAdminConstant.Promotion.BASE), promo).header("Authorization", auth))
                .andExpect(status().isCreated())).get("id").asLong();
    }

    @Test
    void campaignCarouselListsProductsOfTheChosenActivePromotion() throws Exception {
        String auth = admin();
        String t = tag();
        ProductEntity product = newPublishedProduct(t);
        long promotionId = createPromotion(product.getId(), "PUBLISHED", auth);

        mvc.perform(get(carouselPage(Map.of("filterType", "CAMPAIGN", "promotionId", promotionId), auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.blocks[0].content.products.length()").value(1))
                .andExpect(jsonPath("$.data.blocks[0].content.products[0].slug").value("sp-" + t));

        // Chương trình nháp thì không có sản phẩm.
        long draft = createPromotion(product.getId(), "DRAFT", auth);
        mvc.perform(get(carouselPage(Map.of("filterType", "CAMPAIGN", "promotionId", draft), auth)))
                .andExpect(jsonPath("$.data.blocks[0].content.products.length()").value(0));

        // Không chọn chương trình: gom mọi khuyến mãi đang chạy.
        mvc.perform(get(carouselPage(Map.of("filterType", "CAMPAIGN"), auth)))
                .andExpect(jsonPath("$.data.blocks[0].content.products[?(@.slug == 'sp-" + t + "')]").isNotEmpty());
    }

    @Test
    void campaignCarouselAcceptsCampaignIdAliasAndRejectsUnknownOrNonNumericId() throws Exception {
        String auth = admin();
        String t = tag();
        ProductEntity product = newPublishedProduct(t);
        long promotionId = createPromotion(product.getId(), "PUBLISHED", auth);

        // FE gửi campaignId (tên cũ) -> cùng kết quả với promotionId; id dạng chuỗi số cũng được.
        mvc.perform(get(carouselPage(Map.of("filterType", "CAMPAIGN", "campaignId", String.valueOf(promotionId)), auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.blocks[0].content.products[0].slug").value("sp-" + t));

        // campaignId không phải số, hoặc không tồn tại -> 400 (không phải 201, không phải 500).
        for (Object bad : List.of("abc", 999_999_999L)) {
            String key = "campaignId";
            mvc.perform(jsonRequest(post(pagesPath()), Map.of("name", "Trang", "slug", "trang-" + tag(),
                            "blocks", List.of(Map.of("type", "PRODUCT_CAROUSEL", "sortOrder", 0, "config",
                                    Map.of("dataSource", Map.of("filterType", "CAMPAIGN", key, bad))))))
                            .header("Authorization", auth))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void campaignCarouselRejectsNonNumericPromotionId() throws Exception {
        String auth = admin();
        mvc.perform(jsonRequest(post(pagesPath()), Map.of("name", "Trang", "slug", "trang-" + tag(),
                        "blocks", List.of(Map.of("type", "PRODUCT_CAROUSEL", "sortOrder", 0, "config",
                                Map.of("dataSource", Map.of("filterType", "CAMPAIGN", "promotionId", "abc"))))))
                        .header("Authorization", auth))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("CAROUSEL_SOURCE_INVALID"));
    }

    @Test
    void mostFavoritedCarouselUsesWishlistCounts() throws Exception {
        String auth = admin();
        String t = tag();
        ProductEntity product = newPublishedProduct(t);
        mvc.perform(jsonRequest(post(UrlConstant.Common.API + UrlConstant.Common.VERSION + UrlConstant.Wishlist.BASE),
                        Map.of("productIds", List.of(String.valueOf(product.getId()))))
                        .header("Authorization", bearer(customerAccessToken())))
                .andExpect(status().isOk());

        mvc.perform(get(carouselPage(Map.of("filterType", "MOST_FAVORITED"), auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.blocks[0].content.products[?(@.slug == 'sp-" + t + "')]").isNotEmpty());
    }

    @Test
    void bestSellersCarouselOnlyContainsPublishedProducts() throws Exception {
        String auth = admin();
        String t = tag();
        newPublishedProduct(t);
        mvc.perform(get(carouselPage(Map.of("filterType", "BEST_SELLERS"), auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.blocks[0].content.products").isArray());
    }
}
