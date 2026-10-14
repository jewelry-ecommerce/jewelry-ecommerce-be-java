SELECT p.*
  FROM products p
  JOIN categories c ON c.id = p.category_id
 WHERE p.status = 'PUBLISHED'
   AND p.deleted_date IS NULL
   AND (/*categorySlug*/'' = '' OR c.slug = /*categorySlug*/'')
   AND (/*collectionSlug*/'' = '' OR EXISTS (
         SELECT 1
           FROM product_collections pc
           JOIN collections col ON col.id = pc.collection_id
          WHERE pc.product_id = p.id
            AND col.slug = /*collectionSlug*/''
            AND col.status = 'PUBLISHED'
            AND col.deleted_date IS NULL
       ))
   AND (/*keyword*/'' = '' OR LOWER(p.name) LIKE LOWER('%'||/*keyword*/''||'%') OR LOWER(p.code) LIKE LOWER('%'||/*keyword*/''||'%'))
   AND (/*material*/'' = '' OR LOWER(p.material) = LOWER(/*material*/''))
   AND p.base_price >= /*minPrice*/0
   AND p.base_price <= /*maxPrice*/999999999999
