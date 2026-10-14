package com.pk.core.business.service.impl;

import com.pk.core.business.repository.BannerPlacementLayoutItemRepo;
import com.pk.core.business.repository.BannerPlacementLayoutRepo;
import com.pk.core.business.repository.BannerPlacementRepo;
import com.pk.core.business.repository.BannerRepo;
import com.pk.core.business.service.BannerLayoutService;
import com.pk.core.common.exception.BusinessException;
import com.pk.core.common.exception.ErrorCode;
import com.pk.core.common.exception.ResourceNotFoundException;
import com.pk.core.model.dto.request.AdminBannerLayoutItemRequestDto;
import com.pk.core.model.dto.request.AdminBannerLayoutRequestDto;
import com.pk.core.model.dto.response.BannerLayoutItemResponseDto;
import com.pk.core.model.dto.response.BannerLayoutResponseDto;
import com.pk.core.model.entity.BannerEntity;
import com.pk.core.model.entity.BannerPlacementEntity;
import com.pk.core.model.entity.BannerPlacementLayoutEntity;
import com.pk.core.model.entity.BannerPlacementLayoutItemEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class BannerLayoutServiceImpl implements BannerLayoutService {

    private static final Set<String> STATUSES = Set.of("DRAFT", "SCHEDULED", "ACTIVE", "INACTIVE", "ARCHIVED");
    private static final Set<String> SLOT_TYPES = Set.of("SINGLE_IMAGE", "MULTIPLE_IMAGE");
    /** Tên kiểu layout của builder (có thể bị FE gửi nhầm vào slotType) -> tên chuẩn của layoutType. */
    private static final Map<String, String> LAYOUT_TYPES = Map.of("GRID", "GRID", "SLIDER", "SLIDER",
            "CAROUSEL", "SLIDER", "DOUBLE_BANNER", "DOUBLE_BANNER", "DOUBLE", "DOUBLE_BANNER");
    private static final Set<String> SINGLE_SLOT_TYPES = Set.of("SLIDER", "CAROUSEL", "POPUP", "SINGLE");
    static final String DEFAULT_SLOT_KEY = "main";
    private static final Pattern SLOT_KEY = Pattern.compile("[A-Za-z0-9_-]{1,60}");

    private final BannerPlacementRepo placements;
    private final BannerPlacementLayoutRepo layouts;
    private final BannerPlacementLayoutItemRepo items;
    private final BannerRepo banners;
    private final BannerLayoutSupport support;

    // ------------------------------------------------------------------ read

    @Transactional(readOnly = true)
    @Override
    public List<BannerLayoutResponseDto> list(Long placementId) {
        requirePlacement(placementId);
        return layouts.findByPlacementId(placementId).stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    @Override
    public BannerLayoutResponseDto detail(Long placementId, Long layoutId) {
        requirePlacement(placementId);
        return toDto(requireLayout(placementId, layoutId));
    }

    // ------------------------------------------------------------------ write

    @Transactional
    @Override
    public BannerLayoutResponseDto create(Long placementId, AdminBannerLayoutRequestDto req) {
        BannerPlacementEntity placement = requirePlacement(placementId);
        BannerPlacementLayoutEntity layout = new BannerPlacementLayoutEntity();
        layout.setPlacementId(placementId);
        applyHeader(layout, req, true);
        List<BannerPlacementLayoutItemEntity> newItems = buildItems(req.getItems());
        validateItems(placement, newItems);
        layouts.create(layout);
        saveItems(layout.getId(), newItems);
        activateIfNeeded(layout);
        return toDto(layout);
    }

    @Transactional
    @Override
    public BannerLayoutResponseDto update(Long placementId, Long layoutId, AdminBannerLayoutRequestDto req) {
        BannerPlacementEntity placement = requirePlacement(placementId);
        BannerPlacementLayoutEntity layout = requireLayout(placementId, layoutId);
        applyHeader(layout, req, false);
        if (req.getItems() != null) {
            List<BannerPlacementLayoutItemEntity> newItems = buildItems(req.getItems());
            validateItems(placement, newItems);
            items.deleteByLayoutId(layoutId); // chỉ xoá dòng gắn slot, banner còn nguyên
            saveItems(layoutId, newItems);
        }
        layout.touch();
        layouts.update(layout);
        activateIfNeeded(layout);
        return toDto(layout);
    }

    @Transactional
    @Override
    public BannerLayoutItemResponseDto patchItem(Long placementId, Long layoutId, Long itemId,
                                                 AdminBannerLayoutItemRequestDto req) {
        BannerPlacementEntity placement = requirePlacement(placementId);
        requireLayout(placementId, layoutId);
        BannerPlacementLayoutItemEntity target = requireItem(layoutId, itemId);
        List<BannerPlacementLayoutItemEntity> all = new ArrayList<>(items.findByLayoutId(layoutId));
        applyItem(target, req, false);
        // validate toàn layout với bản item đã sửa (thay đúng item cùng id)
        List<BannerPlacementLayoutItemEntity> view = new ArrayList<>();
        for (BannerPlacementLayoutItemEntity it : all) {
            view.add(it.getId().equals(target.getId()) ? target : it);
        }
        validateItems(placement, view);
        target.touch();
        items.update(target);
        return toItemDto(target);
    }

    @Transactional
    @Override
    public void deleteItem(Long placementId, Long layoutId, Long itemId) {
        requirePlacement(placementId);
        requireLayout(placementId, layoutId);
        items.delete(requireItem(layoutId, itemId));
    }

    // ------------------------------------------------------------------ build / apply

    private void applyHeader(BannerPlacementLayoutEntity l, AdminBannerLayoutRequestDto req, boolean creating) {
        if (req.getName() != null) {
            if (req.getName().isBlank()) {
                throw BusinessException.badRequest(ErrorCode.VALIDATION_FAILED, "name không được để trống");
            }
            l.setName(req.getName().trim());
        } else if (creating) {
            throw BusinessException.badRequest(ErrorCode.VALIDATION_FAILED, "name không được để trống");
        }
        if (req.getStatus() != null) {
            String s = req.getStatus().trim().toUpperCase(Locale.ROOT);
            if (!STATUSES.contains(s)) {
                throw BusinessException.badRequest(ErrorCode.VALIDATION_FAILED,
                        "status không hợp lệ: " + req.getStatus() + " (DRAFT | SCHEDULED | ACTIVE | INACTIVE | ARCHIVED)",
                        req.getStatus());
            }
            l.setStatus(s);
        }
        if (req.getActiveFrom() != null) {
            l.setActiveFrom(req.getActiveFrom());
        }
        if (req.getActiveTo() != null) {
            l.setActiveTo(req.getActiveTo());
        }
        if (req.getNote() != null) {
            l.setNote(req.getNote());
        }
        if (l.getActiveFrom() != null && l.getActiveTo() != null && !l.getActiveTo().after(l.getActiveFrom())) {
            throw BusinessException.badRequest(ErrorCode.VALIDATION_FAILED, "activeTo phải sau activeFrom");
        }
        if (BannerPlacementLayoutEntity.SCHEDULED.equals(l.getStatus()) && l.getActiveFrom() == null) {
            throw BusinessException.badRequest(ErrorCode.VALIDATION_FAILED, "Layout SCHEDULED cần activeFrom");
        }
    }

    private List<BannerPlacementLayoutItemEntity> buildItems(List<AdminBannerLayoutItemRequestDto> reqs) {
        List<BannerPlacementLayoutItemEntity> out = new ArrayList<>();
        if (reqs == null) {
            return out;
        }
        for (AdminBannerLayoutItemRequestDto r : reqs) {
            BannerPlacementLayoutItemEntity it = new BannerPlacementLayoutItemEntity();
            applyItem(it, r, true);
            out.add(it);
        }
        return out;
    }

    /** Ghi các field khác null của req vào item (tạo: slotKey & bannerId bắt buộc). */
    private void applyItem(BannerPlacementLayoutItemEntity it, AdminBannerLayoutItemRequestDto r, boolean creating) {
        if (r.getSlotKey() != null) {
            String key = r.getSlotKey().trim();
            if (!SLOT_KEY.matcher(key).matches()) {
                throw BusinessException.badRequest(ErrorCode.VALIDATION_FAILED,
                        "slotKey chỉ gồm chữ, số, gạch dưới/ngang (tối đa 60 ký tự)", r.getSlotKey());
            }
            it.setSlotKey(key);
        } else if (creating) {
            // Slider / banner đơn không có khái niệm slot: mọi slide dùng chung slot mặc định "main".
            it.setSlotKey(DEFAULT_SLOT_KEY);
        }
        if (r.getBannerId() != null) {
            requireBanner(r.getBannerId());
            it.setBannerId(r.getBannerId());
        } else if (creating) {
            throw BusinessException.badRequest(ErrorCode.VALIDATION_FAILED, "Mỗi item cần bannerId");
        }
        if (r.getIsActive() != null) {
            it.setActive(r.getIsActive());
        }
        if (r.getPriority() != null) {
            if (r.getPriority() < 0) {
                throw BusinessException.badRequest(ErrorCode.VALIDATION_FAILED, "priority phải >= 0", r.getPriority());
            }
            it.setPriority(r.getPriority());
        }
        if (r.getDisplayMetadata() != null) {
            it.setDisplayMetadata(support.writeMap(normalizeMetadata(r.getDisplayMetadata())));
        }
    }

    private void saveItems(Long layoutId, List<BannerPlacementLayoutItemEntity> list) {
        for (BannerPlacementLayoutItemEntity it : list) {
            it.setLayoutId(layoutId);
            items.create(it);
        }
    }

    /** Layout ACTIVE: các layout ACTIVE khác của vị trí chuyển INACTIVE (mỗi vị trí một layout ACTIVE). */
    private void activateIfNeeded(BannerPlacementLayoutEntity layout) {
        if (BannerPlacementLayoutEntity.ACTIVE.equals(layout.getStatus())) {
            layouts.deactivateOthers(layout.getPlacementId(), layout.getId());
        }
    }

    // ------------------------------------------------------------------ validate

    private Map<String, Object> normalizeMetadata(Map<String, Object> in) {
        Map<String, Object> out = new LinkedHashMap<>(in); // giữ cả key lạ, chỉ kiểm key đã biết
        for (String k : new String[]{"x", "y"}) {
            if (in.get(k) != null) {
                out.put(k, BannerLayoutSupport.intOf(in.get(k), "displayMetadata." + k, 0, 1000));
            }
        }
        for (String k : new String[]{"width", "height"}) {
            if (in.get(k) != null) {
                out.put(k, BannerLayoutSupport.intOf(in.get(k), "displayMetadata." + k, 1, 1000));
            }
        }
        if (in.get("order") != null) {
            out.put("order", BannerLayoutSupport.intOf(in.get("order"), "displayMetadata.order", 0, 100000));
        }
        // Hợp đồng: `layoutType` (GRID | SLIDER | DOUBLE_BANNER) là kiểu layout của builder; `slotType`
        // (SINGLE_IMAGE | MULTIPLE_IMAGE) chỉ nói slot chứa 1 hay nhiều ảnh. Nếu FE gửi tên layout vào slotType thì
        // tách ra layoutType (SLIDER => MULTIPLE_IMAGE; GRID/DOUBLE_BANNER => để backend suy ra theo số ảnh).
        if (in.get("layoutType") != null) {
            String lt = LAYOUT_TYPES.get(String.valueOf(in.get("layoutType")).trim().toUpperCase(Locale.ROOT));
            if (lt == null) {
                throw BusinessException.badRequest(ErrorCode.VALIDATION_FAILED,
                        "displayMetadata.layoutType chỉ nhận GRID | SLIDER | DOUBLE_BANNER", in.get("layoutType"));
            }
            out.put("layoutType", lt);
        }
        if (in.get("slotType") != null) {
            String t = String.valueOf(in.get("slotType")).trim().toUpperCase(Locale.ROOT);
            if (LAYOUT_TYPES.containsKey(t)) {
                String lt = LAYOUT_TYPES.get(t);
                out.putIfAbsent("layoutType", lt);
                if ("SLIDER".equals(lt)) {
                    out.put("slotType", "MULTIPLE_IMAGE");
                } else {
                    out.remove("slotType");
                }
            } else if (SLOT_TYPES.contains(t)) {
                out.put("slotType", t);
            } else {
                throw BusinessException.badRequest(ErrorCode.VALIDATION_FAILED,
                        "displayMetadata.slotType chỉ nhận SINGLE_IMAGE | MULTIPLE_IMAGE (kiểu layout GRID | SLIDER | DOUBLE_BANNER"
                                + " gửi ở displayMetadata.layoutType)", in.get("slotType"));
            }
        }
        if (in.get("childBannerIds") != null) {
            if (!(in.get("childBannerIds") instanceof List<?> ids)) {
                throw BusinessException.badRequest(ErrorCode.VALIDATION_FAILED,
                        "displayMetadata.childBannerIds phải là mảng id banner");
            }
            List<Long> norm = new ArrayList<>();
            for (Object o : ids) {
                if (!(o instanceof Number n)) {
                    throw BusinessException.badRequest(ErrorCode.VALIDATION_FAILED,
                            "displayMetadata.childBannerIds chỉ gồm số", o);
                }
                requireBanner(n.longValue());
                norm.add(n.longValue());
            }
            out.put("childBannerIds", norm);
        }
        return out;
    }

    /** Ràng buộc theo displayType của vị trí + trùng (slotKey, bannerId) + slotType SINGLE_IMAGE. */
    private void validateItems(BannerPlacementEntity placement, List<BannerPlacementLayoutItemEntity> list) {
        Set<String> pairs = new HashSet<>();
        Map<String, List<BannerPlacementLayoutItemEntity>> bySlot = new LinkedHashMap<>();
        for (BannerPlacementLayoutItemEntity it : list) {
            if (!pairs.add(it.getSlotKey() + "#" + it.getBannerId())) {
                throw BusinessException.badRequest(ErrorCode.VALIDATION_FAILED,
                        "Banner " + it.getBannerId() + " đã có trong slot " + it.getSlotKey(), it.getBannerId());
            }
            bySlot.computeIfAbsent(it.getSlotKey(), k -> new ArrayList<>()).add(it);
        }
        String type = placement.getDisplayType();
        if (type != null && SINGLE_SLOT_TYPES.contains(type) && bySlot.size() > 1) {
            throw BusinessException.badRequest(ErrorCode.VALIDATION_FAILED,
                    "Vị trí " + type + " chỉ có 1 slot, nhận " + bySlot.size() + ": " + new LinkedHashSet<>(bySlot.keySet()));
        }
        if ("DOUBLE_BANNER".equals(type) && bySlot.size() > 2) {
            throw BusinessException.badRequest(ErrorCode.VALIDATION_FAILED,
                    "DOUBLE_BANNER tối đa 2 slot độc lập, nhận " + bySlot.size() + ": " + new LinkedHashSet<>(bySlot.keySet()));
        }
        Map<String, Object> settings = support.readMap(placement.getSettings());
        for (Map.Entry<String, List<BannerPlacementLayoutItemEntity>> e : bySlot.entrySet()) {
            List<BannerPlacementLayoutItemEntity> slotItems = new ArrayList<>(e.getValue());
            slotItems.sort(Comparator.comparingInt(BannerPlacementLayoutItemEntity::getPriority));
            Map<String, Object> meta = null;
            for (BannerPlacementLayoutItemEntity it : slotItems) {
                Map<String, Object> m = support.readMap(it.getDisplayMetadata());
                if (m != null) {
                    meta = m;
                    break;
                }
            }
            if (meta != null && "SINGLE_IMAGE".equals(meta.get("slotType")) && slotItems.size() > 1) {
                throw BusinessException.badRequest(ErrorCode.VALIDATION_FAILED,
                        "Slot " + e.getKey() + " là SINGLE_IMAGE nhưng có " + slotItems.size() + " banner");
            }
            if ("GRID".equals(type)) {
                validateGridSlot(e.getKey(), meta, settings);
            }
        }
    }

    private static void validateGridSlot(String slotKey, Map<String, Object> meta, Map<String, Object> settings) {
        if (meta == null || meta.get("x") == null || meta.get("y") == null
                || meta.get("width") == null || meta.get("height") == null) {
            throw BusinessException.badRequest(ErrorCode.VALIDATION_FAILED,
                    "Slot " + slotKey + " của GRID cần displayMetadata x, y, width, height");
        }
        int x = ((Number) meta.get("x")).intValue();
        int y = ((Number) meta.get("y")).intValue();
        int w = ((Number) meta.get("width")).intValue();
        int h = ((Number) meta.get("height")).intValue();
        if (settings != null && settings.get("columns") instanceof Number cols && x + w > cols.intValue()) {
            throw BusinessException.badRequest(ErrorCode.VALIDATION_FAILED,
                    "Slot " + slotKey + " vượt số cột: x + width = " + (x + w) + " > columns " + cols.intValue());
        }
        if (settings != null && settings.get("rows") instanceof Number rows && y + h > rows.intValue()) {
            throw BusinessException.badRequest(ErrorCode.VALIDATION_FAILED,
                    "Slot " + slotKey + " vượt số hàng: y + height = " + (y + h) + " > rows " + rows.intValue());
        }
    }

    // ------------------------------------------------------------------ lookup / map

    private BannerPlacementEntity requirePlacement(Long id) {
        BannerPlacementEntity p = id == null ? null : placements.findOne(id);
        if (p == null) {
            throw new ResourceNotFoundException("Vị trí banner", "BannerPlacement", id);
        }
        return p;
    }

    private BannerPlacementLayoutEntity requireLayout(Long placementId, Long layoutId) {
        BannerPlacementLayoutEntity l = layoutId == null ? null : layouts.findOne(layoutId);
        if (l == null || !placementId.equals(l.getPlacementId())) {
            throw new ResourceNotFoundException("Layout banner", "BannerPlacementLayout", layoutId);
        }
        return l;
    }

    private BannerPlacementLayoutItemEntity requireItem(Long layoutId, Long itemId) {
        BannerPlacementLayoutItemEntity it = itemId == null ? null : items.findOne(itemId);
        if (it == null || !layoutId.equals(it.getLayoutId())) {
            throw new ResourceNotFoundException("Banner trong layout", "BannerPlacementLayoutItem", itemId);
        }
        return it;
    }

    private BannerEntity requireBanner(Long id) {
        BannerEntity b = banners.findOne(id);
        if (b == null || b.getDeletedDate() != null) {
            throw new ResourceNotFoundException("Banner", "Banner", id);
        }
        return b;
    }

    private BannerLayoutResponseDto toDto(BannerPlacementLayoutEntity l) {
        BannerLayoutResponseDto d = new BannerLayoutResponseDto();
        d.setId(String.valueOf(l.getId()));
        d.setPlacementId(String.valueOf(l.getPlacementId()));
        d.setName(l.getName());
        d.setStatus(l.getStatus());
        d.setActiveFrom(l.getActiveFrom());
        d.setActiveTo(l.getActiveTo());
        d.setNote(l.getNote());
        d.setCreatedDate(l.getCreatedDate());
        d.setUpdatedDate(l.getUpdatedDate());
        d.setItems(items.findByLayoutId(l.getId()).stream().map(this::toItemDto).toList());
        return d;
    }

    private BannerLayoutItemResponseDto toItemDto(BannerPlacementLayoutItemEntity it) {
        BannerLayoutItemResponseDto d = new BannerLayoutItemResponseDto();
        d.setId(String.valueOf(it.getId()));
        d.setLayoutId(String.valueOf(it.getLayoutId()));
        d.setSlotKey(it.getSlotKey());
        d.setBannerId(String.valueOf(it.getBannerId()));
        d.setIsActive(it.isActive());
        d.setPriority(it.getPriority());
        d.setDisplayMetadata(support.readMap(it.getDisplayMetadata()));
        BannerEntity b = banners.findOne(it.getBannerId());
        d.setBanner(b == null || b.getDeletedDate() != null ? null : support.toBannerDto(b));
        return d;
    }
}
