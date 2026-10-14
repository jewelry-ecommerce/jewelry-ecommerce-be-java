package com.pk.core.test.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pk.core.model.dto.response.ProductInfoResponseDto;
import com.pk.core.model.dto.response.ProductMediaResponseDto;
import com.pk.core.model.dto.response.ProductResponseDto;
import com.pk.core.model.dto.response.ProductSkuResponseDto;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** FE v5 (PIM mục 5): productInfos/seo/dimensions/ảnh theo SKU phải phản ánh dữ liệu đã lưu, không phải giá trị cứng. */
class ProductResponseContractTest {

    private final ObjectMapper mapper = new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL);

    private ProductResponseDto product() {
        ProductResponseDto dto = new ProductResponseDto();
        dto.setId(1L);
        dto.setCode("P1");
        dto.setName("Nhẫn vàng");
        dto.setSlug("nhan-vang");
        dto.setShortDescription("Mô tả ngắn");
        dto.setThumbnailUrl("/chung.jpg");
        dto.setPriceFrom(BigDecimal.valueOf(1_000_000));
        ProductSkuResponseDto sku7 = new ProductSkuResponseDto();
        sku7.setId(7L);
        sku7.setSkuCode("SKU-7");
        sku7.setDefault(true);
        sku7.setListPrice(BigDecimal.valueOf(1_000_000));
        sku7.setWeightGram(BigDecimal.valueOf(3));
        ProductSkuResponseDto sku8 = new ProductSkuResponseDto();
        sku8.setId(8L);
        sku8.setSkuCode("SKU-8");
        sku8.setListPrice(BigDecimal.valueOf(1_200_000));
        dto.setSkus(List.of(sku7, sku8));
        return dto;
    }

    @Test
    void productInfosAreSerializedWithEntityId() {
        ProductResponseDto dto = product();
        dto.setProductInfos(List.of(new ProductInfoResponseDto(5L, "Bảo hành", "12 tháng", 1)));

        JsonNode infos = mapper.valueToTree(dto).get("productInfos");

        assertEquals(1, infos.size());
        assertEquals("5", infos.get(0).get("id").asText());
        assertEquals("Bảo hành", infos.get(0).get("title").asText());
        assertEquals("12 tháng", infos.get(0).get("description").asText());
    }

    @Test
    void seoUsesConfiguredMetaAndFallsBackToNameAndShortDescription() {
        ProductResponseDto dto = product();
        JsonNode fallback = mapper.valueToTree(dto).get("seo");
        assertEquals("Nhẫn vàng", fallback.get("metaTitle").asText());
        assertEquals("Mô tả ngắn", fallback.get("metaDescription").asText());

        dto.setMetaTitle("Tiêu đề SEO");
        dto.setMetaDescription("Mô tả SEO");
        dto.setMetaKeywords("nhẫn, vàng");
        JsonNode seo = mapper.valueToTree(dto).get("seo");
        assertEquals("Tiêu đề SEO", seo.get("metaTitle").asText());
        assertEquals("Mô tả SEO", seo.get("metaDescription").asText());
        assertEquals("nhẫn, vàng", seo.get("metaKeywords").asText());
    }

    @Test
    void dimensionsReturnStoredValuesAndFallBackToDefaultSkuWeight() {
        ProductResponseDto dto = product();
        JsonNode before = mapper.valueToTree(dto).get("dimensions");
        assertEquals(3.0, before.get("weight").asDouble());
        assertTrue(!before.has("length") || before.get("length").isNull(), "length chưa cấu hình: bỏ khóa hoặc null");

        dto.setWeightGram(BigDecimal.valueOf(4.5));
        dto.setLength(BigDecimal.valueOf(10));
        dto.setWidth(BigDecimal.valueOf(2));
        dto.setHeight(BigDecimal.valueOf(1.5));
        JsonNode after = mapper.valueToTree(dto).get("dimensions");
        assertEquals(4.5, after.get("weight").asDouble());
        assertEquals(10.0, after.get("length").asDouble());
        assertEquals(2.0, after.get("width").asDouble());
        assertEquals(1.5, after.get("height").asDouble());
    }

    @Test
    void variantImageUsesSkuOwnMediaElseSharedThumbnail() {
        ProductResponseDto dto = product();
        dto.setMedia(List.of(new ProductMediaResponseDto("1", "1", "7", "/sku7.jpg", "alt", "IMAGE", 0, true)));

        JsonNode variants = mapper.valueToTree(dto).get("variants");

        assertEquals("/sku7.jpg", variants.get(0).get("image").asText());
        assertEquals("/chung.jpg", variants.get(1).get("image").asText());
    }
}
