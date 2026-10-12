package com.pk.core.business.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pk.core.business.repository.BannerPlacementLayoutItemRepo;
import com.pk.core.business.repository.BannerPlacementLayoutRepo;
import com.pk.core.business.repository.BannerRepo;
import com.pk.core.common.exception.BusinessException;
import com.pk.core.common.exception.ErrorCode;
import com.pk.core.model.dto.response.BannerActionResponseDto;
import com.pk.core.model.dto.response.BannerPlacementRenderResponseDto;
import com.pk.core.model.dto.response.BannerResponseDto;
import com.pk.core.model.entity.BannerEntity;
import com.pk.core.model.entity.BannerPlacementEntity;
import com.pk.core.model.entity.BannerPlacementLayoutEntity;
import com.pk.core.model.entity.BannerPlacementLayoutItemEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Phần dùng chung của banner builder: (de)serialize JSON settings/displayMetadata, chuyển BannerEntity -> DTO và dựng
 * kết quả render theo layout. Không phụ thuộc BannerService/BannerPlacementService để tránh vòng phụ thuộc.
 */
@Component
@RequiredArgsConstructor
public class BannerLayoutSupport {

    static final Set<String> SETTING_KEYS = Set.of("columns", "rows", "gap", "mobileOddFullWidthItem");
    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {
    };
    private static final TypeReference<List<BannerActionResponseDto>> ACTIONS = new TypeReference<>() {
    };

    private final BannerRepo banners;
    private final BannerPlacementLayoutRepo layouts;
    private final BannerPlacementLayoutItemRepo items;
    private final ObjectMapper objectMapper;

    // ------------------------------------------------------------------ json

