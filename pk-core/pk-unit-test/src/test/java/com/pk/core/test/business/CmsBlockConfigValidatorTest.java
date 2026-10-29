package com.pk.core.test.business;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.pk.core.business.service.impl.CmsBlockConfigValidator;
import com.pk.core.common.exception.BusinessException;
import com.pk.core.common.exception.ErrorCode;

/** Kiểm tra validate config 6 loại block CMS theo schema FE: payload đúng, thiếu field, sai kiểu, URL nguy hiểm. */
class CmsBlockConfigValidatorTest {

    private static Map<String, Object> card() {
        return new HashMap<>(Map.of("imageUrl", "https://cdn.example.com/a.jpg", "title", "A",
                "actionText", "Xem", "actionUrl", "/stores"));
    }

    private static Map<String, Object> infoCards(Map<String, Object> item) {
        return new HashMap<>(Map.of("header", Map.of("title", "T"), "items", List.of(item)));
    }

    private static BusinessException fail(String type, Map<String, Object> config) {
        BusinessException ex = assertThrows(BusinessException.class, () -> CmsBlockConfigValidator.validate(type, config));
        assertEquals(400, ex.getStatus());
        assertEquals(ErrorCode.CMS_BLOCK_CONFIG_INVALID, ex.getCode());
        return ex;
    }

    @Test
    void validInfoCardsPassAndKeepUnknownFields() {
        Map<String, Object> item = card();
        item.put("extra", "giữ nguyên");
        Map<String, Object> cfg = infoCards(item);
        cfg.put("display", Map.of("columns", 3));
        cfg.put("unknownKey", 1);
        assertDoesNotThrow(() -> CmsBlockConfigValidator.validate("INFO_CARDS", cfg));
    }

    @Test
    void infoCardsMissingFieldReportsFieldPath() {
        Map<String, Object> item = card();
        item.remove("actionText");
        assertEquals("config.items[0].actionText", fail("INFO_CARDS", infoCards(item)).getField());
        assertEquals("config.header", fail("INFO_CARDS", new HashMap<>(Map.of("items", List.of(card())))).getField());
        assertEquals("config.items", fail("INFO_CARDS",
                new HashMap<>(Map.of("header", Map.of("title", "T"), "items", List.of()))).getField());
    }

    @Test
    void infoCardsWrongTypesAndColumns() {
        Map<String, Object> cfg = infoCards(card());
        cfg.put("display", Map.of("columns", 5));
        assertEquals("config.display.columns", fail("INFO_CARDS", cfg).getField());
        Map<String, Object> item = card();
        item.put("title", 12);
        assertEquals("config.items[0].title", fail("INFO_CARDS", infoCards(item)).getField());
        assertEquals("config.items", fail("INFO_CARDS",
                new HashMap<>(Map.of("header", Map.of("title", "T"), "items", "khong-phai-mang"))).getField());
    }

    @Test
    void dangerousUrlsAreRejected() {
        for (String bad : List.of("javascript:alert(1)", "JaVaScript:alert(1)", "data:text/html;base64,AAAA",
                "vbscript:x", "file:///etc/passwd", "//evil.example.com/x", "ftp://x/y", "no-slash.jpg",
                "https://a.com/x y", "https://a.com/<script>")) {
            Map<String, Object> item = card();
            item.put("imageUrl", bad);
            assertEquals("config.items[0].imageUrl", fail("INFO_CARDS", infoCards(item)).getField(), bad);
        }
        Map<String, Object> item = card();
        item.put("actionUrl", "javascript:alert(1)");
        assertEquals("config.items[0].actionUrl", fail("INFO_CARDS", infoCards(item)).getField());
    }

    @Test
    void safeUrlsAreAccepted() {
        for (String ok : List.of("https://a.com/x?y=1#z", "http://a.com", "/stores", "#section", "mailto:a@b.com",
                "tel:+84901234567")) {
            Map<String, Object> item = card();
            item.put("actionUrl", ok);
            assertDoesNotThrow(() -> CmsBlockConfigValidator.validate("INFO_CARDS", infoCards(item)), ok);
        }
        Map<String, Object> image = card();
        image.put("imageUrl", "mailto:a@b.com");
        assertEquals("config.items[0].imageUrl", fail("INFO_CARDS", infoCards(image)).getField());
    }

