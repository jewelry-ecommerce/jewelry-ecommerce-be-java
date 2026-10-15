SELECT *
  FROM order_idempotency_keys
 WHERE owner = /*owner*/'x'
   AND idem_key = /*key*/'x'
