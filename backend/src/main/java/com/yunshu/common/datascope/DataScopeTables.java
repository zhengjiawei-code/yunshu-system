package com.yunshu.common.datascope;

import java.util.Locale;
import java.util.Set;

/**
 * 数据权限表清单：平台配置表豁免，其余表默认参与过滤（黑名单模式）。
 * 豁免表不参与行级过滤；新增业务表无需登记，必须自带 dept_id / create_by 列。
 * <p>
 * 注意：豁免表的可见性由服务层显式负责（例如区划为平台共享数据、末端网点适用公司为跨公司关系数据），
 * 不能依赖拦截器兜底。清单任何变更需评审，并同步登记到数据权限规范文档。
 */
public final class DataScopeTables {

    /** 平台表豁免清单（家伟 2026-11-21 裁定，任何变更需评审） */
    public static final Set<String> EXEMPT_TABLES = Set.of(
            "sys_menu",
            "sys_role",
            "sys_role_menu",
            "sys_user",
            "sys_user_role",
            "sys_user_org",
            "sys_user_menu",
            "sys_dept",
            "sys_region",
            "sys_region_sync_log",
            "sys_dept_apply_company",
            "sys_carrier",
            "sys_route",
            "sys_route_transfer",
            "sys_route_company",
            "sys_column_config",
            "biz_waybill_no_seq",
            "biz_pre_waybill_no_seq",
            "biz_export_task",
            "flyway_schema_history"
    );

    /** 数据库系统 schema 一律豁免（元数据查询） */
    private static final Set<String> SYSTEM_SCHEMAS = Set.of(
            "information_schema", "mysql", "performance_schema", "sys"
    );

    private DataScopeTables() {
    }

    public static boolean isExempt(String tableName) {
        if (tableName == null) {
            return false;
        }
        String normalized = tableName.toLowerCase(Locale.ROOT).replace("`", "").trim();
        int dot = normalized.indexOf('.');
        if (dot > 0) {
            String schema = normalized.substring(0, dot);
            if (SYSTEM_SCHEMAS.contains(schema)) {
                return true;
            }
            normalized = normalized.substring(dot + 1);
        }
        return EXEMPT_TABLES.contains(normalized);
    }
}
