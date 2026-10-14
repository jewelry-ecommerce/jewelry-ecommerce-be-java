package com.pk.core.test.business;

import com.fasterxml.jackson.databind.JsonNode;
import com.pk.core.business.repository.CategoryRepo;
import com.pk.core.business.repository.ProductRepo;
import com.pk.core.business.repository.ProductSkuRepo;
import com.pk.core.model.constant.UrlConstant;
import com.pk.core.model.constant.admin.UrlAdminConstant;
import com.pk.core.model.entity.CategoryEntity;
import com.pk.core.model.entity.ProductEntity;
import com.pk.core.model.entity.ProductSkuEntity;
import com.pk.core.test.common.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** V24: export CSV sản phẩm, import CSV có kết quả từng dòng (partial success), tra cứu job. */
class ImportExportIT extends IntegrationTestBase {

    private static final String V1 = UrlConstant.Common.API + UrlConstant.Common.VERSION;

    @Autowired CategoryRepo categories;
    @Autowired ProductRepo products;
    @Autowired ProductSkuRepo productSkus;

    private CategoryEntity category(String t) {
        CategoryEntity c = new CategoryEntity("DM " + t, "dm-" + t, null);
        c.setCode("dm-" + t);
        categories.create(c);
        return c;
    }

    private JsonNode importCsv(String csv, String fileName, String auth, int expected) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", fileName, "text/csv", csv.getBytes(StandardCharsets.UTF_8));
        return body(mvc.perform(multipart(V1 + UrlAdminConstant.Ie.IMPORT_PRODUCTS).file(file)
                .header("Authorization", auth)).andExpect(status().is(expected)));
    }

    @Test
    void exportSelectedProductsWritesCsvWithOneRowPerSku() throws Exception {
        String auth = bearer(adminAccessToken());
        String t = UUID.randomUUID().toString().substring(0, 8);
        CategoryEntity c = category(t);
        ProductEntity p = new ProductEntity(c.getId(), "EX-" + t, "Nhẫn, \"đặc biệt\" " + t, "ex-" + t);
        p.setBasePrice(BigDecimal.valueOf(1_000_000));
        products.create(p);
        for (String suffix : List.of("A", "B")) {
            ProductSkuEntity sku = new ProductSkuEntity("EXS-" + t + suffix, "M", BigDecimal.valueOf(1_000_000), 5);
            sku.setProductId(p.getId());
            sku.setName("Nhẫn " + suffix);
            productSkus.create(sku);
        }

        JsonNode job = body(mvc.perform(jsonRequest(post(V1 + UrlAdminConstant.Ie.EXPORT_PRODUCTS),
                        Map.of("productIds", List.of(p.getId()))).header("Authorization", auth))
                .andExpect(status().isOk()));

        assertEquals("DONE", job.get("status").asText());
        assertEquals("EXPORT", job.get("jobType").asText());
        assertEquals(2, job.get("totalRows").asInt());
        String csv = mvc.perform(get(job.get("fileUrl").asText())).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertTrue(csv.contains("product_code,product_name"));
        assertTrue(csv.contains("\"Nhẫn, \"\"đặc biệt\"\" " + t + "\""));
        assertTrue(csv.contains("EXS-" + t + "A"));
        assertTrue(csv.contains("EXS-" + t + "B"));
        assertTrue(!csv.contains("cost"), "giá vốn (admin-only) không được xuất");
    }

    @Test
    void importCreatesAddsAndUpdatesWithPartialSuccess() throws Exception {
        String auth = bearer(adminAccessToken());
        String t = UUID.randomUUID().toString().substring(0, 8);
        CategoryEntity c = category(t);
        String csv = "product_code,product_name,category_code,sku_code,sku_name,size,list_price,sale_price,stock\r\n"
                + "IM-" + t + ",Nhẫn nhập " + t + "," + c.getCode() + ",IMS-" + t + "A,Nhẫn A,M,1500000,,7\r\n"
                + "IM-" + t + ",Nhẫn nhập " + t + "," + c.getCode() + ",IMS-" + t + "B,Nhẫn B,L,1700000,1600000,3\r\n"
                + "IM-" + t + ",Nhẫn nhập " + t + "," + c.getCode() + ",IMS-" + t + "C,Nhẫn C,S,abc,,1\r\n"
                + "IM-" + t + ",Nhẫn nhập " + t + "," + c.getCode() + ",IMS-" + t + "A,,,1800000,,\r\n";

        JsonNode job = importCsv(csv, "sanpham.csv", auth, 200);

        assertEquals("DONE", job.get("status").asText());
        assertEquals(4, job.get("totalRows").asInt());
        assertEquals(3, job.get("successRows").asInt());
        assertEquals(1, job.get("failedRows").asInt());
        assertNotNull(job.get("fileUrl").asText());

        ProductEntity p = products.findByCode("IM-" + t);
        assertNotNull(p);
        assertEquals("DRAFT", p.getStatus());
        assertEquals(0, new BigDecimal("1800000").compareTo(productSkus.findBySkuCode("IMS-" + t + "A").getListPrice()));
        assertEquals(0, new BigDecimal("1700000").compareTo(productSkus.findBySkuCode("IMS-" + t + "B").getListPrice()));
        assertEquals(null, productSkus.findBySkuCode("IMS-" + t + "C"));

        long id = job.get("jobId").asLong();
        JsonNode errors = body(mvc.perform(get(V1 + UrlAdminConstant.Ie.JOBS + "/" + id + "/result-rows?status=ERROR")
                .header("Authorization", auth)).andExpect(status().isOk()));
        assertEquals(1, errors.get("content").size());
        assertEquals(4, errors.get("content").get(0).get("lineNo").asInt());
        assertTrue(errors.get("content").get(0).get("message").asText().contains("list_price"));
        JsonNode all = body(mvc.perform(get(V1 + UrlAdminConstant.Ie.JOBS + "/" + id + "/result-rows")
                .header("Authorization", auth)).andExpect(status().isOk()));
        assertEquals(4, all.get("content").size());

        JsonNode status = body(mvc.perform(get(V1 + UrlAdminConstant.Ie.JOB_STATUS + "/" + id)
                .header("Authorization", auth)).andExpect(status().isOk()));
        assertEquals("DONE", status.get("status").asText());
        JsonNode list = body(mvc.perform(get(V1 + UrlAdminConstant.Ie.JOBS + "?jobType=IMPORT")
                .header("Authorization", auth)).andExpect(status().isOk()));
        assertTrue(list.get("content").size() >= 1);
    }

    @Test
    void importWithMissingColumnsFailsTheJob() throws Exception {
        String auth = bearer(adminAccessToken());
        JsonNode job = importCsv("product_code,product_name\r\nX,Y\r\n", "thieu.csv", auth, 200);

        assertEquals("FAILED", job.get("status").asText());
        assertTrue(job.get("errorMessage").asText().contains("sku_code"));
    }

    @Test
    void importRejectsNonCsvAndRequiresAuth() throws Exception {
        String auth = bearer(adminAccessToken());
        importCsv("a", "x.txt", auth, 400);
        mvc.perform(get(V1 + UrlAdminConstant.Ie.TOPICS)).andExpect(status().isUnauthorized());
        mvc.perform(get(V1 + UrlAdminConstant.Ie.TOPICS).header("Authorization", bearer(customerAccessToken())))
                .andExpect(status().isForbidden());
    }

    @Test
    void topicsDeclaresFastFboUnsupported() throws Exception {
        JsonNode topics = body(mvc.perform(get(V1 + UrlAdminConstant.Ie.TOPICS)
                .header("Authorization", bearer(adminAccessToken()))).andExpect(status().isOk()));
        assertEquals(false, topics.get("fastFboSupported").asBoolean());
    }

    @Test
    void unknownJobIs404() throws Exception {
        mvc.perform(get(V1 + UrlAdminConstant.Ie.JOB_STATUS + "/999999")
                .header("Authorization", bearer(adminAccessToken()))).andExpect(status().isNotFound());
    }
}
