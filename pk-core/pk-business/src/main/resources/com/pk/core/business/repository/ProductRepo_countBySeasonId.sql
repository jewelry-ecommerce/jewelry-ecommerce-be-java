SELECT COUNT(*)
  FROM products
 WHERE season_id = /*seasonId*/1
   AND deleted_date IS NULL
