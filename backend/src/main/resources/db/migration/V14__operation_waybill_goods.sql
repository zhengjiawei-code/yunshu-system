-- ============================================================================
-- 云枢运输管理系统 · V14 运单货物明细子表 + 运单收货方结构扩展
-- ----------------------------------------------------------------------------
-- 前置：flyway_schema_history 最新为 V13（success=1），biz_waybill 0 条，无需回填
-- 内容：
--   1) 新建 biz_waybill_goods（按行落库的货物明细；行汇总 = 主表 quantity/weight/volume）
--   2) biz_waybill 新增 8 列：
--        发货单位、收货单位；
--        收货省/市/区县 按项目既有区划风格拆为 code + name 成对（6 列）
-- 约定：
--   - utf8mb4 / utf8mb4_0900_ai_ci；金额/重量/体积一律 DECIMAL，禁止 FLOAT/DOUBLE；
--   - 不建物理外键；每张表、每个字段均有中文 COMMENT；
--   - 件数/重量/体积不设 DEFAULT，必须显式传值，由服务层校验 quantity>=1、weight>0、volume>0；
--   - 新增收货区划列全部可空，历史/未录入场景不受影响。
-- ============================================================================

SET NAMES utf8mb4;

-- ----------------------------------------------------------------------------
-- 1. 运单货物明细子表
-- ----------------------------------------------------------------------------
CREATE TABLE `biz_waybill_goods` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID，自增',
  `waybill_id` BIGINT UNSIGNED NOT NULL COMMENT '运单ID：逻辑引用biz_waybill.id，与运单同事务写入；不建物理外键',
  `seq` TINYINT UNSIGNED NOT NULL COMMENT '行序号：从1开始按货物行顺序递增，同一运单内唯一，删除后不回收',
  `goods_name` VARCHAR(100) NOT NULL COMMENT '货物名称：必填，单行最长100字符',
  `goods_type` VARCHAR(50) NULL DEFAULT NULL COMMENT '货物类型：如普货/易碎/冷链等，可空',
  `package_type` VARCHAR(50) NULL DEFAULT NULL COMMENT '包装类型：如纸箱/木架/托盘等，可空',
  `quantity` INT UNSIGNED NOT NULL COMMENT '件数：必填且≥1，无默认值，必须显式传值，服务端校验',
  `weight` DECIMAL(12,3) NOT NULL COMMENT '重量(kg)：必填且>0，保留3位小数；无默认值，必须显式传值，服务端校验',
  `volume` DECIMAL(12,3) NOT NULL COMMENT '体积(m³)：必填且>0，保留3位小数；无默认值，必须显式传值，服务端校验',
  `goods_remark` VARCHAR(500) NULL DEFAULT NULL COMMENT '货物备注：货物特殊说明、装卸要求等，可空',
  `dept_id` BIGINT UNSIGNED NOT NULL COMMENT '开单网点ID：引用sys_dept.id，与运单dept_id一致；用于数据权限过滤',
  `create_by` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '创建人用户ID：由数据权限自动填充，可空',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间：服务器时间',
  `update_by` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '最后修改人用户ID：由数据权限自动填充，可空',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后修改时间：数据库自动刷新',
  `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=正常，1=已删除；查询默认过滤deleted=0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_waybill_seq` (`waybill_id`, `seq`),
  KEY `idx_waybill` (`waybill_id`),
  KEY `idx_dept` (`dept_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='运单货物明细子表：一行一条货物；行汇总必须等于主表件数/重量/体积';

-- ----------------------------------------------------------------------------
-- 2. 运单主表：发货/收货单位 + 收货省市区县（code/name 成对，风格与 dest_* 一致）
-- ----------------------------------------------------------------------------
ALTER TABLE `biz_waybill`
  ADD COLUMN `sender_company` VARCHAR(100) NULL DEFAULT NULL COMMENT '发货单位：发货方所属单位名称，可空' AFTER `sender_address`,
  ADD COLUMN `receiver_company` VARCHAR(100) NULL DEFAULT NULL COMMENT '收货单位：收货方所属单位名称，可空' AFTER `receiver_phone`,
  ADD COLUMN `receiver_province_code` VARCHAR(12) NULL DEFAULT NULL COMMENT '收货省行政编码：取自区划表，如440000；与目的地列保持一致，统一VARCHAR(12)' AFTER `receiver_company`,
  ADD COLUMN `receiver_province_name` VARCHAR(64) NULL DEFAULT NULL COMMENT '收货省名称快照：如广东省，可空' AFTER `receiver_province_code`,
  ADD COLUMN `receiver_city_code` VARCHAR(12) NULL DEFAULT NULL COMMENT '收货市行政编码：如440100，可空' AFTER `receiver_province_name`,
  ADD COLUMN `receiver_city_name` VARCHAR(64) NULL DEFAULT NULL COMMENT '收货市名称快照：如广州市，可空' AFTER `receiver_city_code`,
  ADD COLUMN `receiver_district_code` VARCHAR(12) NULL DEFAULT NULL COMMENT '收货区县行政编码：同名区县靠编码区分，如440111，可空' AFTER `receiver_city_name`,
  ADD COLUMN `receiver_district_name` VARCHAR(64) NULL DEFAULT NULL COMMENT '收货区县名称快照：如白云区，可空' AFTER `receiver_district_code`;
