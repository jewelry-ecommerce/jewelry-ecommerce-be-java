SELECT *
  FROM inventory_movements
 WHERE (/*skuId*/0 = 0 OR sku_id = /*skuId*/0)
   AND (/*reason*/'' = '' OR reason = /*reason*/'')
   AND created_date >= /*fromAt*/CURRENT_TIMESTAMP
   AND created_date < /*toAt*/CURRENT_TIMESTAMP
 ORDER BY created_date DESC, id DESC
 LIMIT /*pageSize*/20 OFFSET /*pageOffset*/0
