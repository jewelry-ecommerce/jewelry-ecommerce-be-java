DELETE FROM ai_stylist_sessions
 WHERE user_id = /*userId*/1
   AND id NOT IN (SELECT id FROM (SELECT id FROM ai_stylist_sessions
                                   WHERE user_id = /*userId*/1
                                   ORDER BY created_date DESC, id DESC
                                   LIMIT /*keep*/20) AS newest)
