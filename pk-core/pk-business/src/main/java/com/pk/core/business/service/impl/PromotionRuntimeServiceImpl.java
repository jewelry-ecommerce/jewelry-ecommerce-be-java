package com.pk.core.business.service.impl;

import com.pk.core.business.repository.PromoDraftComponentRepo;
import com.pk.core.business.repository.PromoDraftRepo;
import com.pk.core.business.repository.PromoPublishedRepo;
import com.pk.core.business.repository.PromoSchemaComponentRepo;
import com.pk.core.business.repository.PromoSchemaRepo;
import com.pk.core.business.repository.PromoVoucherCodeRepo;
import com.pk.core.business.repository.UserRepo;
import com.pk.core.business.repository.VoucherRepo;
import com.pk.core.business.service.PromotionRuntimeService;
import com.pk.core.common.exception.ResourceNotFoundException;
import com.pk.core.common.util.PhoneUtil;
import com.pk.core.model.entity.PromoDraftEntity;
import com.pk.core.model.entity.PromoPublishedEntity;
import com.pk.core.model.entity.PromoSchemaComponentEntity;
import com.pk.core.model.entity.PromoSchemaEntity;
import com.pk.core.model.entity.PromoVoucherCodeEntity;
import com.pk.core.model.entity.UserEntity;
import com.pk.core.model.entity.VoucherEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PromotionRuntimeServiceImpl implements PromotionRuntimeService {

    private static final String TENANT = PromotionCampaignServiceImpl.TENANT;

    private final PromoDraftRepo drafts;
    private final PromoDraftComponentRepo draftComponents;
    private final PromoPublishedRepo published;
    private final PromoSchemaRepo schemas;
    private final PromoSchemaComponentRepo schemaComponents;
    private final PromoVoucherCodeRepo voucherCodes;
    private final VoucherRepo vouchers;
    private final UserRepo users;

    // ------------------------------------------------------------------ mô hình nội bộ

    private static final class Line {
        String id;
        String skuId;
        long qty;
        BigDecimal unit;
        BigDecimal subtotal;
        Set<String> cats = new HashSet<>();
        Set<String> cols = new HashSet<>();
    }

    private static final class Cand {
        String source;
        String draftId;
        String promotionId;
        String snapshotId;
        String promotionName;
        PromoSchemaEntity schema;
        Date from;
        Date to;
        Map<String, Object> target;
        Map<String, Object> benefit;
        Map<String, Object> condition;
        PromoVoucherCodeEntity voucher;

        boolean isVoucher() {
            return "VOUCHER".equals(schema.getSchemaType());
        }
    }

    private static final class Adj {
        Cand c;
        Line line;
        BigDecimal discount = BigDecimal.ZERO;
        BigDecimal effective;
        boolean selected;
        List<String> reasons = new ArrayList<>();
    }

    private static final class Eval {
        Cand c;
        List<Line> eligible = new ArrayList<>();
        List<String> targetReasons = new ArrayList<>();
        List<Map<String, Object>> keys = new ArrayList<>();
        boolean condPassed;
        String condReason;
        List<Adj> adjs = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
    }

    private static final class Run {
        List<Eval> evals = new ArrayList<>();
        List<Adj> selected = new ArrayList<>();
        List<Adj> rejected = new ArrayList<>();
        BigDecimal totalSelected = BigDecimal.ZERO;
    }

    // ================================================================== API

    @Transactional(readOnly = true)
    @Override
    public Map<String, Object> evaluate(Map<String, Object> req) {
        Date at = evaluatedAt(req.get("evaluatedAt"));
        List<Line> lines = lines(req.get("lines"));
        Set<String> coupons = coupons(req.get("couponCodes"));
        Run run = run(publishedCands(at), lines, coupons);
        return response(at, run);
    }

    @Transactional(readOnly = true)
    @Override
    public Map<String, Object> simulate(String draftId, Map<String, Object> req) {
        PromoDraftEntity d = draft(draftId);
        Date at = evaluatedAt(req.get("evaluatedAt"));
        List<Line> lines = lines(req.get("lines"));
        Set<String> coupons = coupons(req.get("couponCodes"));
        List<Cand> base = publishedCands(at);
        List<Cand> all = new ArrayList<>(base);
        all.addAll(draftCands(d));
        Run baseRun = run(base, lines, coupons);
        Run proj = run(all, lines, coupons);

        Map<String, Object> out = response(at, proj);
        out.put("promotionEvaluationId", UUID.randomUUID().toString());
        Map<String, Object> sim = new LinkedHashMap<>();
        sim.put("correlationId", UUID.randomUUID().toString());
        sim.put("persisted", false);
        sim.put("sourceDraftId", String.valueOf(d.getId()));
        sim.put("baselineTotalSelectedDiscountAmountMinor", m(baseRun.totalSelected));
        sim.put("projectedTotalSelectedDiscountAmountMinor", m(proj.totalSelected));
        sim.put("discountDeltaMinor", m(proj.totalSelected.subtract(baseRun.totalSelected)));
        sim.put("baselineSelectedReferences", baseRun.selected.stream().map(this::reference).toList());
        List<Map<String, Object>> sources = new ArrayList<>();
        for (Eval e : proj.evals) {
            Map<String, Object> s = new LinkedHashMap<>();
            s.put("sourceType", e.c.source);
            s.put("sourceDraftId", e.c.draftId);
            s.put("draftSchemaId", "DRAFT".equals(e.c.source) ? String.valueOf(e.c.schema.getId()) : null);
            s.put("promotionId", e.c.promotionId);
            s.put("promotionSchemaId", String.valueOf(e.c.schema.getId()));
            s.put("promotionSnapshotId", e.c.snapshotId);
            s.put("scope", e.c.schema.getScope());
            s.put("schemaType", e.c.schema.getSchemaType());
            sources.add(s);
        }
        sim.put("candidateSources", sources);
        sim.put("conflicts", conflicts(proj, baseRun));
        out.put("simulation", sim);
        return out;
    }

    @Transactional(readOnly = true)
    @Override
    public Map<String, Object> simulationVouchers(String draftId, Map<String, Object> req) {
        PromoDraftEntity d = draft(draftId);
        Date at = evaluatedAt(req.get("evaluatedAt"));
        List<Map<String, Object>> pub = new ArrayList<>();
        List<Map<String, Object>> dr = new ArrayList<>();
        for (Cand c : publishedCands(at)) {
            if (c.isVoucher() && "PUBLIC".equals(visibility(c.voucher))) {
                pub.add(simulationItem(c, false));
            }
        }
        for (Cand c : draftCands(d)) {
            if (c.isVoucher() && c.voucher != null) {
                dr.add(simulationItem(c, false));
            }
        }
        List<Map<String, Object>> sections = new ArrayList<>();
        if (!dr.isEmpty()) {
            sections.add(section("DRAFT", "DRAFT", "Voucher của bản nháp", null, dr));
        }
        if (!pub.isEmpty()) {
            sections.add(section("PUBLISHED", "PUBLISHED", "Voucher đang chạy", null, pub));
        }
        return Map.of("sections", sections);
    }

    @Transactional(readOnly = true)
    @Override
    public Map<String, Object> discoverVouchers(Map<String, Object> req) {
        Map<String, Object> ctx = context(req);
        Date at = evaluatedAt(ctx.get("evaluatedAt"));
        List<Line> lines = lines(ctx.get("lines"));
        Set<String> coupons = coupons(ctx.get("couponCodes"));
        List<Map<String, Object>> ok = new ArrayList<>();
        List<Map<String, Object>> blocked = new ArrayList<>();
        for (Cand c : publishedCands(at)) {
            if (!c.isVoucher() || c.voucher == null) {
                continue;
            }
            boolean visible = "PUBLIC".equals(visibility(c.voucher)) || coupons.contains(c.voucher.getNormalizedCode());
            if (!visible) {
                continue;
            }
            String problem = lines.isEmpty() ? null : voucherProblem(c, lines);
            Map<String, Object> card = card(c, problem);
            (problem == null ? ok : blocked).add(card);
        }
        List<Map<String, Object>> sections = new ArrayList<>();
        if (!ok.isEmpty()) {
            sections.add(section("AVAILABLE", "AVAILABLE", "Voucher có thể dùng", null, ok));
        }
        if (!blocked.isEmpty()) {
            sections.add(section("UNAVAILABLE", "UNAVAILABLE", "Chưa đủ điều kiện", null, blocked));
        }
        return Map.of("sections", sections);
    }

    @Transactional(readOnly = true)
    @Override
    public Map<String, Object> validateCode(Map<String, Object> req) {
        String code = PromoJson.str(req.get("code"));
        String norm = code == null ? "" : code.toUpperCase(Locale.ROOT);
        Map<String, Object> ctx = context(req);
        Date at = evaluatedAt(ctx.get("evaluatedAt"));
        List<Line> lines = lines(ctx.get("lines"));
        if (norm.isEmpty()) {
            return verdict(false, "VOUCHER_CODE_REQUIRED", "Vui lòng nhập mã voucher", null);
        }
        PromoVoucherCodeEntity vc = voucherCodes.findAll(Sort.unsorted()).stream().filter(x -> norm.equals(x.getNormalizedCode())).findFirst().orElse(null);
        if (vc != null) {
            Cand c = candidateOf(vc);
            if (c == null) {
                return verdict(false, "VOUCHER_NOT_FOUND", "Mã voucher không tồn tại", null);
            }
            String state = voucherState(c, at);
            if (state != null) {
                return verdict(false, state, stateMessage(state), card(c, stateMessage(state)));
            }
            String problem = lines.isEmpty() ? null : voucherProblem(c, lines);
            if (problem != null) {
                return verdict(false, problem.startsWith("Đơn") ? "VOUCHER_MIN_ORDER_NOT_MET" : "VOUCHER_NO_ELIGIBLE_ITEMS", problem, card(c, problem));
            }
            return verdict(true, "VOUCHER_VALID", "Mã voucher hợp lệ", card(c, null));
        }
        VoucherEntity legacy = vouchers.findByCode(norm);
        if (legacy == null || !VoucherEntity.PUBLISHED.equals(legacy.getStatus())) {
            return verdict(false, "VOUCHER_NOT_FOUND", "Mã voucher không tồn tại", null);
        }
        String legacyProblem = null;
        String legacyCode = null;
        if (at.before(legacy.getStartsAt())) {
            legacyCode = "VOUCHER_NOT_STARTED";
            legacyProblem = "Voucher chưa tới thời gian áp dụng";
        } else if (at.after(legacy.getEndsAt())) {
            legacyCode = "VOUCHER_EXPIRED";
            legacyProblem = "Voucher đã hết hạn";
        } else if (!legacy.isUsageAvailable()) {
            legacyCode = "VOUCHER_USAGE_LIMIT_REACHED";
            legacyProblem = "Voucher đã hết lượt sử dụng";
        } else if (!lines.isEmpty() && subtotal(lines).compareTo(legacy.getMinOrderValue()) < 0) {
            legacyCode = "VOUCHER_MIN_ORDER_NOT_MET";
            legacyProblem = "Đơn hàng chưa đạt giá trị tối thiểu " + PromotionAuthoringCatalog.vnd(legacy.getMinOrderValue());
        }
        Map<String, Object> card = legacyCard(legacy, legacyProblem);
        return legacyProblem == null ? verdict(true, "VOUCHER_VALID", "Mã voucher hợp lệ", card) : verdict(false, legacyCode, legacyProblem, card);
    }

    @Transactional(readOnly = true)
    @Override
    public Map<String, Object> customerByPhone(String phone) {
        String norm = PhoneUtil.normalize(phone);
        UserEntity u = norm == null ? null : users.findByPhone(norm);
        if (u == null) {
            throw new ResourceNotFoundException("Khách hàng", "Customer", phone);
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("customerId", String.valueOf(u.getId()));
        m.put("userId", String.valueOf(u.getId()));
        m.put("memberId", String.valueOf(u.getId()));
        m.put("phone", u.getPhone());
        m.put("fullName", u.getFullName());
        m.put("status", u.isEnabled() ? "ACTIVE" : "INACTIVE");
        return m;
    }

    // ================================================================== ỨNG VIÊN

    private List<Cand> publishedCands(Date at) {
        List<Cand> out = new ArrayList<>();
        for (PromoPublishedEntity pub : published.findAll(Sort.unsorted())) {
            if (!"ACTIVE".equals(PromotionCampaignServiceImpl.publishedStatus(pub)) || at.before(pub.getStartsAt()) || at.after(pub.getEndsAt())) {
                continue;
            }
            PromoDraftEntity d = drafts.findOne(pub.getDraftId());
            Map<String, Object> target = targetOf(d.getId());
            if (target == null) {
                continue;
            }
            for (PromoSchemaEntity s : schemasOf(d.getId())) {
                if (!"ENABLED".equals(s.getStatus()) || s.getUnpublishedAt() != null) {
                    continue;
                }
                Cand c = cand("PUBLISHED", d, String.valueOf(pub.getId()), String.valueOf(pub.getId()), pub.getName(), s, target);
                if (c == null || (s.getSchemaStartsAt() != null && at.before(s.getSchemaStartsAt())) || (s.getSchemaEndsAt() != null && at.after(s.getSchemaEndsAt()))) {
                    continue;
                }
                if (c.isVoucher() && (c.voucher == null || !"ACTIVE".equals(c.voucher.getStatus())
                        || (c.voucher.getUsageStartsAt() != null && at.before(c.voucher.getUsageStartsAt()))
                        || (c.voucher.getUsageEndsAt() != null && at.after(c.voucher.getUsageEndsAt())))) {
                    continue;
                }
                out.add(c);
            }
        }
        out.sort(Comparator.comparingInt((Cand c) -> c.schema.getPriority()).thenComparing(c -> c.schema.getId()));
        return out;
    }

    private List<Cand> draftCands(PromoDraftEntity d) {
        List<Cand> out = new ArrayList<>();
        Map<String, Object> target = targetOf(d.getId());
        if (target == null) {
            return out;
        }
        for (PromoSchemaEntity s : schemasOf(d.getId())) {
            if (!"ENABLED".equals(s.getStatus())) {
                continue;
            }
            Cand c = cand("DRAFT", d, String.valueOf(d.getId()), "draft-" + d.getId(), d.getName(), s, target);
            if (c != null) {
                out.add(c);
            }
        }
        return out;
    }

    /** Ứng viên từ một mã voucher đã phát hành (null nếu chiến dịch chưa phát hành). */
    private Cand candidateOf(PromoVoucherCodeEntity vc) {
        PromoSchemaEntity s = schemas.findOne(vc.getSchemaId());
        PromoDraftEntity d = s == null ? null : drafts.findOne(s.getDraftId());
        PromoPublishedEntity pub = d == null || d.getPublishedId() == null ? null : published.findOne(d.getPublishedId());
        Map<String, Object> target = d == null ? null : targetOf(d.getId());
        if (pub == null || target == null) {
            return null;
        }
        return cand("PUBLISHED", d, String.valueOf(pub.getId()), String.valueOf(pub.getId()), pub.getName(), s, target);
    }

    private Cand cand(String source, PromoDraftEntity d, String promotionId, String snapshotId, String name, PromoSchemaEntity s, Map<String, Object> target) {
        Cand c = new Cand();
        c.source = source;
        c.draftId = "DRAFT".equals(source) ? String.valueOf(d.getId()) : null;
        c.promotionId = promotionId;
        c.snapshotId = snapshotId;
        c.promotionName = name;
        c.schema = s;
        c.target = target;
        c.from = s.getSchemaStartsAt() != null && s.getSchemaStartsAt().after(d.getStartsAt()) ? s.getSchemaStartsAt() : d.getStartsAt();
        c.to = s.getSchemaEndsAt() != null && s.getSchemaEndsAt().before(d.getEndsAt()) ? s.getSchemaEndsAt() : d.getEndsAt();
        for (PromoSchemaComponentEntity comp : schemaComponents.findAll(Sort.unsorted())) {
            if (comp.getSchemaId().equals(s.getId())) {
                if ("BENEFIT".equals(comp.getKind())) {
                    c.benefit = PromoJson.readMap(comp.getConfig());
                } else {
                    c.condition = PromoJson.readMap(comp.getConfig());
                }
            }
        }
        if (c.benefit == null) {
            return null;
        }
        if (c.isVoucher()) {
            c.voucher = voucherCodes.findAll(Sort.unsorted()).stream().filter(v -> s.getId().equals(v.getSchemaId())).findFirst().orElse(null);
            if (c.voucher != null && c.voucher.getUsageStartsAt() != null && c.voucher.getUsageStartsAt().after(c.from)) {
                c.from = c.voucher.getUsageStartsAt();
            }
            if (c.voucher != null && c.voucher.getUsageEndsAt() != null && c.voucher.getUsageEndsAt().before(c.to)) {
                c.to = c.voucher.getUsageEndsAt();
            }
        }
        return c;
    }

    private Map<String, Object> targetOf(Long draftId) {
        return draftComponents.findAll(Sort.unsorted()).stream().filter(x -> draftId.equals(x.getDraftId()) && "TARGET".equals(x.getKind()))
                .map(x -> PromoJson.readMap(x.getConfig())).findFirst().orElse(null);
    }

    private List<PromoSchemaEntity> schemasOf(Long draftId) {
        return schemas.findAll(Sort.unsorted()).stream().filter(s -> draftId.equals(s.getDraftId()))
                .sorted(Comparator.comparingInt(PromoSchemaEntity::getSortOrder)).toList();
    }

    private PromoDraftEntity draft(String id) {
        Long key = PromoJson.id(id);
        PromoDraftEntity d = key == null ? null : drafts.findOne(key);
        if (d == null) {
            throw new ResourceNotFoundException("Chiến dịch khuyến mãi", "PromotionDraft", id);
        }
        return d;
    }

    private static String visibility(PromoVoucherCodeEntity v) {
        Map<String, Object> p = PromoJson.readMap(v == null ? null : v.getPolicy());
        return p.get("visibilityType") == null ? "PUBLIC" : String.valueOf(p.get("visibilityType"));
    }

    // ================================================================== ENGINE

    private Run run(List<Cand> cands, List<Line> lines, Set<String> coupons) {
        Run run = new Run();
        List<Adj> itemAdjs = new ArrayList<>();
        for (Cand c : cands) {
            if (c.isVoucher()) {
                continue;
            }
            Eval e = new Eval();
            e.c = c;
            eligible(e, lines);
            e.condPassed = true;
            e.condReason = "CONDITION_ALWAYS_PASSED";
            for (Line l : e.eligible) {
                Adj a = new Adj();
                a.c = c;
                a.line = l;
                a.discount = unitDiscount(c.benefit, l.unit).multiply(BigDecimal.valueOf(l.qty));
                a.reasons.add(a.discount.signum() > 0 ? ("FLAT_PRICE".equals(c.benefit.get("type")) ? "BENEFIT_FLAT_PRICE_APPLIED" : "BENEFIT_MARKDOWN_APPLIED") : "BENEFIT_NO_DISCOUNT_PRODUCED");
                e.adjs.add(a);
                if (a.discount.signum() > 0) {
                    itemAdjs.add(a);
                }
            }
            run.evals.add(e);
        }
        // mỗi dòng hàng chỉ nhận khuyến mãi sản phẩm lớn nhất (hoà: bản đã phát hành trước, rồi ưu tiên nhỏ hơn)
        Map<String, Adj> winner = new LinkedHashMap<>();
        for (Adj a : itemAdjs) {
            Adj cur = winner.get(a.line.id);
            if (cur == null || better(a, cur)) {
                winner.put(a.line.id, a);
            }
        }
        Map<String, BigDecimal> effective = new LinkedHashMap<>();
        for (Line l : lines) {
            Adj w = winner.get(l.id);
            effective.put(l.id, w == null ? l.subtotal : l.subtotal.subtract(w.discount).max(BigDecimal.ZERO));
        }
        for (Adj a : itemAdjs) {
            a.selected = winner.get(a.line.id) == a;
            a.effective = effective.get(a.line.id);
            a.reasons.add(a.selected ? "LINE_ADJUSTMENT_SELECTED" : "LINE_ADJUSTMENT_REJECTED_BY_COMPATIBILITY");
            (a.selected ? run.selected : run.rejected).add(a);
            if (a.selected) {
                run.totalSelected = run.totalSelected.add(a.discount);
            }
        }
        for (Eval e : run.evals) {
            for (Adj a : e.adjs) {
                if (a.effective == null) {
                    a.effective = effective.get(a.line.id);
                }
                if (a.selected) {
                    e.total = e.total.add(a.discount);
                }
            }
        }
        // voucher: chỉ xét mã khách nhập; tối đa một voucher được chọn (engine checkout chỉ nhận một mã)
        Eval bestVoucher = null;
        List<Eval> voucherEvals = new ArrayList<>();
        for (Cand c : cands) {
            if (!c.isVoucher() || c.voucher == null || !coupons.contains(c.voucher.getNormalizedCode())) {
                continue;
            }
            Eval e = new Eval();
            e.c = c;
            eligible(e, lines);
            BigDecimal base = BigDecimal.ZERO;
            for (Line l : e.eligible) {
                base = base.add(effective.get(l.id));
            }
            BigDecimal min = c.condition != null && "TARGET_SUBTOTAL".equals(c.condition.get("type")) ? PromoJson.num(c.condition.get("amountMinor"), "amountMinor") : BigDecimal.ZERO;
            e.condPassed = !e.eligible.isEmpty() && base.compareTo(min) >= 0;
            e.condReason = "CONDITION_ALWAYS_PASSED";
            BigDecimal total = e.condPassed ? voucherDiscount(c.benefit, base) : BigDecimal.ZERO;
            BigDecimal allocated = BigDecimal.ZERO;
            for (int i = 0; i < e.eligible.size() && total.signum() > 0; i++) {
                Line l = e.eligible.get(i);
                BigDecimal part = i == e.eligible.size() - 1 ? total.subtract(allocated)
                        : total.multiply(effective.get(l.id)).divide(base, 0, RoundingMode.FLOOR);
                allocated = allocated.add(part);
                Adj a = new Adj();
                a.c = c;
                a.line = l;
                a.discount = part;
                a.effective = effective.get(l.id).subtract(part).max(BigDecimal.ZERO);
                a.reasons.add("BENEFIT_MARKDOWN_APPLIED");
                e.adjs.add(a);
            }
            e.total = total;
            voucherEvals.add(e);
            if (total.signum() > 0 && (bestVoucher == null || total.compareTo(bestVoucher.total) > 0)) {
                bestVoucher = e;
            }
        }
        for (Eval e : voucherEvals) {
            boolean win = e == bestVoucher;
            for (Adj a : e.adjs) {
                a.selected = win;
                a.reasons.add(win ? "LINE_ADJUSTMENT_SELECTED" : "LINE_ADJUSTMENT_REJECTED_BY_COMPATIBILITY");
                (win ? run.selected : run.rejected).add(a);
                if (win) {
                    run.totalSelected = run.totalSelected.add(a.discount);
                }
            }
            run.evals.add(e);
        }
        return run;
    }

    private static boolean better(Adj a, Adj cur) {
        int cmp = a.discount.compareTo(cur.discount);
        if (cmp != 0) {
            return cmp > 0;
        }
        if (!a.c.source.equals(cur.c.source)) {
            return "PUBLISHED".equals(a.c.source);
        }
        return a.c.schema.getPriority() < cur.c.schema.getPriority();
    }

    private void eligible(Eval e, List<Line> lines) {
        Map<String, Object> t = e.c.target;
        String type = String.valueOf(t.get("type"));
        Set<String> excluded = new HashSet<>(PromoJson.strings(t.get("excludedSkuIds")));
        Set<String> skuIds = new HashSet<>(PromoJson.strings(t.get("skuIds")));
        Set<String> catIds = new HashSet<>(PromoJson.strings(t.get("categoryIds")));
        Set<String> colIds = new HashSet<>(PromoJson.strings(t.get("collectionIds")));
        for (Line l : lines) {
            if (excluded.contains(l.skuId)) {
                continue;
            }
            switch (type) {
                case "ALL" -> {
                    e.eligible.add(l);
                    e.keys.add(key("ALL", "*", l.id));
                }
                case "MANUAL" -> {
                    if (skuIds.contains(l.skuId)) {
                        e.eligible.add(l);
                        e.keys.add(key("SKU_ID", l.skuId, l.id));
                    }
                }
                case "CATEGORY" -> {
                    for (String c : l.cats) {
                        if (catIds.contains(c)) {
                            e.eligible.add(l);
                            e.keys.add(key("CATEGORY_ID", c, l.id));
                            break;
                        }
                    }
                }
                default -> {
                    for (String c : l.cols) {
                        if (colIds.contains(c)) {
                            e.eligible.add(l);
                            e.keys.add(key("COLLECTION_ID", c, l.id));
                            break;
                        }
                    }
                }
            }
        }
        boolean hit = !e.eligible.isEmpty();
        e.targetReasons.add("TARGET_" + switch (type) {
            case "ALL" -> "ALL";
            case "MANUAL" -> "MANUAL";
            case "CATEGORY" -> "CATEGORY";
            default -> "COLLECTION";
        } + (hit ? "_MATCHED" : "ALL".equals(type) ? "_MATCHED" : "_NO_MATCH"));
    }

    private static Map<String, Object> key(String type, String value, String lineId) {
        Map<String, Object> k = new LinkedHashMap<>();
        k.put("keyType", type);
        k.put("keyValue", value);
        k.put("lineId", lineId);
        return k;
    }

    /** Cùng công thức PromotionEntity.computeUnitDiscount. */
    private static BigDecimal unitDiscount(Map<String, Object> b, BigDecimal unit) {
        if (unit == null || unit.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal d;
        if ("FLAT_PRICE".equals(b.get("type"))) {
            d = unit.subtract(PromoJson.num(b.get("fixedPriceAmountMinor"), "x"));
        } else if ("PERCENT".equals(b.get("discountKind"))) {
            d = unit.multiply(PromoJson.num(b.get("discountValue"), "x")).divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP);
            Map<String, Object> max = PromoJson.asMap(b.get("maxDiscountAmount"));
            if (max != null) {
                d = d.min(PromoJson.num(max.get("amountMinor"), "x"));
            }
        } else {
            d = PromoJson.num(b.get("discountValue"), "x");
        }
        return d.max(BigDecimal.ZERO).min(unit);
    }

    /** Cùng công thức VoucherEntity.computeDiscount. */
    private static BigDecimal voucherDiscount(Map<String, Object> b, BigDecimal subtotal) {
        BigDecimal raw = "PERCENT".equals(b.get("discountKind"))
                ? subtotal.multiply(PromoJson.num(b.get("discountValue"), "x")).divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP)
                : PromoJson.num(b.get("discountValue"), "x");
        Map<String, Object> max = PromoJson.asMap(b.get("maxDiscountAmount"));
        if (max != null && "PERCENT".equals(b.get("discountKind"))) {
            raw = raw.min(PromoJson.num(max.get("amountMinor"), "x"));
        }
        return raw.min(subtotal).max(BigDecimal.ZERO);
    }

    // ================================================================== PHẢN HỒI

    private Map<String, Object> response(Date at, Run run) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("tenantCode", TENANT);
        out.put("evaluatedAt", PromoJson.iso(at));
        List<Map<String, Object>> candidates = new ArrayList<>();
        List<Map<String, Object>> evaluations = new ArrayList<>();
        Set<String> reasonCodes = new LinkedHashSet<>();
        int applicable = 0;
        for (Eval e : run.evals) {
            Map<String, Object> cand = candidateHead(e.c);
            cand.put("priority", e.c.schema.getPriority());
            cand.put("startsAt", PromoJson.iso(e.c.from));
            cand.put("endsAt", PromoJson.iso(e.c.to));
            cand.put("matchedKeys", e.keys);
            cand.put("reasonCode", e.keys.isEmpty() ? "NO_INDEX_MATCH" : "CANDIDATE_INDEX_MATCHED");
            candidates.add(cand);

            boolean anySelected = e.adjs.stream().anyMatch(a -> a.selected);
            if (anySelected) {
                applicable++;
            }
            Map<String, Object> ev = candidateHead(e.c);
            ev.put("status", anySelected ? "APPLICABLE" : "REJECTED");
            List<String> rs = new ArrayList<>();
            rs.add(anySelected ? "LINE_ADJUSTMENT_SELECTED" : e.adjs.isEmpty() ? "BENEFIT_NO_DISCOUNT_PRODUCED" : "LINE_ADJUSTMENT_REJECTED_BY_COMPATIBILITY");
            ev.put("reasonCodes", rs);
            reasonCodes.addAll(rs);
            ev.put("matchedKeys", e.keys);
            ev.put("audience", Map.of("passed", true, "reasonCode", "AUDIENCE_ALL_PASSED"));
            ev.put("target", Map.of("eligibleLineIds", e.eligible.stream().map(l -> l.id).toList(), "reasonCodes", e.targetReasons));
            Map<String, Object> se = new LinkedHashMap<>();
            se.put("promotionSchemaId", String.valueOf(e.c.schema.getId()));
            se.put("promotionSchemaName", e.c.schema.getName());
            se.put("scope", e.c.schema.getScope());
            se.put("schemaType", e.c.schema.getSchemaType());
            se.put("priority", e.c.schema.getPriority());
            se.put("conditionPassed", e.condPassed);
            se.put("conditionReasonCode", e.condPassed ? "CONDITION_ALWAYS_PASSED" : "CONDITION_UNSUPPORTED_TYPE");
            se.put("benefitPassed", !e.adjs.isEmpty() && e.total.signum() >= 0 && e.adjs.stream().anyMatch(a -> a.discount.signum() > 0));
            se.put("benefitReasonCodes", e.adjs.stream().flatMap(a -> a.reasons.stream()).distinct().filter(r -> r.startsWith("BENEFIT")).toList());
            List<Map<String, Object>> lineAdjs = new ArrayList<>();
            for (Adj a : e.adjs) {
                Map<String, Object> la = new LinkedHashMap<>();
                la.put("lineId", a.line.id);
                la.put("skuId", a.line.skuId);
                la.put("quantity", a.line.qty);
                la.put("unitPriceMinor", m(a.line.unit));
                la.put("lineSubtotalMinor", m(a.line.subtotal));
                la.put("discountAmountMinor", m(a.discount));
                la.put("effectiveLineSubtotalMinor", m(a.effective == null ? a.line.subtotal : a.effective));
                la.put("benefitType", "BENEFIT");
                la.put("reasonCodes", a.reasons);
                lineAdjs.add(la);
            }
            se.put("lineAdjustments", lineAdjs);
            se.put("totalDiscountAmountMinor", m(e.total));
            ev.put("schemas", List.of(se));
            ev.put("totalDiscountAmountMinor", m(e.total));
            evaluations.add(ev);
        }
        out.put("candidates", candidates);
        out.put("evaluations", evaluations);
        Map<String, Object> sel = new LinkedHashMap<>();
        sel.put("selectedLineAdjustments", run.selected.stream().map(a -> selectedDto(a, true)).toList());
        sel.put("rejectedLineAdjustments", run.rejected.stream().map(a -> selectedDto(a, false)).toList());
        sel.put("totalSelectedDiscountAmountMinor", m(run.totalSelected));
        sel.put("selectedAdjustmentCount", run.selected.size());
        sel.put("rejectedAdjustmentCount", run.rejected.size());
        out.put("selection", sel);
        Map<String, Object> ex = new LinkedHashMap<>();
        ex.put("totalLookupKeys", run.evals.stream().mapToInt(e -> e.keys.size()).sum());
        ex.put("matchedCandidateCount", run.evals.size());
        ex.put("dedupedCandidateCount", run.evals.size());
        ex.put("evaluatedCandidateCount", run.evals.size());
        ex.put("applicableCandidateCount", applicable);
        ex.put("rejectedCandidateCount", run.evals.size() - applicable);
        ex.put("totalDiscountAmountMinor", m(run.totalSelected));
        ex.put("reasonCodes", new ArrayList<>(reasonCodes));
        out.put("explain", ex);
        return out;
    }

    private Map<String, Object> candidateHead(Cand c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("promotionId", c.promotionId);
        m.put("promotionSchemaId", String.valueOf(c.schema.getId()));
        m.put("promotionSnapshotId", c.snapshotId);
        m.put("promotionName", c.promotionName);
        m.put("promotionSchemaName", c.schema.getName());
        m.put("scope", c.schema.getScope());
        m.put("schemaType", c.schema.getSchemaType());
        return m;
    }

    private Map<String, Object> selectedDto(Adj a, boolean selected) {
        Map<String, Object> m = candidateHead(a.c);
        m.put("lineId", a.line.id);
        m.put("skuId", a.line.skuId);
        m.put("discountAmountMinor", m(a.discount));
        if (selected) {
            m.put("effectiveLineSubtotalMinor", m(a.effective == null ? a.line.subtotal : a.effective));
        }
        m.put("reasonCodes", a.reasons.stream().filter(r -> r.startsWith("LINE_")).toList());
        return m;
    }

    private Map<String, Object> reference(Adj a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("sourceType", a.c.source);
        m.put("sourceDraftId", a.c.draftId);
        m.put("promotionId", a.c.promotionId);
        m.put("promotionSchemaId", String.valueOf(a.c.schema.getId()));
        m.put("promotionSnapshotId", a.c.snapshotId);
        m.put("lineId", a.line.id);
        m.put("scope", a.c.schema.getScope());
        m.put("schemaType", a.c.schema.getSchemaType());
        m.put("discountAmountMinor", m(a.discount));
        m.put("reasonCodes", a.reasons.stream().filter(r -> r.startsWith("LINE_")).toList());
        return m;
    }

    /** So sánh bản nháp với những gì đang chạy: ai thắng trên từng dòng hàng. */
    private List<Map<String, Object>> conflicts(Run proj, Run base) {
        List<Map<String, Object>> out = new ArrayList<>();
        Set<String> draftSeen = new HashSet<>();
        for (Eval e : proj.evals) {
            if (!"DRAFT".equals(e.c.source)) {
                continue;
            }
            draftSeen.add(String.valueOf(e.c.schema.getId()));
            boolean effect = e.adjs.stream().anyMatch(a -> a.discount.signum() > 0);
            if (!effect) {
                Map<String, Object> c = conflict("DRAFT_NO_EFFECT", null, null, null, null, List.of(), BigDecimal.ZERO, BigDecimal.ZERO);
                c.put("reasonCodes", List.of("BENEFIT_NO_DISCOUNT_PRODUCED"));
                out.add(c);
                continue;
            }
            for (Adj a : e.adjs) {
                if (a.discount.signum() <= 0) {
                    continue;
                }
                Adj rival = (a.selected ? proj.rejected : proj.selected).stream()
                        .filter(x -> x.line.id.equals(a.line.id) && !x.c.source.equals(a.c.source) && x.c.isVoucher() == a.c.isVoucher()).findFirst().orElse(null);
                Adj baseWinner = base.selected.stream().filter(x -> x.line.id.equals(a.line.id) && x.c.isVoucher() == a.c.isVoucher()).findFirst().orElse(null);
                if (a.selected) {
                    String outcome = a.c.isVoucher() ? (baseWinner != null ? "DRAFT_VOUCHER_WINS" : "DRAFT_ADDS_DISCOUNT")
                            : (baseWinner != null ? "DRAFT_REPLACES_PUBLISHED" : "DRAFT_ADDS_DISCOUNT");
                    out.add(conflict(outcome, "DRAFT", baseWinner == null ? null : "PUBLISHED", a, baseWinner, List.of(a.line.id), a.discount,
                            baseWinner == null ? BigDecimal.ZERO : baseWinner.discount));
                } else if (rival != null) {
                    out.add(conflict(a.c.isVoucher() ? "PUBLISHED_VOUCHER_WINS" : "PUBLISHED_BLOCKS_DRAFT", "PUBLISHED", "DRAFT", rival, a, List.of(a.line.id), rival.discount, a.discount));
                }
            }
        }
        return out;
    }

    private Map<String, Object> conflict(String outcome, String winnerSource, String loserSource, Adj winner, Adj loser, List<String> lines, BigDecimal wd, BigDecimal ld) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("outcome", outcome);
        m.put("winnerSource", "PUBLISHED_BLOCKS_DRAFT".equals(outcome) || "PUBLISHED_VOUCHER_WINS".equals(outcome) ? "PUBLISHED" : winnerSource);
        m.put("loserSource", loserSource);
        m.put("winner", winner == null ? null : reference(winner));
        m.put("loser", loser == null ? null : reference(loser));
        m.put("affectedLineIds", lines);
        m.put("winnerDiscountAmountMinor", m(wd));
        m.put("loserDiscountAmountMinor", m(ld));
        m.put("reasonCodes", List.of());
        return m;
    }

    // ================================================================== VOUCHER

    private String voucherState(Cand c, Date at) {
        if (c.voucher == null || !"ACTIVE".equals(c.voucher.getStatus())) {
            return "VOUCHER_DISABLED";
        }
        PromoPublishedEntity pub = published.findOne(Long.valueOf(c.promotionId));
        if (pub == null || !"ACTIVE".equals(PromotionCampaignServiceImpl.publishedStatus(pub)) && !"SCHEDULED".equals(PromotionCampaignServiceImpl.publishedStatus(pub))
                || c.schema.getUnpublishedAt() != null || !"ENABLED".equals(c.schema.getStatus())) {
            return "VOUCHER_NOT_ACTIVE";
        }
        if (at.before(c.from)) {
            return "VOUCHER_NOT_STARTED";
        }
        if (at.after(c.to)) {
            return "VOUCHER_EXPIRED";
        }
        return null;
    }

    private static String stateMessage(String state) {
        return switch (state) {
            case "VOUCHER_DISABLED" -> "Mã voucher đã bị vô hiệu";
            case "VOUCHER_NOT_STARTED" -> "Voucher chưa tới thời gian áp dụng";
            case "VOUCHER_EXPIRED" -> "Voucher đã hết hạn";
            default -> "Chương trình voucher hiện không hoạt động";
        };
    }

    /** null = dùng được; ngược lại là thông báo lý do. */
    private String voucherProblem(Cand c, List<Line> lines) {
        Eval e = new Eval();
        e.c = c;
        eligible(e, lines);
        if (e.eligible.isEmpty()) {
            return "Giỏ hàng không có sản phẩm thuộc phạm vi áp dụng của voucher";
        }
        BigDecimal base = BigDecimal.ZERO;
        for (Line l : e.eligible) {
            base = base.add(l.subtotal);
        }
        if (c.condition != null && "TARGET_SUBTOTAL".equals(c.condition.get("type"))) {
            BigDecimal min = PromoJson.num(c.condition.get("amountMinor"), "amountMinor");
            if (base.compareTo(min) < 0) {
                return "Đơn hàng chưa đạt giá trị tối thiểu " + PromotionAuthoringCatalog.vnd(min);
            }
        }
        return null;
    }

    private Map<String, Object> card(Cand c, String problem) {
        PromoVoucherCodeEntity v = c.voucher;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("voucherCodeId", String.valueOf(v.getId()));
        m.put("promotionId", c.promotionId);
        m.put("promotionSchemaId", String.valueOf(c.schema.getId()));
        m.put("promotionName", c.promotionName);
        m.put("schemaType", "VOUCHER");
        m.put("code", v.getCode());
        m.put("codeMask", PromotionCampaignServiceImpl.mask(v.getCode()));
        m.put("image", v.getImageUrl());
        m.put("status", v.getStatus());
        m.put("title", c.schema.getName());
        m.put("subtitle", c.promotionName);
        m.put("conditionSummary", PromotionAuthoringCatalog.conditionSummary(c.condition));
        m.put("benefitSummary", PromotionAuthoringCatalog.benefitSummary(c.benefit, "VOUCHER"));
        m.put("startsAt", PromoJson.iso(c.from));
        m.put("endsAt", PromoJson.iso(c.to));
        m.put("disabled", problem != null);
        m.put("disabledMessage", problem);
        m.put("disabledReason", problem);
        m.put("reasonCodes", problem == null ? List.of() : List.of("VOUCHER_NOT_APPLICABLE"));
        return m;
    }

    private Map<String, Object> legacyCard(VoucherEntity v, String problem) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("voucherCodeId", "legacy-" + v.getId());
        m.put("promotionId", "legacy-" + v.getId());
        m.put("promotionSchemaId", "legacy-" + v.getId());
        m.put("promotionName", v.getCode());
        m.put("schemaType", "VOUCHER");
        m.put("code", v.getCode());
        m.put("codeMask", PromotionCampaignServiceImpl.mask(v.getCode()));
        m.put("status", VoucherEntity.PUBLISHED.equals(v.getStatus()) ? "ACTIVE" : "INACTIVE");
        m.put("title", v.getCode());
        m.put("subtitle", "Voucher");
        m.put("conditionSummary", v.getMinOrderValue() == null || v.getMinOrderValue().signum() == 0 ? "Áp dụng cho mọi đơn hàng"
                : "Đơn từ " + PromotionAuthoringCatalog.vnd(v.getMinOrderValue()));
        Map<String, Object> cfg = new LinkedHashMap<>();
        cfg.put("type", "VOUCHER");
        cfg.put("discountKind", VoucherEntity.PERCENT.equals(v.getDiscountType()) ? "PERCENT" : "FIXED_AMOUNT");
        cfg.put("discountValue", v.getDiscountValue());
        if (v.getMaxDiscountAmount() != null) {
            cfg.put("maxDiscountAmount", Map.of("amountMinor", v.getMaxDiscountAmount().toPlainString()));
        }
        m.put("benefitSummary", PromotionAuthoringCatalog.benefitSummary(cfg, "VOUCHER"));
        m.put("startsAt", PromoJson.iso(v.getStartsAt()));
        m.put("endsAt", PromoJson.iso(v.getEndsAt()));
        m.put("disabled", problem != null);
        m.put("disabledMessage", problem);
        m.put("disabledReason", problem);
        return m;
    }

    private Map<String, Object> simulationItem(Cand c, boolean disabled) {
        Map<String, Object> card = card(c, disabled ? "Không khả dụng" : null);
        card.remove("reasonCodes");
        card.remove("disabledReason");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("sourceType", c.source);
        m.put("sourceDraftId", c.draftId);
        m.putAll(card);
        return m;
    }

    private static Map<String, Object> section(String type, String key, String title, String subtitle, List<Map<String, Object>> items) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("groupType", type);
        m.put("groupKey", key);
        m.put("title", title);
        m.put("subtitle", subtitle);
        m.put("items", items);
        return m;
    }

    private static Map<String, Object> verdict(boolean valid, String reasonCode, String message, Map<String, Object> card) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("valid", valid);
        m.put("reasonCode", reasonCode);
        m.put("reasonCodes", List.of(reasonCode));
        m.put("message", message);
        m.put("card", card);
        return m;
    }

    // ================================================================== PARSE

    /** FE gửi checkoutContext; form tạo đơn admin gửi items/customerId/couponCodes ở gốc. */
    private Map<String, Object> context(Map<String, Object> req) {
        Map<String, Object> ctx = PromoJson.asMap(req.get("checkoutContext"));
        if (ctx != null) {
            return ctx;
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("couponCodes", req.get("couponCodes"));
        out.put("evaluatedAt", req.get("evaluatedAt"));
        if (req.get("items") instanceof List<?> items) {
            List<Object> lines = new ArrayList<>();
            int i = 1;
            for (Object o : items) {
                Map<String, Object> it = PromoJson.asMap(o);
                if (it == null) {
                    continue;
                }
                Map<String, Object> l = new LinkedHashMap<>();
                l.put("lineId", "line-" + i++);
                l.put("skuId", it.get("variationId") != null ? it.get("variationId") : it.get("skuId"));
                l.put("quantity", it.get("quantity"));
                l.put("unitPriceMinor", it.get("unitPriceMinor"));
                lines.add(l);
            }
            out.put("lines", lines);
        } else if (req.get("lines") != null) {
            out.put("lines", req.get("lines"));
        }
        return out;
    }

    private static Date evaluatedAt(Object raw) {
        Date d = PromoJson.date(raw, "evaluatedAt");
        return d == null ? new Date() : d;
    }

    private static Set<String> coupons(Object raw) {
        Set<String> out = new LinkedHashSet<>();
        for (String s : PromoJson.strings(raw)) {
            out.add(s.toUpperCase(Locale.ROOT));
        }
        return out;
    }

    private static List<Line> lines(Object raw) {
        List<Line> out = new ArrayList<>();
        if (!(raw instanceof List<?> list)) {
            return out;
        }
        int i = 1;
        for (Object o : list) {
            Map<String, Object> m = PromoJson.asMap(o);
            if (m == null) {
                continue;
            }
            Line l = new Line();
            l.id = m.get("lineId") == null ? "line-" + i : String.valueOf(m.get("lineId"));
            i++;
            l.skuId = String.valueOf(m.get("skuId"));
            BigDecimal q = PromoJson.num(m.get("quantity"), "quantity");
            l.qty = q == null ? 1 : q.longValue();
            l.unit = PromoJson.num(m.get("unitPriceMinor"), "unitPriceMinor");
            if (l.unit == null || l.unit.signum() < 0 || l.qty < 1) {
                throw PromoJson.invalid("Dòng hàng không hợp lệ (cần đơn giá >= 0 và số lượng >= 1)", "lines");
            }
            BigDecimal st = PromoJson.num(m.get("lineSubtotalMinor"), "lineSubtotalMinor");
            l.subtotal = st != null ? st : l.unit.multiply(BigDecimal.valueOf(l.qty));
            l.cats.addAll(PromoJson.strings(m.get("categoryIds")));
            l.cols.addAll(PromoJson.strings(m.get("collectionIds")));
            out.add(l);
        }
        return out;
    }

    private static BigDecimal subtotal(List<Line> lines) {
        BigDecimal s = BigDecimal.ZERO;
        for (Line l : lines) {
            s = s.add(l.subtotal);
        }
        return s;
    }

    private static String m(BigDecimal v) {
        return v == null ? "0" : v.setScale(0, RoundingMode.HALF_UP).toPlainString();
    }
}
