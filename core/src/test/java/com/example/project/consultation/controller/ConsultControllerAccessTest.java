package com.example.project.consultation.controller;

import com.example.project.common.api.ResponseCode;
import com.example.project.common.exception.CommonExceptionAdvice;
import com.example.project.common.exception.ServiceException;
import com.example.project.user.service.AccountAccessService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class ConsultControllerAccessTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        AccountAccessService blockedAccount = new AccountAccessService(null, null, null) {
            @Override
            public void requireRestrictedFeatureAccess(Long userId) {
                throw new ServiceException(ResponseCode.FORBIDDEN);
            }
        };
        ConsultController controller = new ConsultController(null, blockedAccount);
        mockMvc = standaloneSetup(controller)
                .setControllerAdvice(new CommonExceptionAdvice())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("7", null, List.of())
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("차단된 회원은 기존 Access Token으로도 AI 상담에 접근할 수 없다")
    void rejectBlockedAccount() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/ai/consult")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"증여 상담\"}"))
                .andExpect(status().isForbidden())
                .andReturn();

        int statusCode = objectMapper
                .readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .get("statusCode")
                .asInt();
        assertEquals(403, statusCode);
    }
}
