package com.pk.core.test.business;

import com.fasterxml.jackson.databind.JsonNode;
import com.pk.core.business.repository.BannerPlacementRepo;
import com.pk.core.business.repository.CategoryRepo;
import com.pk.core.business.repository.ProductRepo;
import com.pk.core.model.constant.UrlConstant;
import com.pk.core.model.constant.admin.UrlAdminConstant;
import com.pk.core.model.entity.BannerPlacementEntity;
import com.pk.core.model.entity.CategoryEntity;
import com.pk.core.model.entity.ProductEntity;
import com.pk.core.test.common.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Block CMS theo spec (config object, targetSegment) và nội dung storefront đã nạp sẵn cho BANNER/PRODUCT_CAROUSEL. */
class CmsBlocksControllerIT extends IntegrationTestBase {

    @Autowired BannerPlacementRepo placements;
    @Autowired CategoryRepo categories;
    @Autowired ProductRepo products;

    private static String tag() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private String admin() throws Exception {
        return bearer(adminAccessToken());
    }

    @Test
    void unknownBlockTypeAndBadConfigAreRejected() throws Exception {
        String slug = "trang-" + tag();
        mvc.perform(jsonRequest(post(UrlConstant.Common.API + UrlConstant.Common.VERSION + UrlAdminConstant.Cms.PAGES), Map.of("name", "Trang", "slug", slug,
                        "status", "DRAFT", "blocks", List.of(Map.of("type", "HERO", "sortOrder", 0, "config", Map.of()))))
                        .header("Authorization", admin()))
                .andExpect(status().isBadRequest());
        mvc.perform(jsonRequest(post(UrlConstant.Common.API + UrlConstant.Common.VERSION + UrlAdminConstant.Cms.PAGES), Map.of("name", "Trang", "slug", slug,
                        "status", "DRAFT", "blocks", List.of(Map.of("type", "BANNER", "sortOrder", 0, "config", Map.of()))))
                        .header("Authorization", admin()))
                .andExpect(status().isBadRequest());
        // Lỗi block không để lại trang dở dang: tạo lại cùng slug với block hợp lệ phải thành công (không 409).
        mvc.perform(jsonRequest(post(UrlConstant.Common.API + UrlConstant.Common.VERSION + UrlAdminConstant.Cms.PAGES), Map.of("name", "Trang", "slug", slug,
                        "status", "DRAFT", "blocks", List.of(Map.of("type", "IMAGE_GALLERY", "sortOrder", 0,
                                "config", Map.of("header", Map.of("title", "T"), "items", List.of(Map.of("imageUrl", "/a.jpg")))))))
                        .header("Authorization", admin()))
                .andExpect(status().isCreated());
    }

