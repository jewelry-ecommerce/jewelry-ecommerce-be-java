SELECT *
  FROM search_keywords
 WHERE keyword_key LIKE /*pattern*/'%x%' ESCAPE '!'
 ORDER BY hit_count DESC, last_searched_date DESC, id DESC
 LIMIT /*pageSize*/8
