package com.yunshu.common.datascope;

import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.select.Join;
import net.sf.jsqlparser.statement.select.ParenthesedSelect;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SetOperationList;
import net.sf.jsqlparser.statement.update.Update;
import net.sf.jsqlparser.util.TablesNamesFinder;

import java.util.List;
import java.util.Set;

/**
 * SQL 结构安全校验：只允许可直接加条件的安全形态。
 * UNION、FROM/JOIN 子查询、WHERE/HAVING 子查询、多表 UPDATE/DELETE 一律拒绝执行（fail-closed）。
 */
public final class DataScopeSqlSupport {

    private DataScopeSqlSupport() {
    }

    /** 语句是否触及任何受过滤表（豁免表与系统 schema 不算） */
    public static boolean touchesFilteredTable(Statement statement) {
        try {
            Set<String> tables = new TablesNamesFinder().getTables(statement);
            for (String table : tables) {
                if (!DataScopeTables.isExempt(table)) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            throw new DataScopeException("数据权限：SQL 解析失败，拒绝执行，请检查 SQL 语法", e);
        }
    }

    public static void assertSupported(Select select) {
        if (select instanceof SetOperationList) {
            throw new DataScopeException("数据权限：不支持 UNION/集合查询，已拒绝执行");
        }
        if (!(select instanceof PlainSelect plainSelect)) {
            throw new DataScopeException("数据权限：不支持的查询语句类型 "
                    + select.getClass().getSimpleName() + "，已拒绝执行");
        }
        assertFromItemAllowed(plainSelect.getFromItem());
        List<Join> joins = plainSelect.getJoins();
        if (joins != null) {
            for (Join join : joins) {
                assertFromItemAllowed(join.getRightItem());
            }
        }
        assertNoSubSelect(plainSelect.getWhere(), "WHERE");
        assertNoSubSelect(plainSelect.getHaving(), "HAVING");
    }

    public static void assertSupported(Update update) {
        Set<String> tables = new TablesNamesFinder().getTables(update);
        if (tables.size() > 1) {
            throw new DataScopeException("数据权限：不支持多表 UPDATE，已拒绝执行");
        }
        assertNoSubSelect(update.getWhere(), "UPDATE WHERE");
    }

    public static void assertSupported(Delete delete) {
        Set<String> tables = new TablesNamesFinder().getTables(delete);
        if (tables.size() > 1) {
            throw new DataScopeException("数据权限：不支持多表 DELETE，已拒绝执行");
        }
        assertNoSubSelect(delete.getWhere(), "DELETE WHERE");
    }

    private static void assertFromItemAllowed(Object fromItem) {
        if (fromItem == null) {
            return;
        }
        if (fromItem instanceof ParenthesedSelect) {
            throw new DataScopeException("数据权限：不支持 FROM/JOIN 子查询，已拒绝执行");
        }
    }

    /** 表达式里出现表引用即视为子查询；解析失败同样拒绝 */
    private static void assertNoSubSelect(Expression expression, String position) {
        if (expression == null) {
            return;
        }
        try {
            Set<String> tables = new TablesNamesFinder().getTables(expression);
            if (!tables.isEmpty()) {
                throw new DataScopeException("数据权限：不支持 " + position + " 子查询，已拒绝执行");
            }
        } catch (DataScopeException e) {
            throw e;
        } catch (Exception e) {
            throw new DataScopeException("数据权限：" + position + " 表达式解析失败，拒绝执行", e);
        }
    }
}
