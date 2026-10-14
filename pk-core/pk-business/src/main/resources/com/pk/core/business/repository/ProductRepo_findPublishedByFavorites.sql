SELECT p.*
  FROM products p
  JOIN (SELECT w.product_id AS product_id, COUNT(*) AS hearts
          FROM wishlist_items w
         GROUP BY w.product_id) f ON f.product_id = p.id
 WHERE p.status = 'PUBLISHED'
   AND p.deleted_date IS NULL
 ORDER BY f.hearts DESC, p.id DESC
