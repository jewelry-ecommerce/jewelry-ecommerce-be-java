package com.pk.core.test.business;

import com.pk.core.business.service.impl.BannerLayoutSupport;
import com.pk.core.common.exception.BusinessException;
import com.pk.core.model.entity.BannerPlacementLayoutEntity;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BannerLayoutSupportTest {

    @Test
    void settingsAreValidatedAndNormalized() {
        Map<String, Object> s = BannerLayoutSupport.normalizeSettings(
                Map.of("columns", 4, "gap", 8, "mobileOddFullWidthItem", "last"));
        assertEquals(4, s.get("columns"));
        assertEquals("LAST", s.get("mobileOddFullWidthItem"));
        assertNull(BannerLayoutSupport.normalizeSettings(Map.of()));
        assertNull(BannerLayoutSupport.normalizeSettings(null));
        assertThrows(BusinessException.class, () -> BannerLayoutSupport.normalizeSettings(Map.of("columns", 0)));
        assertThrows(BusinessException.class, () -> BannerLayoutSupport.normalizeSettings(Map.of("columns", 2.5)));
        assertThrows(BusinessException.class, () -> BannerLayoutSupport.normalizeSettings(Map.of("colour", "red")));
        assertThrows(BusinessException.class,
                () -> BannerLayoutSupport.normalizeSettings(Map.of("mobileOddFullWidthItem", "MIDDLE")));
    }

    @Test
    void layoutIsInEffectOnlyWhenActiveOrScheduledAndInsideWindow() {
        Date now = new Date();
        Date past = new Date(now.getTime() - 60_000);
        Date future = new Date(now.getTime() + 60_000);
        BannerPlacementLayoutEntity l = new BannerPlacementLayoutEntity();
        l.setStatus("ACTIVE");
        assertTrue(BannerLayoutSupport.inEffect(l, now));
        l.setActiveFrom(future);
        assertFalse(BannerLayoutSupport.inEffect(l, now));
        l.setActiveFrom(past);
        l.setActiveTo(future);
        assertTrue(BannerLayoutSupport.inEffect(l, now));
        l.setActiveTo(past);
        assertFalse(BannerLayoutSupport.inEffect(l, now));
        l.setActiveTo(null);
        l.setStatus("SCHEDULED");
        assertTrue(BannerLayoutSupport.inEffect(l, now));
        l.setStatus("DRAFT");
        assertFalse(BannerLayoutSupport.inEffect(l, now));
        l.setStatus("INACTIVE");
        assertFalse(BannerLayoutSupport.inEffect(l, now));
    }
}
