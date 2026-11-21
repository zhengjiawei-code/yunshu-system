package com.yunshu.common.datascope;

import com.baomidou.mybatisplus.extension.plugins.handler.MultiDataPermissionHandler;
import com.baomidou.mybatisplus.extension.plugins.inner.DataPermissionInterceptor;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.update.Update;

/**
 * 在 MyBatis-Plus 数据权限拦截器之前做 SQL 形态校验：
 * 只要语句触及受过滤表，就拒绝 UNION、子查询、多表更新/删除等无法安全改写的结构。
 */
public class DataScopeInterceptor extends DataPermissionInterceptor {

    public DataScopeInterceptor(MultiDataPermissionHandler handler) {
        super(handler);
    }

    @Override
    protected void processSelect(Select select, int index, String sql, Object obj) {
        if (DataScopeSqlSupport.touchesFilteredTable(select)) {
            DataScopeSqlSupport.assertSupported(select);
        }
        super.processSelect(select, index, sql, obj);
    }

    @Override
    protected void processUpdate(Update update, int index, String sql, Object obj) {
        if (DataScopeSqlSupport.touchesFilteredTable(update)) {
            DataScopeSqlSupport.assertSupported(update);
        }
        super.processUpdate(update, index, sql, obj);
    }

    @Override
    protected void processDelete(Delete delete, int index, String sql, Object obj) {
        if (DataScopeSqlSupport.touchesFilteredTable(delete)) {
            DataScopeSqlSupport.assertSupported(delete);
        }
        super.processDelete(delete, index, sql, obj);
    }
}
