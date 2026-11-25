package com.waterrepair.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI waterRepairOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("供水抢修调度服务 API")
                        .version("1.0.0")
                        .description("从报修受理、事故确认、候选关阀影响计算、班组资质与互斥派工，"
                                + "到现场回执序列、复压核验与压力复核更正的闭环接口。"
                                + "先调用 /api/auth/login 获取 token，再点击右上角 Authorize 输入 Bearer token。"))
                .components(new Components().addSecuritySchemes("bearer-jwt",
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("Token")))
                .addSecurityItem(new SecurityRequirement().addList("bearer-jwt"));
    }
}
