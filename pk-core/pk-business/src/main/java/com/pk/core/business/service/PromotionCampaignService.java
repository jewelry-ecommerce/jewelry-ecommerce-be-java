package com.pk.core.business.service;

import java.util.List;
import java.util.Map;

/**
 * Chiến dịch khuyến mãi theo wizard của Admin FE (nháp -> hình thức/schema -> điều kiện, quyền lợi -> mã voucher -> phát hành
 * -> tạm dừng/tiếp tục/kết thúc). Mọi giá trị trả về là map đúng hợp đồng JSON của FE (id dạng chuỗi, thời gian ISO-8601).
 * Phát hành sẽ hiện thực hoá từng hình thức thành khuyến mãi/voucher của engine cũ để giá và checkout dùng chung một nguồn.
 */
public interface PromotionCampaignService {

    // ---------------------------------------------------------------- nháp
    Map<String, Object> listDrafts(Integer page, Integer limit, String status, String search);

    Map<String, Object> getDraft(String draftId);

    Map<String, Object> createDraft(Map<String, Object> body, Long actorId);

    Map<String, Object> updateDraft(String draftId, Map<String, Object> body, Long actorId);

    void deleteDraft(String draftId, Long actorId);

    Map<String, Object> publishDraft(String draftId, Long actorId);

    // ---------------------------------------------------------------- thành phần cấp chiến dịch
    Map<String, Object> draftComponents(String draftId);

    /** kind: TARGET | AUDIENCE | APPLICABILITY. */
    Map<String, Object> upsertDraftComponent(String draftId, String kind, Map<String, Object> body, Long actorId);

    // ---------------------------------------------------------------- schema
    Map<String, Object> listSchemas(String draftId);

    Map<String, Object> getSchema(String draftId, String schemaId);

    Map<String, Object> createSchema(String draftId, Map<String, Object> body, Long actorId);

    Map<String, Object> updateSchema(String draftId, String schemaId, Map<String, Object> body, Long actorId);

    void deleteSchema(String draftId, String schemaId);

    void reorderSchemas(String draftId, List<String> schemaIds, Long actorId);

    Map<String, Object> schemaComponents(String draftId, String schemaId);

    List<Map<String, Object>> componentOptions(String draftId, String schemaId, String kind);

    /** kind: CONDITION | BENEFIT. */
    Map<String, Object> upsertSchemaComponent(String draftId, String schemaId, String kind, Map<String, Object> body, Long actorId);

    // ---------------------------------------------------------------- mã voucher
    Map<String, Object> voucherAuthoring(String draftId, String schemaId);

    Map<String, Object> voucherCodes(String draftId, String schemaId);

    Map<String, Object> upsertVoucherCode(String draftId, String schemaId, Map<String, Object> body, Long actorId);

    void deleteVoucherCode(String draftId, String schemaId, String voucherCodeId);

    // ---------------------------------------------------------------- đã phát hành
    Map<String, Object> publishedDetail(String promotionId);

    Map<String, Object> publishedQuotaStatistics(String promotionId);

    Map<String, Object> publishedComponents(String promotionId);

    Map<String, Object> publishedSchemas(String promotionId);

    Map<String, Object> publishedSchemaComponents(String promotionId, String schemaId);

    Map<String, Object> publishedVoucherCodes(String promotionId, String schemaId);

    Map<String, Object> pause(String promotionId, Long actorId);

    Map<String, Object> resume(String promotionId, Long actorId);

    Map<String, Object> end(String promotionId, Long actorId);

    Map<String, Object> unpublishSchema(String promotionId, String schemaId, Long actorId);

    // ---------------------------------------------------------------- tổng quan
    Map<String, Object> overview(Map<String, String> params);
}