    public Map<String, Object> readMap(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, MAP);
        } catch (JsonProcessingException ex) {
            return null; // dữ liệu hỏng thì coi như không cấu hình, không làm hỏng cả trang
        }
    }

    public String writeMap(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Không ghi được JSON cấu hình banner", ex);
        }
    }

    public BannerResponseDto toBannerDto(BannerEntity b) {
        List<BannerActionResponseDto> actions = new ArrayList<>();
        if (b.getActions() != null && !b.getActions().isBlank()) {
            try {
                actions = objectMapper.readValue(b.getActions(), ACTIONS);
            } catch (JsonProcessingException ex) {
                actions = new ArrayList<>();
            }
        }
        return BannerResponseDto.from(b, actions);
    }

    /** Kiểm tra & chuẩn hoá settings của vị trí; trả null nếu rỗng (= xoá cấu hình). */
    public static Map<String, Object> normalizeSettings(Map<String, Object> in) {
        if (in == null || in.isEmpty()) {
            return null;
        }
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : in.entrySet()) {
            if (!SETTING_KEYS.contains(e.getKey())) {
                throw BusinessException.badRequest(ErrorCode.VALIDATION_FAILED,
                        "settings." + e.getKey() + " không hợp lệ (chỉ nhận columns, rows, gap, mobileOddFullWidthItem)",
                        e.getKey());
            }
            if (e.getValue() == null) {
                continue;
            }
            switch (e.getKey()) {
                case "columns" -> out.put("columns", intOf(e.getValue(), "settings.columns", 1, 12));
                case "rows" -> out.put("rows", intOf(e.getValue(), "settings.rows", 1, 50));
                case "gap" -> out.put("gap", intOf(e.getValue(), "settings.gap", 0, 100));
                default -> {
                    String v = String.valueOf(e.getValue()).trim().toUpperCase(java.util.Locale.ROOT);
                    if (!v.equals("FIRST") && !v.equals("LAST")) {
                        throw BusinessException.badRequest(ErrorCode.VALIDATION_FAILED,
                                "settings.mobileOddFullWidthItem chỉ nhận FIRST | LAST", e.getValue());
                    }
                    out.put("mobileOddFullWidthItem", v);
                }
            }
        }
        return out.isEmpty() ? null : out;
    }

    /** Số nguyên trong [min, max]; ném 400 nếu không phải số nguyên hoặc ngoài khoảng. */
    static int intOf(Object v, String field, int min, int max) {
        if (!(v instanceof Number n) || n.doubleValue() != Math.floor(n.doubleValue())) {
            throw BusinessException.badRequest(ErrorCode.VALIDATION_FAILED, field + " phải là số nguyên", v);
        }
        long l = n.longValue();
        if (l < min || l > max) {
            throw BusinessException.badRequest(ErrorCode.VALIDATION_FAILED,
                    field + " phải trong khoảng " + min + ".." + max, v);
        }
        return (int) l;
    }

    // ------------------------------------------------------------------ render

    /** Layout đang hiệu lực: ACTIVE hoặc SCHEDULED, now trong [activeFrom, activeTo). Ưu tiên ACTIVE, rồi layout mới hơn. */
    public static boolean inEffect(BannerPlacementLayoutEntity l, Date now) {
        boolean statusOk = BannerPlacementLayoutEntity.ACTIVE.equals(l.getStatus())
                || BannerPlacementLayoutEntity.SCHEDULED.equals(l.getStatus());
        return statusOk
                && (l.getActiveFrom() == null || !now.before(l.getActiveFrom()))
                && (l.getActiveTo() == null || now.before(l.getActiveTo()));
    }

    /**
     * Dựng render theo layout. Trả null nếu vị trí CHƯA có layout nào (để caller dùng cách cũ theo banners.placement_code).
     * Có layout nhưng không cái nào hiệu lực -> không có slot/banner.
     */
    public BannerPlacementRenderResponseDto render(BannerPlacementEntity p) {
        List<BannerPlacementLayoutEntity> all = layouts.findByPlacementId(p.getId());
        if (all == null || all.isEmpty()) {
            return null;
        }
        Date now = new Date();
        BannerPlacementLayoutEntity pick = null;
        for (BannerPlacementLayoutEntity l : all) { // id giảm dần
            if (!inEffect(l, now)) {
                continue;
            }
            if (pick == null || (BannerPlacementLayoutEntity.ACTIVE.equals(l.getStatus())
                    && !BannerPlacementLayoutEntity.ACTIVE.equals(pick.getStatus()))) {
                pick = l;
            }
        }
        List<Map<String, Object>> slots = new ArrayList<>();
        List<BannerResponseDto> flat = new ArrayList<>();
        if (pick != null) {
            buildSlots(p, pick, slots, flat);
        }
        BannerPlacementRenderResponseDto dto = new BannerPlacementRenderResponseDto(p.getCode(), p.getName(),
                p.getDisplayType(), flat);
        dto.setPlatform(p.getPlatform());
        dto.setSettings(readMap(p.getSettings()));
        dto.setLayoutSlots(slots);
        return dto;
    }

    private void buildSlots(BannerPlacementEntity p, BannerPlacementLayoutEntity layout,
                            List<Map<String, Object>> slots, List<BannerResponseDto> flat) {
        Map<String, List<BannerPlacementLayoutItemEntity>> bySlot = new LinkedHashMap<>();
        for (BannerPlacementLayoutItemEntity it : items.findByLayoutId(layout.getId())) {
            bySlot.computeIfAbsent(it.getSlotKey(), k -> new ArrayList<>()).add(it);
        }
        List<Map<String, Object>> built = new ArrayList<>();
        for (Map.Entry<String, List<BannerPlacementLayoutItemEntity>> e : bySlot.entrySet()) {
            List<BannerPlacementLayoutItemEntity> list = new ArrayList<>(e.getValue());
            list.sort(Comparator.comparingInt(BannerPlacementLayoutItemEntity::getPriority)
                    .thenComparing(BannerPlacementLayoutItemEntity::getId));
            Map<String, Object> meta = null;
            List<Map<String, Object>> rendered = new ArrayList<>();
            List<BannerResponseDto> slotBanners = new ArrayList<>();
            for (BannerPlacementLayoutItemEntity it : list) {
                Map<String, Object> m = readMap(it.getDisplayMetadata());
                if (meta == null && m != null) {
                    meta = m; // layout của slot lấy từ item đầu tiên (priority nhỏ nhất) có metadata
                }
                if (!it.isActive()) {
                    continue;
                }
                BannerEntity b = banners.findOne(it.getBannerId());
                if (b == null || b.getDeletedDate() != null || !BannerEntity.ACTIVE.equals(b.getStatus())) {
                    continue;
                }
                BannerResponseDto bd = toBannerDto(b);
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("order_index", rendered.size());
                row.put("banner", bd);
                rendered.add(row);
                slotBanners.add(bd);
            }
            if (rendered.isEmpty()) {
                continue; // slot không còn banner hiển thị được thì bỏ khỏi kết quả
            }
            flat.addAll(slotBanners);
            Map<String, Object> m = meta == null ? Map.of() : meta;
            Map<String, Object> lm = new LinkedHashMap<>();
            lm.put("order", m.getOrDefault("order", built.size()));
            lm.put("x", m.getOrDefault("x", 0));
            lm.put("y", m.getOrDefault("y", 0));
            lm.put("width", m.getOrDefault("width", 1));
            lm.put("height", m.getOrDefault("height", 1));
            String declared = m.get("slotType") == null ? null : String.valueOf(m.get("slotType"));
            Map<String, Object> slot = new LinkedHashMap<>();
            slot.put("id", p.getCode() + "_" + e.getKey());
            slot.put("slot_key", e.getKey());
            slot.put("slot_type", declared != null ? declared : (rendered.size() > 1 ? "MULTIPLE_IMAGE" : "SINGLE_IMAGE"));
            slot.put("layout_metadata", lm);
            slot.put("banners", rendered);
            built.add(slot);
        }
        built.sort(Comparator.comparingInt((Map<String, Object> s) -> ((Number) ((Map<?, ?>) s.get("layout_metadata"))
                .get("order")).intValue()));
        slots.addAll(built);
        // flat theo thứ tự slot cuối cùng
        flat.clear();
        for (Map<String, Object> s : built) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> rows = (List<Map<String, Object>>) s.get("banners");
            for (Map<String, Object> r : rows) {
                flat.add((BannerResponseDto) r.get("banner"));
            }
        }
    }
}
