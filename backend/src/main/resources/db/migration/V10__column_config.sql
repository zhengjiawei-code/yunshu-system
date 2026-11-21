-- 列表列配置：按「用户 + 页面标识」持久化，跨设备一致。
-- 唯一键保证同一用户同一页面只有一份配置；恢复默认即物理删除该行。
CREATE TABLE IF NOT EXISTS sys_column_config (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id     BIGINT       NOT NULL COMMENT '用户ID',
    page_key    VARCHAR(64)  NOT NULL COMMENT '页面标识，如 system:user',
    config_json TEXT         NOT NULL COMMENT '列配置JSON：顺序/显隐/排序/筛选类型/默认查询值',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_page (user_id, page_key)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='列表列配置（用户+页面隔离）';
