SELECT sku_id
  FROM promotion_scope_skus
 WHERE promotion_id = /*promotionId*/1
   AND scope_type = /*scopeType*/'INCLUDE'
