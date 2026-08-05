package com.example.project.config;

import org.springframework.context.annotation.Bean;
import springfox.documentation.builders.ApiInfoBuilder;
import springfox.documentation.builders.PathSelectors;
import springfox.documentation.builders.RequestHandlerSelectors;
import springfox.documentation.service.ApiInfo;
import springfox.documentation.service.ApiKey;
import springfox.documentation.service.AuthorizationScope;
import springfox.documentation.service.SecurityReference;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spi.service.contexts.SecurityContext;
import springfox.documentation.spring.web.plugins.Docket;
import springfox.documentation.swagger2.annotations.EnableSwagger2;

import java.util.List;

@EnableSwagger2
public class SwaggerConfig {

    @Bean
    public Docket api() {
        return new Docket(DocumentationType.SWAGGER_2)
                .apiInfo(apiInfo())
                .securitySchemes(List.of(bearerToken()))
                .securityContexts(List.of(securityContext()))
                .select()
                .apis(RequestHandlerSelectors.basePackage("com.example.project"))
                .paths(PathSelectors.ant("/api/**"))
                .build();
    }

    private ApiInfo apiInfo() {
        return new ApiInfoBuilder()
                .title("MiriZoom Backend API")
                .description("MiriZoom 백엔드 REST API 명세. 인증 API에서 발급받은 Access Token은 "
                        + "Authorize에 'Bearer {token}' 형식으로 입력합니다.")
                .version("1.1")
                .build();
    }

    private ApiKey bearerToken() {
        return new ApiKey("Bearer", "Authorization", "header");
    }

    private SecurityContext securityContext() {
        return SecurityContext.builder()
                .securityReferences(List.of(
                        new SecurityReference(
                                "Bearer",
                                new AuthorizationScope[]{new AuthorizationScope("global", "Access Token")}
                        )
                ))
                .forPaths(PathSelectors.regex(
                        "/api/(users.*|fm.*|gm.*|gs.*|ai.*|auth/logout)"
                ))
                .build();
    }
}
