SELECT DISTINCT pp.product_id
  FROM promotion_products pp
  JOIN promotions p ON p.id = pp.promotion_id
 WHERE p.status = 'PUBLISHED'
   AND p.deleted_date IS NULL
   AND p.starts_at <= /*now*/CURRENT_TIMESTAMP
   AND p.ends_at >= /*now*/CURRENT_TIMESTAMP
 ORDER BY pp.product_id DESC
