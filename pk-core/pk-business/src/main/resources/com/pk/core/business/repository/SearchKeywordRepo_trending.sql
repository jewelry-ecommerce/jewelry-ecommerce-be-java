SELECT *
  FROM search_keywords
 WHERE last_searched_date >= /*since*/CURRENT_TIMESTAMP
 ORDER BY hit_count DESC, last_searched_date DESC, id DESC
 LIMIT /*pageSize*/10
