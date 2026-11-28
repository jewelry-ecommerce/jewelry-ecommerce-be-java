-- Product reviews (admin moderation + storefront display)
CREATE SEQUENCE IF NOT EXISTS product_reviews_id_seq;

CREATE TABLE IF NOT EXISTS product_reviews (
    id              BIGINT PRIMARY KEY DEFAULT nextval('product_reviews_id_seq'),
    product_id      BIGINT NOT NULL,
    order_id        BIGINT,
    user_id         BIGINT,
    author_name     VARCHAR(120) NOT NULL DEFAULT '',
    rating          INT NOT NULL CHECK (rating BETWEEN 1 AND 5),
    title           VARCHAR(200) NOT NULL DEFAULT '',
    content         TEXT NOT NULL DEFAULT '',
    images_json     TEXT NOT NULL DEFAULT '[]',
    status          VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    admin_reply     TEXT,
    rejected_reason TEXT,
    created_id      BIGINT,
    created_date    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_id      BIGINT,
    updated_date    TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_product_reviews_product ON product_reviews (product_id);
CREATE INDEX IF NOT EXISTS idx_product_reviews_status ON product_reviews (status);
CREATE INDEX IF NOT EXISTS idx_product_reviews_created ON product_reviews (created_date DESC);

ALTER SEQUENCE product_reviews_id_seq OWNED BY product_reviews.id;
