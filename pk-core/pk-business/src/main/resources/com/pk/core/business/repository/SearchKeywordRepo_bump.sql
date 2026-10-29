UPDATE search_keywords
   SET hit_count = hit_count + 1,
       last_searched_date = CURRENT_TIMESTAMP
 WHERE keyword_key = /*keywordKey*/'x'
