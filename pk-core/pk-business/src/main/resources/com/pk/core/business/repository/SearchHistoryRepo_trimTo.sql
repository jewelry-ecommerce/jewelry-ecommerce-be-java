DELETE FROM search_history
 WHERE user_id = /*userId*/1
   AND id NOT IN (SELECT id FROM (SELECT id FROM search_history
                                   WHERE user_id = /*userId*/1
                                   ORDER BY searched_date DESC, id DESC
                                   LIMIT /*keep*/20) AS newest)
