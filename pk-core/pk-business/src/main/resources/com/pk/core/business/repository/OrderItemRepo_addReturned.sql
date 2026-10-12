UPDATE order_items
   SET returned_quantity = returned_quantity + /*qty*/1
 WHERE id = /*id*/1
   AND returned_quantity + /*qty*/1 <= quantity
