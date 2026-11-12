SELECT DISTINCT product_id
  FROM ai_stylist_feedback
 WHERE user_id = /*userId*/1
   AND liked = FALSE
