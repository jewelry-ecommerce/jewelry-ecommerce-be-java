UPDATE orders
   SET refunded_amount = refunded_amount + /*amount*/1,
       payment_status = CASE WHEN refunded_amount + /*amount*/1 >= grand_total THEN 'REFUNDED' ELSE payment_status END,
       updated_date = CURRENT_TIMESTAMP
 WHERE id = /*id*/1
   AND payment_status = 'PAID'
   AND refunded_amount + /*amount*/1 <= grand_total
