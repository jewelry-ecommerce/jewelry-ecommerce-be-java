SELECT COUNT(*)
  FROM sku_attribute_values
 WHERE attribute_id = /*attributeId*/1
   AND POSITION('||' || /*value*/'x' || '||' IN '||' || value || '||') > 0
