UPDATE banner_placement_layouts
   SET status = 'INACTIVE', updated_date = CURRENT_TIMESTAMP
 WHERE placement_id = /*placementId*/1
   AND id <> /*keepId*/1
   AND status = 'ACTIVE'
