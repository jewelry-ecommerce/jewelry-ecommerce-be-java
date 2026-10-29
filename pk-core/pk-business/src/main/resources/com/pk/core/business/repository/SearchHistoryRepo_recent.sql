SELECT *
  FROM search_history
 WHERE user_id = /*userId*/1
 ORDER BY searched_date DESC, id DESC
 LIMIT /*pageSize*/10
