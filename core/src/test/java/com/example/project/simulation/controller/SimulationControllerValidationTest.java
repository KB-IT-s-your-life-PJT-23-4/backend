package com.example.project.simulation.controller;

import com.example.project.common.exception.CommonExceptionAdvice;
import com.example.project.security.JwtProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class SimulationControllerValidationTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        JwtProvider jwtProvider = new JwtProvider(
                "simulation-controller-test-secret-key-over-32-bytes",
                60_000L,
                120_000L
        );
        SimulationController controller = new SimulationController(null, jwtProvider);
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = standaloneSetup(controller)
                .setControllerAdvice(new CommonExceptionAdvice())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .setValidator(validator)
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Authorization 헤더가 없으면 AUTH_HEADER_MISSING을 반환한다")
    void missingAuthorizationHeader() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/gs/1"))
                .andExpect(status().isUnauthorized())
                .andReturn();

        JsonNode body = body(result);
        assertEquals(401, body.get("statusCode").asInt());
        assertEquals("AUTH_HEADER_MISSING", body.get("error").asText());
    }

    @Test
    @DisplayName("시뮬레이션 ID 형식이 잘못되면 INVALID_SIMULATION_ID를 반환한다")
    void invalidSimulationId() throws Exception {
        authenticate();

        MvcResult result = mockMvc.perform(get("/api/gs/not-a-number"))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertEquals("INVALID_SIMULATION_ID", body(result).get("error").asText());
    }

    @Test
    @DisplayName("실행 요청 필수값이 누락되면 INVALID_REQUEST를 반환한다")
    void invalidExecuteRequest() throws Exception {
        authenticate();

        MvcResult result = mockMvc.perform(post("/api/gs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "familyId": 31,
                                  "requestedAmount": 0
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertEquals("INVALID_REQUEST", body(result).get("error").asText());
    }

    private void authenticate() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("1", null, List.of())
        );
    }

    private JsonNode body(MvcResult result) throws Exception {
        return objectMapper.readTree(
                result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }
}
