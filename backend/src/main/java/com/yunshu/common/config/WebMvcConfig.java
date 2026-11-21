package com.yunshu.common.config;

import com.yunshu.common.datascope.DataScopeWebInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 注册数据权限上下文拦截器：进入 Controller 前解析登录态，离开时清理 ThreadLocal。
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final DataScopeWebInterceptor dataScopeWebInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(dataScopeWebInterceptor).addPathPatterns("/**");
    }
}
