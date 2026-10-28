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

/** Lịch sử tìm kiếm của một tài khoản (V38): mỗi (userId, keywordKey) một dòng, searchedDate là lần tìm gần nhất. */
@Getter
@Setter
@NoArgsConstructor
@Table(name = TableConstant.SEARCH_HISTORY)
public class SearchHistoryEntity {

    @Id
    @PrimaryKey(generationType = GenerationType.SEQUENCE, generator = "search_history_id_seq")
    @Column(name = "id")
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "keyword")
    private String keyword;

    @Column(name = "keyword_key")
    private String keywordKey;

    @Column(name = "searched_date")
    private Date searchedDate = new Date();
}
