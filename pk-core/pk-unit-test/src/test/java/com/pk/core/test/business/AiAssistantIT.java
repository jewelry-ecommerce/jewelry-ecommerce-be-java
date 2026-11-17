package com.pk.core.test.business;

import com.pk.core.business.ai.AssistantReplyParser;
import com.pk.core.business.ai.GeminiAssistantClient;
import com.pk.core.business.repository.CategoryRepo;
import com.pk.core.business.repository.ProductRepo;
import com.pk.core.business.repository.ProductSkuRepo;
import com.pk.core.common.exception.BusinessException;
import com.pk.core.common.exception.ErrorCode;
import com.pk.core.model.constant.UrlConstant;
import com.pk.core.model.entity.CategoryEntity;
import com.pk.core.model.entity.ProductEntity;
import com.pk.core.model.entity.ProductSkuEntity;
import com.pk.core.service.PkServiceApplication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.math.BigDecimal;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Uses production repository transaction boundaries: deliberately no RepoTxTestConfig. */
@SpringBootTest(classes = PkServiceApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AiAssistantIT {
    private static final String ENDPOINT = UrlConstant.Common.API + UrlConstant.Common.VERSION + UrlConstant.Ai.ASSISTANT;
    @Autowired private MockMvc mvc;
    @Autowired private CategoryRepo categories;
    @Autowired private ProductRepo products;
    @Autowired private ProductSkuRepo skus;
    @Autowired private PlatformTransactionManager transactionManager;
    @MockBean private GeminiAssistantClient gemini;

    @BeforeEach
    void answerWithoutCallingAnExternalProvider() {
        when(gemini.reply(anyString(), anyList())).thenAnswer(invocation -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive(),
                    "Catalog transaction must close before calling the external AI provider");
            return new AssistantReplyParser.Draft("Shop chào bạn, shop có thể tư vấn chọn nhẫn ạ.", List.of());
        });
    }

    @Test
    void guestGreetingLoadsCatalogWithTheProductionTransactionBoundary() throws Exception {
        mvc.perform(post(ENDPOINT).contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"chào\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reply").value("Shop chào bạn, shop có thể tư vấn chọn nhẫn ạ."));
    }

    @Test
    void guestProductQuestionDoesNotBecomeAnInternalError() throws Exception {
        mvc.perform(post(ENDPOINT).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"tôi cần tư vấn mua nhẫn\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recommendations").isArray());
    }

    @Test
    void providerUnavailableReturnsTheExistingFallbackInsteadOfAnInternalError() throws Exception {
        when(gemini.reply(anyString(), anyList()))
                .thenThrow(new BusinessException(503, ErrorCode.AI_PROVIDER_UNAVAILABLE, "AI đang bận"));
        mvc.perform(post(ENDPOINT).contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"chào\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reply").isNotEmpty())
                .andExpect(jsonPath("$.data.recommendations").isEmpty());
    }

    @Test
    void productAdviceIncludesOnlyTheRealCatalogCard() throws Exception {
        Long productId = seedProduct();
        when(gemini.reply(anyString(), anyList())).thenAnswer(invocation -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            return new AssistantReplyParser.Draft("Bạn có thể tham khảo nhẫn bạc này ạ.", List.of(productId));
        });
        mvc.perform(post(ENDPOINT).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"tôi cần tư vấn mua nhẫn\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recommendations[0].id").value(productId.intValue()))
                .andExpect(jsonPath("$.data.recommendations[0].price").value(500000));
    }

    private Long seedProduct() {
        return new TransactionTemplate(transactionManager).execute(status -> {
            String suffix = UUID.randomUUID().toString();
            CategoryEntity category = categories.create(new CategoryEntity("Nhẫn", "ring-" + suffix, ""));
            ProductEntity product = new ProductEntity(category.getId(), "RING-" + suffix, "Nhẫn bạc", "ring-" + suffix);
            product.setBasePrice(new BigDecimal("500000"));
            product.publish();
            products.create(product);
            ProductSkuEntity sku = new ProductSkuEntity("SKU-" + suffix, "12", new BigDecimal("500000"), 5);
            sku.setProductId(product.getId());
            sku.setStatus(ProductSkuEntity.PUBLISHED);
            skus.create(sku);
            return product.getId();
        });
    }
}
