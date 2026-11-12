INSERT INTO ai_stylist_feedback (session_id, user_id, product_id, liked, created_date, updated_date)
VALUES (/*sessionId*/1, /*userId*/1, /*productId*/1, /*liked*/false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (session_id, product_id)
DO UPDATE SET liked = EXCLUDED.liked,
              user_id = EXCLUDED.user_id,
              updated_date = CURRENT_TIMESTAMP
