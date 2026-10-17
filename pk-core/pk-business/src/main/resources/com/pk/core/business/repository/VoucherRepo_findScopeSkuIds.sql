SELECT sku_id
  FROM voucher_scope_skus
 WHERE voucher_id = /*voucherId*/1
   AND scope_type = /*scopeType*/'INCLUDE'