    @Test
    void blockCrudUsesConfigAndTargetSegment() throws Exception {
        String slug = "trang-" + tag();
        long pageId = body(mvc.perform(jsonRequest(post(UrlConstant.Common.API + UrlConstant.Common.VERSION + UrlAdminConstant.Cms.PAGES),
                        Map.of("name", "Trang", "slug", slug, "status", "DRAFT")).header("Authorization", admin()))
                .andExpect(status().isCreated())).get("id").asLong();

        String blocksPath = UrlConstant.Common.API + UrlConstant.Common.VERSION + UrlAdminConstant.Cms.PAGES + "/" + pageId + "/blocks";
        JsonNode block = body(mvc.perform(jsonRequest(post(blocksPath), Map.of("type", "INFO_CARDS", "sortOrder", 1,
                        "config", Map.of("header", Map.of("title", "T"), "items", List.of(Map.of("imageUrl", "/a.jpg", "title", "A", "actionText", "Xem", "actionUrl", "/x"))), "targetSegment", "VIP"))
                        .header("Authorization", admin()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.config.items[0].title").value("A"))
                .andExpect(jsonPath("$.data.targetSegment").value("VIP")));
        String blockId = block.get("id").asText();

        mvc.perform(jsonRequest(put(blocksPath + "/" + blockId), Map.of("type", "IMAGE_GALLERY", "sortOrder", 2,
                        "config", Map.of("header", Map.of("title", "T"), "items", List.of(Map.of("imageUrl", "/a.jpg")))))
                        .header("Authorization", admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.type").value("IMAGE_GALLERY"))
                .andExpect(jsonPath("$.data.sortOrder").value(2));

        mvc.perform(delete(blocksPath + "/" + blockId).header("Authorization", admin()))
                .andExpect(status().isNoContent());
    }

    @Test
    void storefrontPageResolvesBannerPlacementAndProductCarousel() throws Exception {
        String code = "CMS_" + tag().toUpperCase();
        BannerPlacementEntity placement = new BannerPlacementEntity();
        placement.setCode(code);
        placement.setName("Vị trí CMS");
        placement.setDisplayType("GRID");
        placements.create(placement);
        mvc.perform(jsonRequest(post(UrlConstant.Common.API + UrlConstant.Common.VERSION + UrlAdminConstant.Banner.BANNERS), Map.of("internalName", "Banner " + code,
                        "placementCode", code, "status", "ACTIVE", "title", "Xin chào"))
                        .header("Authorization", admin()))
                .andExpect(status().isOk());

        String t = tag();
        CategoryEntity category = new CategoryEntity("DM " + t, "dm-" + t, null);
        categories.create(category);
        ProductEntity product = new ProductEntity(category.getId(), "P-" + t, "SP " + t, "sp-" + t);
        product.setBasePrice(BigDecimal.valueOf(1_000_000));
        product.publish();
        products.create(product);

        String slug = "trang-" + tag();
        mvc.perform(jsonRequest(post(UrlConstant.Common.API + UrlConstant.Common.VERSION + UrlAdminConstant.Cms.PAGES), Map.of("name", "Trang chủ", "slug", slug,
                        "status", "PUBLISHED", "blocks", List.of(
                                Map.of("type", "BANNER", "sortOrder", 1, "config", Map.of("placementCode", code)),
                                Map.of("type", "PRODUCT_CAROUSEL", "sortOrder", 2, "config", Map.of(
                                        "header", Map.of("title", "Nổi bật"),
                                        "productIds", List.of(String.valueOf(product.getId()), "khong-ton-tai"))))))
                        .header("Authorization", admin()))
                .andExpect(status().isCreated());

        mvc.perform(get(UrlConstant.Common.API + UrlConstant.Common.VERSION + UrlConstant.Cms.PAGES + "/" + slug))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.blocks[0].type").value("BANNER"))
                .andExpect(jsonPath("$.data.blocks[0].content.layout").value("GRID"))
                .andExpect(jsonPath("$.data.blocks[0].content.placement.code").value(code))
                .andExpect(jsonPath("$.data.blocks[0].content.banners.length()").value(1))
                .andExpect(jsonPath("$.data.blocks[1].type").value("PRODUCT_CAROUSEL"))
                .andExpect(jsonPath("$.data.blocks[1].content.header.title").value("Nổi bật"))
                .andExpect(jsonPath("$.data.blocks[1].content.products.length()").value(1))
                .andExpect(jsonPath("$.data.blocks[1].content.products[0].slug").value("sp-" + t));
    }

    // ---- PRODUCT_CAROUSEL theo contract admin FE: dataSource.filterType + display.limit ----

    private String pagesPath() {
        return UrlConstant.Common.API + UrlConstant.Common.VERSION + UrlAdminConstant.Cms.PAGES;
    }

    private int createPageWithCarousel(Map<String, Object> config) throws Exception {
        return mvc.perform(jsonRequest(post(pagesPath()), Map.of("name", "Trang", "slug", "trang-" + tag(),
                        "status", "DRAFT", "blocks", List.of(Map.of("type", "PRODUCT_CAROUSEL", "sortOrder", 0,
                                "config", config))))
                        .header("Authorization", admin()))
                .andReturn().getResponse().getStatus();
    }

    private String publishCarouselPage(Map<String, Object> config) throws Exception {
        String slug = "trang-" + tag();
        mvc.perform(jsonRequest(post(pagesPath()), Map.of("name", "Trang", "slug", slug, "status", "PUBLISHED",
                        "blocks", List.of(Map.of("type", "PRODUCT_CAROUSEL", "sortOrder", 0, "config", config))))
                        .header("Authorization", admin()))
                .andExpect(status().isCreated());
        return UrlConstant.Common.API + UrlConstant.Common.VERSION + UrlConstant.Cms.PAGES + "/" + slug;
    }

    private ProductEntity newPublishedProduct(String t, CategoryEntity category) {
        ProductEntity product = new ProductEntity(category.getId(), "P-" + t, "SP " + t, "sp-" + t);
        product.setBasePrice(BigDecimal.valueOf(1_000_000));
        product.publish();
        products.create(product);
        return product;
    }

    @Test
    void carouselManualProductsResolvesDataSourceProductIds() throws Exception {
        String t = tag();
        CategoryEntity category = new CategoryEntity("DM " + t, "dm-" + t, null);
        categories.create(category);
        ProductEntity product = newPublishedProduct(t, category);
        String url = publishCarouselPage(Map.of("header", Map.of("title", "Chọn tay"),
                "dataSource", Map.of("filterType", "MANUAL_PRODUCTS",
                        "productIds", List.of(String.valueOf(product.getId())))));
        mvc.perform(get(url))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.blocks[0].content.products.length()").value(1))
                .andExpect(jsonPath("$.data.blocks[0].content.products[0].slug").value("sp-" + t));
    }

    @Test
    void carouselCategoryUsesCategorySlugsAndCategoryId() throws Exception {
        String t = tag();
        CategoryEntity category = new CategoryEntity("DM " + t, "dm-" + t, null);
        categories.create(category);
        newPublishedProduct(t, category);
        // categorySlugs
        mvc.perform(get(publishCarouselPage(Map.of("header", Map.of("title", "DM"),
                        "dataSource", Map.of("filterType", "CATEGORY", "categorySlugs", List.of("dm-" + t))))))
                .andExpect(jsonPath("$.data.blocks[0].content.products.length()").value(1))
                .andExpect(jsonPath("$.data.blocks[0].content.products[0].slug").value("sp-" + t));
        // categoryId (FE dùng làm slug)
        mvc.perform(get(publishCarouselPage(Map.of("header", Map.of("title", "DM"),
                        "dataSource", Map.of("filterType", "CATEGORY", "categoryId", "dm-" + t)))))
                .andExpect(jsonPath("$.data.blocks[0].content.products.length()").value(1));
    }

    @Test
    void carouselNewArrivalsHonorsDisplayLimit() throws Exception {
        String t = tag();
        CategoryEntity category = new CategoryEntity("DM " + t, "dm-" + t, null);
        categories.create(category);
        newPublishedProduct(t, category);
        newPublishedProduct(t + "b", category);
        mvc.perform(get(publishCarouselPage(Map.of("header", Map.of("title", "Mới"),
                        "display", Map.of("limit", 1),
                        "dataSource", Map.of("filterType", "NEW_ARRIVALS")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.blocks[0].content.products.length()").value(1));
    }

    @Test
    void carouselDataDrivenSourcesAreServedWithoutError() throws Exception {
        for (String filterType : List.of("BEST_SELLERS", "MOST_FAVORITED", "CAMPAIGN")) {
            mvc.perform(get(publishCarouselPage(Map.of("header", Map.of("title", filterType),
                            "dataSource", Map.of("filterType", filterType)))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.blocks[0].content.products").isArray());
        }
    }

    @Test
    void carouselConfigMissingRequiredDataSourceFieldsIsRejected() throws Exception {
        org.junit.jupiter.api.Assertions.assertEquals(400, createPageWithCarousel(Map.of("header", Map.of("title", "x"),
                "dataSource", Map.of("filterType", "MANUAL_PRODUCTS", "productIds", List.of()))));
        org.junit.jupiter.api.Assertions.assertEquals(400, createPageWithCarousel(Map.of("header", Map.of("title", "x"),
                "dataSource", Map.of("filterType", "CATEGORY"))));
        org.junit.jupiter.api.Assertions.assertEquals(400, createPageWithCarousel(Map.of("header", Map.of("title", "x"),
                "dataSource", Map.of("filterType", "COLLECTION"))));
        org.junit.jupiter.api.Assertions.assertEquals(400, createPageWithCarousel(Map.of("header", Map.of("title", "x"))));
        org.junit.jupiter.api.Assertions.assertEquals(201, createPageWithCarousel(Map.of("header", Map.of("title", "x"),
                "dataSource", Map.of("filterType", "NEW_ARRIVALS"))));
    }

    @Test
    void hiddenBlockIsStoredButNotServedAtStorefront() throws Exception {
        String slug = "trang-" + tag();
        JsonNode page = body(mvc.perform(jsonRequest(post(pagesPath()), Map.of("name", "Trang", "slug", slug,
                        "status", "PUBLISHED", "blocks", List.of(
                                Map.of("type", "INFO_CARDS", "sortOrder", 0, "config", Map.of("header", Map.of("title", "T"), "items", List.of(Map.of("imageUrl", "/a.jpg", "title", "A", "actionText", "Xem", "actionUrl", "/x"))), "isVisible", true),
                                Map.of("type", "IMAGE_GALLERY", "sortOrder", 1, "config", Map.of("header", Map.of("title", "T"), "items", List.of(Map.of("imageUrl", "/a.jpg"))), "isVisible", false))))
                        .header("Authorization", admin()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.blocks.length()").value(2))
                .andExpect(jsonPath("$.data.blocks[1].isVisible").value(false)));
        String url = UrlConstant.Common.API + UrlConstant.Common.VERSION + UrlConstant.Cms.PAGES + "/" + slug;
        mvc.perform(get(url)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.blocks.length()").value(1))
                .andExpect(jsonPath("$.data.blocks[0].type").value("INFO_CARDS"));

        // Bật lại block ẩn qua PUT block -> storefront trả đủ 2 block.
        String hiddenId = page.get("blocks").get(1).get("id").asText();
        mvc.perform(jsonRequest(put(pagesPath() + "/" + page.get("id").asLong() + "/blocks/" + hiddenId),
                        Map.of("type", "IMAGE_GALLERY", "sortOrder", 1, "config", Map.of("header", Map.of("title", "T"), "items", List.of(Map.of("imageUrl", "/a.jpg"))), "isVisible", true))
                        .header("Authorization", admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isVisible").value(true));
        // Sửa block chỉ đổi bản nháp; phải xuất bản thì storefront mới thấy.
        mvc.perform(get(url)).andExpect(jsonPath("$.data.blocks.length()").value(1));
        mvc.perform(post(pagesPath() + "/" + page.get("id").asLong() + "/publish").header("Authorization", admin()))
                .andExpect(status().isOk());
        mvc.perform(get(url)).andExpect(jsonPath("$.data.blocks.length()").value(2));
    }

    @Test
    void pageSeoLocaleAndActiveAreStoredAndServed() throws Exception {
        String slug = "trang-" + tag();
        Map<String, Object> seo = Map.of("title", "Tiêu đề SEO", "description", "Mô tả", "keywords", "nhẫn,vàng");
        JsonNode created = body(mvc.perform(jsonRequest(post(pagesPath()), Map.of("name", "Trang", "slug", slug,
                        "status", "PUBLISHED", "locale", "en", "isActive", true, "seo", seo))
                        .header("Authorization", admin()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.locale").value("en"))
                .andExpect(jsonPath("$.data.seo.title").value("Tiêu đề SEO")));
        String url = UrlConstant.Common.API + UrlConstant.Common.VERSION + UrlConstant.Cms.PAGES + "/" + slug;
        mvc.perform(get(url)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page.locale").value("en"))
                .andExpect(jsonPath("$.data.page.seo.title").value("Tiêu đề SEO"))
                .andExpect(jsonPath("$.data.page.seo.description").value("Mô tả"));

        // Tắt trang: vẫn PUBLISHED nhưng storefront trả 404; request không gửi seo/locale thì giữ nguyên.
        mvc.perform(jsonRequest(put(pagesPath() + "/" + created.get("id").asLong()),
                        Map.of("name", "Trang", "slug", slug, "isActive", false)).header("Authorization", admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isActive").value(false))
                .andExpect(jsonPath("$.data.locale").value("en"))
                .andExpect(jsonPath("$.data.seo.title").value("Tiêu đề SEO"));
        mvc.perform(get(url)).andExpect(status().isNotFound());
    }
}
