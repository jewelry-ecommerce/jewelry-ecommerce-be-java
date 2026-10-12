package com.pk.core.model.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Kết quả render một vị trí banner: thông tin vị trí + các banner ACTIVE theo thứ tự hiển thị.
 * FE BannerPlacement {id, code, name, type, settings, slots[]}: nếu vị trí có layout hiệu lực thì {@code slots} là các slot
 * thật (slot_key, slot_type, layout_metadata, banners[order_index, banner]); chưa có layout nào thì gom hết vào MỘT slot "main".
 */
@Getter
@Setter
@NoArgsConstructor
public class BannerPlacementRenderResponseDto {

    private String code;
    private String name;
    private String displayType;
    private List<BannerResponseDto> banners;
    private String platform;
    private Map<String, Object> settings;
    /** Slot dựng từ layout; null = dùng cách cũ (một slot "main"). */
    @JsonIgnore
    private List<Map<String, Object>> layoutSlots;

    public BannerPlacementRenderResponseDto(String code, String name, String displayType, List<BannerResponseDto> banners) {
        this.code = code;
        this.name = name;
        this.displayType = displayType;
        this.banners = banners;
    }

    public String getId() {
        return code;
    }

    public String getType() {
        return displayType;
    }

    public List<Map<String, Object>> getSlots() {
        if (layoutSlots != null) {
            return layoutSlots;
        }
        List<Map<String, Object>> items = new ArrayList<>();
        int i = 0;
        for (BannerResponseDto b : banners == null ? List.<BannerResponseDto>of() : banners) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("order_index", i++);
            item.put("banner", b);
            items.add(item);
        }
        if (items.isEmpty()) {
            return List.of();
        }
        Map<String, Object> layout = new LinkedHashMap<>();
        layout.put("order", 0);
        layout.put("x", 0);
        layout.put("y", 0);
        layout.put("width", 1);
        layout.put("height", 1);
        Map<String, Object> slot = new LinkedHashMap<>();
        slot.put("id", code + "_main");
        slot.put("slot_key", "main");
        slot.put("slot_type", items.size() > 1 ? "MULTIPLE_IMAGE" : "SINGLE_IMAGE");
        slot.put("layout_metadata", layout);
        slot.put("banners", items);
        return List.of(slot);
    }
}
