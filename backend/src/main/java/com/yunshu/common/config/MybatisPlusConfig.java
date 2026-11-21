package com.yunshu.common.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.yunshu.common.datascope.DataScopeInterceptor;
import com.yunshu.common.datascope.DataScopeSqlHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 插件注册。
 * 数据权限基于 RoutingDataSource 在 SqlSessionFactory 层统一生效，master/slave 双数据源均被覆盖。
 * 顺序：数据权限在前、分页在后（分页 count SQL 同样会先经过数据权限条件改写）。
 */
@Configuration
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor(DataScopeSqlHandler dataScopeSqlHandler) {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new DataScopeInterceptor(dataScopeSqlHandler));
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }
}
