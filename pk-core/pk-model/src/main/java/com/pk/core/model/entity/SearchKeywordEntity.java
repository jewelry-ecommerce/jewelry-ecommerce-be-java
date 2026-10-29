package com.pk.core.model.entity;

import com.pk.core.model.constant.TableConstant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import vn.com.unit.miragesql.miragesql.annotation.Column;
import vn.com.unit.miragesql.miragesql.annotation.PrimaryKey;
import vn.com.unit.miragesql.miragesql.annotation.PrimaryKey.GenerationType;
import vn.com.unit.miragesql.miragesql.annotation.Table;

import java.util.Date;

/** Từ khoá tìm kiếm phổ biến (V38), duy nhất theo keywordKey (bỏ dấu, chữ thường). keyword là dạng hiển thị. */
@Getter
@Setter
@NoArgsConstructor
@Table(name = TableConstant.SEARCH_KEYWORDS)
public class SearchKeywordEntity {

    @Id
    @PrimaryKey(generationType = GenerationType.SEQUENCE, generator = "search_keywords_id_seq")
    @Column(name = "id")
    private Long id;

    @Column(name = "keyword")
    private String keyword;

    @Column(name = "keyword_key")
    private String keywordKey;

    @Column(name = "hit_count")
    private long hitCount = 1;

    @Column(name = "last_searched_date")
    private Date lastSearchedDate = new Date();
}
