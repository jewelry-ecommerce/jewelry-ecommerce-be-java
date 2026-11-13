package com.pk.core.test.business;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pk.core.business.ai.AssistantReplyParser;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssistantReplyParserTest {

    private final AssistantReplyParser parser = new AssistantReplyParser(new ObjectMapper());

    @Test
    void keepsTheReplyAndOnlyCatalogProductIds() throws Exception {
        AssistantReplyParser.Draft draft = parser.parse(
                "{\"reply\":\"Chào bạn, nhẫn bạc dễ đeo hằng ngày.\",\"productIds\":[10,99,10]}",
                Set.of(10L));
        assertEquals("Chào bạn, nhẫn bạc dễ đeo hằng ngày.", draft.reply());
        assertEquals(1, draft.productIds().size());
        assertEquals(10L, draft.productIds().get(0));
    }

    @Test
    void returnsAnEmptyProductListWhenTheQuestionNeedsNoItem() throws Exception {
        AssistantReplyParser.Draft draft = parser.parse("{\"reply\":\"Bạc 925 tránh nước biển.\",\"productIds\":[]}", Set.of(10L));
        assertTrue(draft.productIds().isEmpty());
    }
}
