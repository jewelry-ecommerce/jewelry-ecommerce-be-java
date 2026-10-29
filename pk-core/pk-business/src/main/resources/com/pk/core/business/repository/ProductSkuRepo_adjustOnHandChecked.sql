UPDATE product_skus
   SET on_hand = on_hand + /*delta*/0,
       updated_date = CURRENT_TIMESTAMP
 WHERE id = /*skuId*/1
   AND CAST(on_hand AS BIGINT) + /*delta*/0 >= 0
   AND CAST(on_hand AS BIGINT) + /*delta*/0 <= 2147483647
   AND (/*delta*/0 >= 0 OR CAST(on_hand AS BIGINT) + /*delta*/0 >= LEAST(reserved, on_hand))
