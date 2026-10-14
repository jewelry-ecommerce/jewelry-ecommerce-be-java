package com.pk.core.business.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pk.core.model.dto.response.ProductApprovalSummaryDto;
import com.pk.core.model.entity.ProductApprovalRequestEntity;
import com.pk.core.model.entity.ProductEntity;
import com.pk.core.model.entity.ProductSkuEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Dựng snapshot / diff / tóm tắt cho quy trình duyệt sản phẩm (dùng chung ProductServiceImpl và ProductApprovalServiceImpl). */
@Component
@RequiredArgsConstructor
public class ProductApprovalSupport {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;

    /** Các trường chính của sản phẩm + SKU tại thời điểm gửi duyệt (JSON text). */
    public String snapshotOf(ProductEntity p, List<ProductSkuEntity> skus) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", p.getCode());
        m.put("name", p.getName());
        m.put("categoryId", p.getCategoryId());
        m.put("status", p.getStatus());
        m.put("shortDescription", p.getShortDescription());
        m.put("material", p.getMaterial());
        m.put("basePrice", p.getBasePrice());
        List<Map<String, Object>> rows = new ArrayList<>();
        for (ProductSkuEntity s : skus) {
            if (ProductSkuEntity.ARCHIVED.equals(s.getStatus())) {
                continue;
            }
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("skuCode", s.getSkuCode());
            r.put("listPrice", s.getListPrice());
            r.put("salePrice", s.getSalePrice());
            r.put("status", s.getStatus());
            rows.add(r);
        }
        m.put("skus", rows);
        try {
            return objectMapper.writeValueAsString(m);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException(ex);
        }
    }

    public Map<String, Object> read(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (JsonProcessingException ex) {
            return null;
        }
    }

    /** Khác biệt giữa bản trước (before) và bản gửi duyệt (after): [{field, currentValue, pendingValue}]. */
    public List<Map<String, Object>> diff(Map<String, Object> before, Map<String, Object> after) {
        List<Map<String, Object>> out = new ArrayList<>();
        Set<String> keys = new LinkedHashSet<>();
        if (before != null) {
            keys.addAll(before.keySet());
        }
        if (after != null) {
            keys.addAll(after.keySet());
        }
        for (String k : keys) {
            Object a = before == null ? null : before.get(k);
            Object b = after == null ? null : after.get(k);
            if (!Objects.equals(String.valueOf(a), String.valueOf(b))) {
                Map<String, Object> d = new LinkedHashMap<>();
                d.put("field", k);
                d.put("currentValue", a);
                d.put("pendingValue", b);
                out.add(d);
            }
        }
        return out;
    }

    /** Tóm tắt theo yêu cầu mới nhất (history đã sắp mới nhất trước); rỗng -> hasPending=false, mọi trường null. */
    public ProductApprovalSummaryDto summary(List<ProductApprovalRequestEntity> history) {
        ProductApprovalSummaryDto s = new ProductApprovalSummaryDto();
        if (history == null || history.isEmpty()) {
            return s;
        }
        ProductApprovalRequestEntity latest = history.get(0);
        s.setHasPending(latest.isPending());
        s.setRequestId(String.valueOf(latest.getId()));
        s.setAction(latest.getActionType());
        s.setStatus(latest.getStatus());
        s.setSubmittedAt(iso(latest.getSubmittedAt()));
        s.setSubmittedBy(latest.getSubmittedBy() == null ? null : String.valueOf(latest.getSubmittedBy()));
        s.setRejectedReason(latest.getRejectedReason());
        return s;
    }

    /** Chi tiết bản chờ duyệt (FE: ProductPendingApprovalDetail). */
    public Map<String, Object> pendingDetail(ProductApprovalRequestEntity req) {
        Map<String, Object> before = read(req.getBeforeSnapshot());
        Map<String, Object> after = read(req.getAfterSnapshot());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", String.valueOf(req.getId()));
        m.put("actionType", req.getActionType());
        m.put("status", req.getStatus());
        m.put("beforeSnapshot", before);
        m.put("afterSnapshot", after);
        m.put("diff", diff(before, after));
        m.put("submittedAt", iso(req.getSubmittedAt()));
        m.put("submittedBy", req.getSubmittedBy() == null ? null : String.valueOf(req.getSubmittedBy()));
        return m;
    }

    public static String iso(Date d) {
        return d == null ? null : Instant.ofEpochMilli(d.getTime()).toString();
    }
}
