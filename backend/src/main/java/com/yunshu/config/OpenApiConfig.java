package com.yunshu.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI yunshuOpenApi() {
        return new OpenAPI().info(new Info()
                .title("运输管理系统 API")
                .version("1.0.0")
                .description("菜单管理接口先行，后续按模块扩展"));
    }
}
