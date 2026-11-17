package com.pk.core.test.business;

import com.pk.core.business.ai.AssistantQuestionRouter;
import com.pk.core.business.ai.AssistantRoute;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssistantQuestionRouterTest {

    @Test
    void greetingDoesNotLookUpProducts() {
        AssistantRoute route = AssistantQuestionRouter.route("chào");
        assertEquals(AssistantRoute.GREETING, route.kind());
        assertFalse(route.looksUpProducts());
    }

    @Test
    void shippingQuestionStaysOnShopFacts() {
        AssistantRoute route = AssistantQuestionRouter.route("phí ship bao nhiêu");
        assertEquals(AssistantRoute.SHOP, route.kind());
        assertFalse(route.looksUpProducts());
    }

    @Test
    void ringQuestionKeepsTheProductWord() {
        AssistantRoute route = AssistantQuestionRouter.route("tôi muốn mua nhẫn");
        assertEquals(AssistantRoute.PRODUCT, route.kind());
        assertEquals("nhẫn", route.keyword());
        assertNull(route.slug());
    }

    @Test
    void productLinkUsesTheSlugAndDropsTheUrl() {
        AssistantRoute route = AssistantQuestionRouter.route(
                "xem giúp https://jewelry-ecommerce-client.vercel.app/san-pham/nhan-bac-01");
        assertTrue(route.looksUpProducts());
        assertEquals("nhan-bac-01", route.slug());
        assertEquals("", route.keyword());
    }
}
