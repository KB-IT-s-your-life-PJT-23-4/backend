package com.example.project.security;

import com.example.project.admin.auth.service.AdminAuthorizationService;
import com.example.project.common.api.ApiResponse;
import com.example.project.common.api.ResponseCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final AntPathRequestMatcher ADMIN_REQUEST_MATCHER =
            new AntPathRequestMatcher("/api/admin/**");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private final JwtProvider jwtProvider;
    private final TokenRevocationStore tokenRevocationStore;
    private final AdminAuthorizationService adminAuthorizationService;

    public SecurityConfig(
            JwtProvider jwtProvider,
            TokenRevocationStore tokenRevocationStore,
            AdminAuthorizationService adminAuthorizationService
    ) {
        this.jwtProvider = jwtProvider;
        this.tokenRevocationStore = tokenRevocationStore;
        this.adminAuthorizationService = adminAuthorizationService;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) ->
                                writeSecurityError(request, response, HttpStatus.UNAUTHORIZED, ResponseCode.UNAUTHORIZED))
                        .accessDeniedHandler((request, response, exception) ->
                                writeSecurityError(request, response, HttpStatus.FORBIDDEN, ResponseCode.FORBIDDEN)))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(ADMIN_REQUEST_MATCHER).hasAnyRole("ROOT", "MIDDLE", "DEFAULT")
                        .anyRequest().permitAll())
                .addFilterBefore(
                        new AdminAuthorizationFilter(adminAuthorizationService),
                        UsernamePasswordAuthenticationFilter.class
                )
                .addFilterBefore(
                        new JwtAuthFilter(jwtProvider, tokenRevocationStore),
                        AdminAuthorizationFilter.class
                );

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    private void writeSecurityError(
            HttpServletRequest request,
            HttpServletResponse response,
            HttpStatus httpStatus,
            ResponseCode responseCode
    ) throws IOException {
        response.setStatus(httpStatus.value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        OBJECT_MAPPER.writeValue(
                response.getWriter(),
                ApiResponse.error(responseCode, request.getRequestURI())
        );
    }
}
