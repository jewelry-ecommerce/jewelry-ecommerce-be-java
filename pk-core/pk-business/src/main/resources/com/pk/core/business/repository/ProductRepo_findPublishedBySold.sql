SELECT p.*
  FROM products p
  LEFT JOIN (SELECT oi.product_id AS product_id, SUM(oi.quantity - oi.returned_quantity) AS sold
               FROM order_items oi
               JOIN orders o ON o.id = oi.order_id
              WHERE o.status IN ('CONFIRMED', 'SHIPPING', 'DELIVERED')
              GROUP BY oi.product_id) s ON s.product_id = p.id
 WHERE p.status = 'PUBLISHED'
   AND p.deleted_date IS NULL
 ORDER BY COALESCE(s.sold, 0) DESC, p.id DESC