    @Test
    void imageGalleryRules() {
        Map<String, Object> item = new HashMap<>(Map.of("imageUrl", "/a.jpg", "actionType", "MIX_MATCH",
                "mixMatchSkus", List.of(Map.of("variationId", "12", "sortOrder", 1))));
        Map<String, Object> cfg = new HashMap<>(Map.of("header", Map.of("title", "T"), "items", List.of(item),
                "display", Map.of("layoutStyle", "GRID")));
        assertDoesNotThrow(() -> CmsBlockConfigValidator.validate("IMAGE_GALLERY", cfg));

        cfg.put("display", Map.of("layoutStyle", "MASONRY"));
        assertEquals("config.display.layoutStyle", fail("IMAGE_GALLERY", cfg).getField());
        cfg.put("display", Map.of("layoutStyle", "GRID"));
        item.put("actionType", "OPEN_APP");
        assertEquals("config.items[0].actionType", fail("IMAGE_GALLERY", cfg).getField());
        item.put("actionType", "MIX_MATCH");
        item.put("mixMatchSkus", List.of(Map.of("sortOrder", 1)));
        assertEquals("config.items[0].mixMatchSkus[0].variationId", fail("IMAGE_GALLERY", cfg).getField());
    }

    @Test
    void productListRules() {
        Map<String, Object> src = new HashMap<>(Map.of("filterType", "COLLECTION", "sort", "price_asc",
                "collectionSlugs", List.of("nhan-cuoi")));
        Map<String, Object> cfg = new HashMap<>(Map.of("productDataSource", src,
                "dataSource", Map.of("filterType", "CMS_STOREFRONT", "cmsPageSlug", "banner-xen-ke")));
        assertDoesNotThrow(() -> CmsBlockConfigValidator.validate("PRODUCT_LIST", cfg));
        assertDoesNotThrow(() -> CmsBlockConfigValidator.validate("PRODUCT_LIST",
                Map.of("productDataSource", Map.of("filterType", "ALL_PRODUCTS"))));

        assertEquals("config.productDataSource", fail("PRODUCT_LIST", new HashMap<>()).getField());
        src.put("sort", "random");
        assertEquals("config.productDataSource.sort", fail("PRODUCT_LIST", cfg).getField());
        src.put("sort", "newest");
        src.put("collectionSlugs", List.of());
        assertEquals("config.productDataSource.collectionSlugs", fail("PRODUCT_LIST", cfg).getField());
        src.put("filterType", "BEST");
        assertEquals("config.productDataSource.filterType", fail("PRODUCT_LIST", cfg).getField());
    }

    @Test
    void categoryNavRules() {
        assertDoesNotThrow(() -> CmsBlockConfigValidator.validate("PRODUCT_CATEGORY_NAV", new HashMap<>()));
        assertDoesNotThrow(() -> CmsBlockConfigValidator.validate("PRODUCT_CATEGORY_NAV",
                Map.of("display", Map.of("limit", 0))));
        assertEquals("config.display.limit", fail("PRODUCT_CATEGORY_NAV", Map.of("display", Map.of("limit", -1))).getField());
        assertEquals("config.display.limit", fail("PRODUCT_CATEGORY_NAV", Map.of("display", Map.of("limit", "5"))).getField());
        assertEquals("config.display.limit", fail("PRODUCT_CATEGORY_NAV", Map.of("display", Map.of("limit", 2.5))).getField());
    }

    @Test
    void collectionShowcaseRules() {
        Map<String, Object> item = new HashMap<>(Map.of("title", "BST 1", "imageUrl", "/c1.jpg", "actionUrl", "/bst-1"));
        Map<String, Object> cfg = new HashMap<>(Map.of("header", Map.of("title", "T"), "items", List.of(item)));
        assertDoesNotThrow(() -> CmsBlockConfigValidator.validate("PRODUCT_COLLECTION_SHOWCASE", cfg));
        cfg.put("items", List.of(item, item, item, item));
        assertEquals("config.items", fail("PRODUCT_COLLECTION_SHOWCASE", cfg).getField());
        item.remove("imageUrl");
        cfg.put("items", List.of(item));
        assertEquals("config.items[0].imageUrl", fail("PRODUCT_COLLECTION_SHOWCASE", cfg).getField());
    }

    @Test
    void expandableDescriptionRules() {
        assertDoesNotThrow(() -> CmsBlockConfigValidator.validate("PRODUCT_EXPANDABLE_DESCRIPTION",
                Map.of("title", "Mô tả", "description", "Nội dung", "maxCollapsedLines", 3)));
        assertEquals("config.description", fail("PRODUCT_EXPANDABLE_DESCRIPTION", Map.of("title", "T")).getField());
        assertEquals("config.title", fail("PRODUCT_EXPANDABLE_DESCRIPTION",
                Map.of("title", " ", "description", "d")).getField());
        assertEquals("config.maxCollapsedLines", fail("PRODUCT_EXPANDABLE_DESCRIPTION",
                Map.of("title", "T", "description", "d", "maxCollapsedLines", 0)).getField());
    }

    @Test
    void otherTypesAreIgnored() {
        assertDoesNotThrow(() -> CmsBlockConfigValidator.validate("BANNER", new HashMap<>()));
    }
}
