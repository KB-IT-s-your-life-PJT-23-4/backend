package com.example.project.config;

import com.example.project.admin.access.service.AdminAccessSseService;
import com.example.project.common.logging.RequestLoggingAspect;
import com.example.project.common.logging.DeferredAccessLogInterceptor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.*;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.multipart.MultipartResolver;
import org.springframework.web.multipart.support.StandardServletMultipartResolver;
import org.springframework.validation.beanvalidation.MethodValidationPostProcessor;
import org.springframework.web.servlet.config.annotation.*;
import org.springframework.web.servlet.view.JstlView;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;

import java.time.Clock;
import java.util.List;

@Configuration
@EnableWebMvc
@Import(SwaggerConfig.class)
@EnableAspectJAutoProxy
@ComponentScan(
        basePackages = "com.example.project",
        useDefaultFilters = false,
        includeFilters = {
                @ComponentScan.Filter(type = FilterType.ANNOTATION, classes = Controller.class),
                @ComponentScan.Filter(type = FilterType.ANNOTATION, classes = ControllerAdvice.class)
        })
public class ServletConfig implements WebMvcConfigurer {

    private final AsyncTaskExecutor adminAccessSseExecutor;

    public ServletConfig(
            @Qualifier("adminAccessSseExecutor")
            AsyncTaskExecutor adminAccessSseExecutor
    ){
        this.adminAccessSseExecutor = adminAccessSseExecutor;
    }

    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        configurer.setTaskExecutor(adminAccessSseExecutor);

        configurer.setDefaultTimeout(60L * 60L * 1000L);
        configurer.registerDeferredResultInterceptors(
                new DeferredAccessLogInterceptor()
        );
    }

    @Override
    public void configureViewResolvers(ViewResolverRegistry registry) {
        registry.jsp("/WEB-INF/views/", ".jsp").viewClass(JstlView.class);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/resources/**").addResourceLocations("/resources/");

        registry.addResourceHandler("/swagger-ui.html")
                .addResourceLocations("classpath:/META-INF/resources/");

        registry.addResourceHandler("/webjars/**")
                .addResourceLocations("classpath:/META-INF/resources/webjars/");
    }
    @Override
    public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
        converters.stream()
                .filter(MappingJackson2HttpMessageConverter.class::isInstance)
                .map(MappingJackson2HttpMessageConverter.class::cast)
                .forEach(converter -> {
                    converter.getObjectMapper()
                            .registerModule(new JavaTimeModule())
                            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
                });
    }

    @Bean
    public MultipartResolver multipartResolver() {
        return new StandardServletMultipartResolver();
    }

    @Bean
    public static MethodValidationPostProcessor methodValidationPostProcessor() {
        return new MethodValidationPostProcessor();
    }

    @Bean
    public RequestLoggingAspect requestLoggingAspect(
            AdminAccessSseService adminAccessSseService,
            Clock applicationClock
    ) {
        return new RequestLoggingAspect(adminAccessSseService, applicationClock);
    }
}
