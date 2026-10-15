SELECT COUNT(*)
  FROM products
 WHERE brand_id = /*brandId*/1
   AND deleted_date IS NULL
