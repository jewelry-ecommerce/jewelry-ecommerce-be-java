-- V38: tìm kiếm storefront - từ khoá phổ biến (trending/gợi ý) và lịch sử tìm kiếm theo tài khoản.
-- Chỉ thêm bảng mới. Chỉ ghi nhận từ khoá có kết quả (không rác); khách vãng lai chỉ góp vào search_keywords,
-- lịch sử cá nhân (search_history) chỉ cho tài khoản đăng nhập, giữ tối đa 20 từ khoá gần nhất mỗi người.
-- keyword_key = từ khoá bỏ dấu + chữ thường, dùng để gộp "nhẫn"/"nhan" và để so khớp gợi ý không dấu.
CREATE SEQUENCE search_keywords_id_seq;
CREATE TABLE search_keywords (
    id                 BIGINT PRIMARY KEY DEFAULT nextval('search_keywords_id_seq'),
    keyword            VARCHAR(100) NOT NULL,
    keyword_key        VARCHAR(100) NOT NULL,
    hit_count          BIGINT       NOT NULL DEFAULT 1,
    last_searched_date TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_search_keywords_key UNIQUE (keyword_key),
    CONSTRAINT ck_search_keywords_hits CHECK (hit_count > 0)
);
CREATE INDEX ix_search_keywords_hot ON search_keywords (last_searched_date DESC, hit_count DESC);
ALTER SEQUENCE search_keywords_id_seq OWNED BY search_keywords.id;

CREATE SEQUENCE search_history_id_seq;
CREATE TABLE search_history (
    id            BIGINT PRIMARY KEY DEFAULT nextval('search_history_id_seq'),
    user_id       BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    keyword       VARCHAR(100) NOT NULL,
    keyword_key   VARCHAR(100) NOT NULL,
    searched_date TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_search_history_user_key UNIQUE (user_id, keyword_key)
);
CREATE INDEX ix_search_history_user ON search_history (user_id, searched_date DESC, id DESC);
ALTER SEQUENCE search_history_id_seq OWNED BY search_history.id;
