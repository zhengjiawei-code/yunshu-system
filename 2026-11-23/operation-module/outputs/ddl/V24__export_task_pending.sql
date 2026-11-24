-- =============================================================================
-- V24 草稿（待家伟批准，未执行）
-- 目的（v1.17 方案第 9 节 / D8）：运单列表导出改为异步任务，新增任务表。
-- 使用方式：批准后复制到 backend/src/main/resources/db/migration/V24__export_task.sql 执行。
-- 当前库版本：21；本文件不参与 Flyway，未执行。
-- =============================================================================

CREATE TABLE biz_export_task (
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '任务ID',
    task_type      VARCHAR(32)     NOT NULL                COMMENT '任务类型：waybill=运单列表导出（后续按模块扩展）',
    query_snapshot JSON            NOT NULL                COMMENT '筛选、排序、列选择与数据权限快照（JSON）',
    status         TINYINT UNSIGNED NOT NULL DEFAULT 0     COMMENT '任务状态：0=排队，10=处理中，20=成功，30=失败，40=已过期',
    total_rows     INT UNSIGNED    NULL                    COMMENT '导出总行数：成功时写入',
    processed_rows INT UNSIGNED    NOT NULL DEFAULT 0      COMMENT '已处理行数：用于进度展示',
    file_name      VARCHAR(128)    NULL                    COMMENT '下载文件名',
    file_path      VARCHAR(500)    NULL                    COMMENT '文件相对路径：不存绝对路径',
    file_size      BIGINT UNSIGNED NULL                    COMMENT '文件大小（字节）',
    fail_reason    VARCHAR(500)    NULL                    COMMENT '失败原因',
    expire_time    DATETIME        NULL                    COMMENT '过期时间：成功后默认7天',
    dept_id        BIGINT UNSIGNED NOT NULL                COMMENT '创建人所属机构ID：数据权限锚点',
    create_by      BIGINT UNSIGNED NOT NULL                COMMENT '创建人用户ID',
    create_time    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    start_time     DATETIME        NULL                    COMMENT '开始处理时间',
    finish_time    DATETIME        NULL                    COMMENT '完成时间',
    update_by      BIGINT UNSIGNED NULL                    COMMENT '最后修改人用户ID',
    update_time    DATETIME        NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后修改时间',
    deleted        TINYINT(1)      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0=正常，1=已删除',
    PRIMARY KEY (id),
    KEY idx_user_status (create_by, status),
    KEY idx_status_expire (status, expire_time),
    KEY idx_type_time (task_type, create_time),
    KEY idx_dept_time (dept_id, create_time)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci
  COMMENT = '导出任务表：异步导出任务与文件元信息';
