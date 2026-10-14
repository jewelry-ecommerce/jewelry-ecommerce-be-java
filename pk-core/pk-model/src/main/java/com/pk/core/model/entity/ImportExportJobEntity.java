package com.pk.core.model.entity;

import com.pk.core.common.entity.UpdateEntity;
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

/** Tác vụ import/export (V24). Chạy đồng bộ nhưng vẫn lưu trạng thái để FE tra cứu lại qua /admin/ie/jobs. */
@Getter
@Setter
@NoArgsConstructor
@Table(name = TableConstant.IMPORT_EXPORT_JOBS)
public class ImportExportJobEntity extends UpdateEntity {

    public static final String EXPORT = "EXPORT";
    public static final String IMPORT = "IMPORT";
    public static final String PENDING = "PENDING";
    public static final String PROCESSING = "PROCESSING";
    public static final String DONE = "DONE";
    public static final String FAILED = "FAILED";

    @Id
    @PrimaryKey(generationType = GenerationType.SEQUENCE, generator = "import_export_jobs_id_seq")
    @Column(name = "id")
    private Long id;

    @Column(name = "job_type")
    private String jobType;

    @Column(name = "entity_type")
    private String entityType = "PRODUCT";

    @Column(name = "status")
    private String status = PENDING;

    @Column(name = "file_name")
    private String fileName;

    @Column(name = "file_url")
    private String fileUrl;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "total_rows")
    private int totalRows;

    @Column(name = "success_rows")
    private int successRows;

    @Column(name = "failed_rows")
    private int failedRows;

    @Column(name = "reference_type")
    private String referenceType;

    @Column(name = "reference_id")
    private String referenceId;

    @Column(name = "params")
    private String params;

    @Column(name = "started_at")
    private Date startedAt;

    @Column(name = "finished_at")
    private Date finishedAt;
}
