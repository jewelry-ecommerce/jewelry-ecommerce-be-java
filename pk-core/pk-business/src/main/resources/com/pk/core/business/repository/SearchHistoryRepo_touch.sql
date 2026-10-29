UPDATE search_history
   SET searched_date = CURRENT_TIMESTAMP,
       keyword = /*keyword*/'x'
 WHERE user_id = /*userId*/1
   AND keyword_key = /*keywordKey*/'x'
