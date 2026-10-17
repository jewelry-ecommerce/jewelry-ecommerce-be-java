SELECT COUNT(*)
  FROM banners
 WHERE placement_code = /*placementCode*/'HOME_HERO'
   AND deleted_date IS NULL
