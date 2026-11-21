package com.yunshu.common.datascope;

import com.baomidou.mybatisplus.extension.plugins.handler.MultiDataPermissionHandler;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Table;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 数据权限条件生成器：黑名单模式，除豁免表外默认对所有表追加行级条件。
 * 四档：1 无条件；3 dept_id = 主机构；4 dept_id IN 主机构子树；5 create_by = 当前用户。
 * 上下文缺失、档位非法、主机构为空时一律追加 1=0（fail-closed）。
 */
@Slf4j
@Component
public class DataScopeSqlHandler implements MultiDataPermissionHandler {

    /** 子树 IN 列表上限，超过直接报错，避免生成超大 SQL */
    private static final int MAX_SUBTREE_SIZE = 2000;

    @Value("${yunshu.data-scope.enabled:false}")
    private boolean enabled;

    @Override
    public Expression getSqlSegment(Table table, Expression where, String mappedStatementId) {
        if (!enabled) {
            return null;
        }
        if (DataScopeTables.isExempt(table.getName())) {
            return null;
        }
        if (table.getSchemaName() != null && DataScopeTables.isExempt(table.getSchemaName() + "." + table.getName())) {
            return null;
        }

        DataScopeContext context = DataScopeContext.current();
        if (context == null) {
            log.warn("数据权限上下文缺失，按 fail-closed 拒绝数据：statement={}", mappedStatementId);
            return denyAll();
        }

        String prefix = columnPrefix(table);
        int scope = context.getScope() == null ? -1 : context.getScope();
        return switch (scope) {
            case DataScopeContext.SCOPE_ALL -> null;
            case DataScopeContext.SCOPE_DEPT -> context.getDeptId() == null
                    ? denyAll()
                    : condition(prefix + DataScopeContext.DEPT_COLUMN + " = " + context.getDeptId());
            case DataScopeContext.SCOPE_SUBTREE -> subtreeCondition(prefix, context);
            case DataScopeContext.SCOPE_SELF -> context.getUserId() == null
                    ? denyAll()
                    : condition(prefix + DataScopeContext.OWNER_COLUMN + " = " + context.getUserId());
            default -> {
                log.warn("数据权限档位非法（{}），按 fail-closed 拒绝：statement={}", scope, mappedStatementId);
                yield denyAll();
            }
        };
    }

    private Expression subtreeCondition(String prefix, DataScopeContext context) {
        List<Long> deptIds = context.getSubtreeDeptIds().stream().sorted().toList();
        if (context.getDeptId() == null || deptIds.isEmpty()) {
            return denyAll();
        }
        if (deptIds.size() > MAX_SUBTREE_SIZE) {
            throw new DataScopeException("数据权限：本机构及以下机构数量超过 " + MAX_SUBTREE_SIZE
                    + "，请调整数据范围或分批查询");
        }
        String joined = deptIds.stream().map(String::valueOf).reduce((a, b) -> a + "," + b).orElse("");
        return condition(prefix + DataScopeContext.DEPT_COLUMN + " IN (" + joined + ")");
    }

    private String columnPrefix(Table table) {
        if (table.getAlias() != null && table.getAlias().getName() != null) {
            return table.getAlias().getName().replace("`", "").trim() + ".";
        }
        return table.getName().replace("`", "").trim() + ".";
    }

    private Expression denyAll() {
        return condition("1 = 0");
    }

    private Expression condition(String sql) {
        try {
            return CCJSqlParserUtil.parseCondExpression(sql);
        } catch (JSQLParserException e) {
            // 条件构造失败绝不放行
            throw new DataScopeException("数据权限：条件构造失败，拒绝执行", e);
        }
    }
}
