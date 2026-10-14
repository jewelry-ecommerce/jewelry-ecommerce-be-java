-- V24: quản lý tác vụ import/export (CSV sản phẩm). Job chạy đồng bộ, lưu kết quả để FE tra cứu lại.
CREATE SEQUENCE import_export_jobs_id_seq;
CREATE TABLE import_export_jobs (
    id              BIGINT PRIMARY KEY DEFAULT nextval('import_export_jobs_id_seq'),
    job_type        VARCHAR(10) NOT NULL,
    entity_type     VARCHAR(20) NOT NULL DEFAULT 'PRODUCT',
    status          VARCHAR(12) NOT NULL DEFAULT 'PENDING',
    file_name       VARCHAR(255),
    file_url        VARCHAR(500),
    error_message   VARCHAR(1000),
    total_rows      INT NOT NULL DEFAULT 0,
    success_rows    INT NOT NULL DEFAULT 0,
    failed_rows     INT NOT NULL DEFAULT 0,
    reference_type  VARCHAR(50),
    reference_id    VARCHAR(100),
    params          TEXT,
    started_at      TIMESTAMPTZ,
    finished_at     TIMESTAMPTZ,
    created_id      BIGINT,
    created_date    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_id      BIGINT,
    updated_date    TIMESTAMPTZ,
    CONSTRAINT ck_iej_type CHECK (job_type IN ('EXPORT', 'IMPORT')),
    CONSTRAINT ck_iej_status CHECK (status IN ('PENDING', 'PROCESSING', 'DONE', 'FAILED'))
);
CREATE INDEX ix_iej_created ON import_export_jobs (created_date DESC);
ALTER SEQUENCE import_export_jobs_id_seq OWNED BY import_export_jobs.id;

CREATE SEQUENCE import_job_rows_id_seq;
CREATE TABLE import_job_rows (
    id            BIGINT PRIMARY KEY DEFAULT nextval('import_job_rows_id_seq'),
    job_id        BIGINT NOT NULL REFERENCES import_export_jobs (id) ON DELETE CASCADE,
    line_no       INT NOT NULL,
    status        VARCHAR(8) NOT NULL,
    message       VARCHAR(1000),
    data          TEXT,
    created_id    BIGINT,
    created_date  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_ijr_status CHECK (status IN ('OK', 'ERROR'))
);
CREATE INDEX ix_ijr_job ON import_job_rows (job_id, line_no);
ALTER SEQUENCE import_job_rows_id_seq OWNED BY import_job_rows.id;
