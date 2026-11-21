package com.yunshu.common.datascope;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 业务表写入自动填充：dept_id 取当前上下文主机构，create_by/update_by 取当前用户。
 * 平台豁免表完全跳过，保证系统管理页面行为不变；业务表缺上下文直接报错（fail-closed）。
 */
@Slf4j
@Component
public class DataScopeMetaObjectHandler implements MetaObjectHandler {

    @Value("${yunshu.data-scope.enabled:false}")
    private boolean enabled;

    @Override
    public void insertFill(MetaObject metaObject) {
        if (!enabled || isExempt(metaObject)) {
            return;
        }
        DataScopeContext context = DataScopeContext.require("新增业务数据");
        if (metaObject.hasSetter("deptId") && getFieldValByName("deptId", metaObject) == null) {
            if (context.getDeptId() == null) {
                throw new DataScopeException("当前账号未分配主机构，不能新增业务数据");
            }
            setFieldValByName("deptId", context.getDeptId(), metaObject);
        }
        if (metaObject.hasSetter("createBy") && getFieldValByName("createBy", metaObject) == null) {
            setFieldValByName("createBy", context.getUserId(), metaObject);
        }
        if (metaObject.hasSetter("updateBy") && getFieldValByName("updateBy", metaObject) == null) {
            setFieldValByName("updateBy", context.getUserId(), metaObject);
        }
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        if (!enabled || isExempt(metaObject)) {
            return;
        }
        DataScopeContext context = DataScopeContext.require("更新业务数据");
        if (metaObject.hasSetter("updateBy")) {
            setFieldValByName("updateBy", context.getUserId(), metaObject);
        }
    }

    private boolean isExempt(MetaObject metaObject) {
        Object original = metaObject.getOriginalObject();
        if (original == null) {
            return true;
        }
        Class<?> entityClass = original.getClass();
        TableInfo tableInfo = TableInfoHelper.getTableInfo(entityClass);
        String tableName = null;
        if (tableInfo != null) {
            tableName = tableInfo.getTableName();
        } else {
            TableName annotation = entityClass.getAnnotation(TableName.class);
            if (annotation != null) {
                tableName = annotation.value();
            }
        }
        // 无法识别表名时不填充，交由业务侧显式赋值
        return tableName == null || DataScopeTables.isExempt(tableName);
    }
}
