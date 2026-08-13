package com.example.project.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.PropertySource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.web.bind.annotation.ControllerAdvice;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.ZoneId;

@Configuration
@ComponentScan(
        basePackages = "com.example.project",
        excludeFilters = {
                @ComponentScan.Filter(type = FilterType.ANNOTATION, classes = Configuration.class),
                @ComponentScan.Filter(type = FilterType.ANNOTATION, classes = Controller.class),
                @ComponentScan.Filter(type = FilterType.ANNOTATION, classes = ControllerAdvice.class),
                // batch 모듈이 같은 base package 라 여기에 걸린다. 배치 빈은 실행할 때
                // 자식 컨텍스트(BatchJobRunner)에서만 살아야 하고, core 가 주워 가면
                // batch.properties 가 없어 @Value 치환에서 기동이 깨진다.
                @ComponentScan.Filter(type = FilterType.REGEX, pattern = "com\\.example\\.project\\.batch\\..*")
        })
@MapperScan(basePackages = "com.example.project", annotationClass = org.apache.ibatis.annotations.Mapper.class)
@EnableTransactionManagement
@PropertySource("classpath:database.properties")
@PropertySource("classpath:application.properties")
public class RootConfig {
    @Bean
    public DataSource dataSource(
            @Value("${jdbc.driver}") String driver,
            @Value("${jdbc.url}") String url,
            @Value("${jdbc.username}") String username,
            @Value("${jdbc.password}") String password) {
        HikariConfig config = new HikariConfig();
        config.setDriverClassName(driver);
        config.setJdbcUrl(url);
        config.setUsername(username);
        config.setPassword(password);
        return new HikariDataSource(config);
    }

    @Bean
    public SqlSessionFactory sqlSessionFactory(DataSource dataSource, ApplicationContext context) throws Exception {
        SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
        factory.setConfigLocation(context.getResource("classpath:mybatis-config.xml"));
        // core WAR 안의 표준 *Mapper.xml만 한 번 읽는다. classpath*: 패턴이나
        // "Mapper 2.xml" 같은 stale 복제 파일은 같은 namespace를 중복 등록할 수 있다.
        factory.setMapperLocations(context.getResources("classpath:mapper/**/*Mapper.xml"));
        factory.setDataSource(dataSource);
        return factory.getObject();
    }

    @Bean
    public DataSourceTransactionManager transactionManager(DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }

    @Bean
    public Clock applicationClock() {
        return Clock.system(ZoneId.of("Asia/Seoul"));
    }
}
