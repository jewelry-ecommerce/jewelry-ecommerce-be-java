UPDATE product_skus
   SET on_hand = GREATEST(on_hand - /*qty*/1, 0),
       reserved = GREATEST(reserved - /*qty*/1, 0),
       updated_date = CURRENT_TIMESTAMP
 WHERE id = /*skuId*/1
