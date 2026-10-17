UPDATE order_returns
   SET status = /*toStatus*/'X',
       updated_date = CURRENT_TIMESTAMP
 WHERE id = /*id*/1
   AND status = /*fromStatus*/'X'
