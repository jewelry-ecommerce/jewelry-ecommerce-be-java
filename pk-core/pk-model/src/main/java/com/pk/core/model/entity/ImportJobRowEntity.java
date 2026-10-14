package com.pk.core.model.entity;

import com.pk.core.common.entity.CreateEntity;
import com.pk.core.model.constant.TableConstant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import vn.com.unit.miragesql.miragesql.annotation.Column;
import vn.com.unit.miragesql.miragesql.annotation.PrimaryKey;
import vn.com.unit.miragesql.miragesql.annotation.PrimaryKey.GenerationType;
import vn.com.unit.miragesql.miragesql.annotation.Table;

/** Kết quả từng dòng của 1 job import (OK | ERROR) - FE xem ở /admin/ie/jobs/{id}/result-rows. */
@Getter
@Setter
@NoArgsConstructor
@Table(name = TableConstant.IMPORT_JOB_ROWS)
public class ImportJobRowEntity extends CreateEntity {

    public static final String OK = "OK";
    public static final String ERROR = "ERROR";

    @Id
    @PrimaryKey(generationType = GenerationType.SEQUENCE, generator = "import_job_rows_id_seq")
    @Column(name = "id")
    private Long id;

    @Column(name = "job_id")
    private Long jobId;

    @Column(name = "line_no")
    private int lineNo;

    @Column(name = "status")
    private String status;

    @Column(name = "message")
    private String message;

    @Column(name = "data")
    private String data;

    public ImportJobRowEntity(Long jobId, int lineNo, String status, String message, String data) {
        this.jobId = jobId;
        this.lineNo = lineNo;
        this.status = status;
        this.message = message;
        this.data = data;
    }
}
