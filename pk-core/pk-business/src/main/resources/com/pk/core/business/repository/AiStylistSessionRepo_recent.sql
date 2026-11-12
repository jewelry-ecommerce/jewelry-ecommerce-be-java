SELECT *
  FROM ai_stylist_sessions
 WHERE user_id = /*userId*/1
 ORDER BY created_date DESC, id DESC
 LIMIT /*pageSize*/20
