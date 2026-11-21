-- ============================================================
-- V9：外转/直达编码唯一口径改为「适用网点 + 路由编码」
-- 依据：2026-11-21 裁决 D4=B
--   1) 不追加 _2 冲突后缀，同一区划在不同适用网点下编码相同；
--   2) 唯一性由应用层校验（排除逻辑删除行），因此不建全局唯一索引，
--      避免逻辑删除后同键无法重建；
--   3) 保留 (apply_dept_id, route_code) 普通索引，支撑编码查询。
-- ============================================================

ALTER TABLE `sys_route_transfer` DROP INDEX `uk_route_code`;
ALTER TABLE `sys_route_transfer` ADD KEY `idx_apply_code` (`apply_dept_id`, `route_code`);
