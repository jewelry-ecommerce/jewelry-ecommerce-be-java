SELECT *
  FROM cms_pages
 WHERE published_slug = /*slug*/'a'
   AND deleted_date IS NULL
