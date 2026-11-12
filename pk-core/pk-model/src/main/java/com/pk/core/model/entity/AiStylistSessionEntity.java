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

import java.math.BigDecimal;
import java.util.Date;

/** Một lần gợi ý AI Stylist / Set Builder của tài khoản (V40). Guest không ghi dòng này. */
@Getter
@Setter
@NoArgsConstructor
@Table(name = TableConstant.AI_STYLIST_SESSIONS)
public class AiStylistSessionEntity {

    public static final String SOURCE_AI = "AI";
    public static final String SOURCE_FALLBACK = "FALLBACK";
    public static final String SCOPE_ITEM = "ITEM";
    public static final String SCOPE_SET = "SET";

    @Id
    @PrimaryKey(generationType = GenerationType.SEQUENCE, generator = "ai_stylist_sessions_id_seq")
    @Column(name = "id")
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "occasion")
    private String occasion;

    @Column(name = "styles_json")
    private String stylesJson;

    @Column(name = "budget")
    private BigDecimal budget;

    @Column(name = "budget_scope")
    private String budgetScope = SCOPE_ITEM;

    @Column(name = "recipient")
    private String recipient;

    @Column(name = "source")
    private String source;

    @Column(name = "fallback_reason")
    private String fallbackReason;

    @Column(name = "created_date")
    private Date createdDate = new Date();
}
