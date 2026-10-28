package com.pk.core.test.business;

import com.fasterxml.jackson.databind.JsonNode;
import com.pk.core.model.constant.UrlConstant;
import com.pk.core.model.constant.admin.UrlAdminConstant;
import com.pk.core.test.common.IntegrationTestBase;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Từ khoá phổ biến, gợi ý và lịch sử tìm kiếm theo tài khoản (V38): chỉ ghi khi có kết quả, khách không có lịch sử. */
class SearchHistoryIT extends IntegrationTestBase {

    private static final String V1 = UrlConstant.Common.API + UrlConstant.Common.VERSION;
    private static final String CATALOG = V1 + UrlAdminConstant.Catalog.PRODUCTS;
    private static final String PRODUCTS = V1 + UrlConstant.Product.BASE;
    private static final String SEARCH = V1 + UrlConstant.Product.SEARCH;

    private String t;

    /** Một sản phẩm đã publish tên "Nhẫn <t>" trong danh mục "DM <t>" để tìm theo <t> ra đúng 1 kết quả. */
    private void publishedProduct() throws Exception {
        t = UUID.randomUUID().toString().substring(0, 8);
        String admin = bearer(adminAccessToken());
        long categoryId = body(mvc.perform(jsonRequest(post(V1 + UrlAdminConstant.Catalog.CATEGORIES),
                        Map.of("name", "DM " + t, "slug", "dm-" + t, "sortOrder", 0)).header("Authorization", admin))
                .andExpect(status().isCreated())).get("id").asLong();
        long productId = body(mvc.perform(jsonRequest(post(CATALOG), Map.of(
                        "categoryId", categoryId, "code", "P-" + t, "name", "Nhẫn " + t, "slug", "nhan-" + t,
                        "variants", List.of(Map.of("skuCode", "SKU-" + t, "sizeLabel", "M", "listPrice", 1_000_000,
                                "onHand", 5, "isDefault", true)))).header("Authorization", admin))
                .andExpect(status().isCreated())).get("id").asLong();
        mvc.perform(patch(CATALOG + "/" + productId + "/publish").header("Authorization", admin)).andExpect(status().isOk());
    }

    private void search(String keyword, String auth) throws Exception {
        var req = get(PRODUCTS).param("search", keyword);
        mvc.perform(auth == null ? req : req.header("Authorization", auth)).andExpect(status().isOk());
    }

    private JsonNode history(String auth) throws Exception {
        return body(mvc.perform(get(SEARCH + "/history").header("Authorization", auth)).andExpect(status().isOk()));
    }

    private boolean contains(JsonNode array, String text) {
        for (JsonNode n : array) {
            if (text.equals(n.isTextual() ? n.asText() : n.path("keyword").asText(n.path("text").asText()))) {
                return true;
            }
        }
        return false;
    }

    @Test
    void historyNeedsLoginAndGuestSearchStillFeedsTrendingAndSuggestions() throws Exception {
        publishedProduct();
        mvc.perform(get(SEARCH + "/history")).andExpect(status().isUnauthorized());
        mvc.perform(delete(SEARCH + "/history")).andExpect(status().isUnauthorized());

        search(t, null);

        JsonNode trending = body(mvc.perform(get(SEARCH + "/trending").param("limit", "20")).andExpect(status().isOk()));
        assertTrue(contains(trending, t), trending.toString());
        JsonNode suggestions = body(mvc.perform(get(SEARCH + "/suggestions").param("q", t)).andExpect(status().isOk()));
        assertTrue(contains(suggestions, t), suggestions.toString());
        boolean keywordEntry = false;
        for (JsonNode s : suggestions) {
            keywordEntry |= "keyword".equals(s.get("type").asText()) && t.equals(s.get("text").asText());
        }
        assertTrue(keywordEntry, "từ khoá phải có dạng type=keyword: " + suggestions);
    }

    @Test
    void categorySuggestionCarriesSlugForNavigation() throws Exception {
        publishedProduct();
        JsonNode suggestions = body(mvc.perform(get(SEARCH + "/suggestions").param("q", "dm " + t)).andExpect(status().isOk()));
        JsonNode first = suggestions.get(0);
        assertEquals("category", first.get("type").asText());
        assertEquals("dm-" + t, first.get("slug").asText());
        assertEquals(0, body(mvc.perform(get(SEARCH + "/suggestions").param("q", "x"))).size(), "q quá ngắn -> rỗng");
    }

    @Test
    void accountHistoryRecordsOnlySearchesWithResultsOnFirstPage() throws Exception {
        publishedProduct();
        String me = bearer(customerAccessToken());
        String other = bearer(customerAccessToken());

        search("zz" + t, me);                                   // không có kết quả -> không ghi
        mvc.perform(get(PRODUCTS).param("search", t).param("page", "2").header("Authorization", other))
                .andExpect(status().isOk());                    // trang 2 -> không ghi
        assertEquals(0, history(me).size());
        assertEquals(0, history(other).size());

        search(t, me);
        JsonNode mine = history(me);
        assertEquals(1, mine.size());
        assertEquals(t, mine.get(0).get("keyword").asText());
        assertFalse(mine.get(0).get("searchedAt").asText().isBlank());
        assertEquals(0, history(other).size(), "lịch sử của người khác không lẫn vào");

        search(t, me);                                          // tìm lại: không nhân đôi dòng
        assertEquals(1, history(me).size());
    }

    @Test
    void deleteOneKeywordThenClearAll() throws Exception {
        publishedProduct();
        String me = bearer(customerAccessToken());
        search(t, me);
        mvc.perform(delete(SEARCH + "/history/keyword/" + t.toUpperCase()).header("Authorization", me))
                .andExpect(status().isOk());                    // không phân biệt hoa thường
        assertEquals(0, history(me).size());

        search(t, me);
        mvc.perform(delete(SEARCH + "/history/keyword").param("keyword", t).header("Authorization", me))
                .andExpect(status().isOk());                    // dạng query (dùng được với từ khoá có dấu qua BFF)
        assertEquals(0, history(me).size());

        search(t, me);
        mvc.perform(delete(SEARCH + "/history").header("Authorization", me)).andExpect(status().isOk());
        assertEquals(0, history(me).size());
        mvc.perform(delete(SEARCH + "/history/keyword/khong-co").header("Authorization", me)).andExpect(status().isOk());
    }
}
