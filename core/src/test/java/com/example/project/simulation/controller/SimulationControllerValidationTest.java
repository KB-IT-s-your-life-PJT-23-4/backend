package com.example.project.simulation.controller;

import com.example.project.common.api.ResponseCode;
import com.example.project.common.api.Pagination;
import com.example.project.common.exception.CommonExceptionAdvice;
import com.example.project.common.exception.ServiceException;
import com.example.project.security.JwtProvider;
import com.example.project.simulation.domain.SimulationStatus;
import com.example.project.simulation.dto.response.SimulationHistoryResponse;
import com.example.project.simulation.service.SimulationHistoryService;
import com.example.project.user.service.AccountAccessService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class SimulationControllerValidationTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private TestAccountAccessService accountAccessService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        JwtProvider jwtProvider = new JwtProvider(
                "simulation-controller-test-secret-key-over-32-bytes",
                60_000L,
                120_000L
        );
        accountAccessService = new TestAccountAccessService();
        SimulationController controller = new SimulationController(
                null,
                null,
                new TestSimulationHistoryService(),
                jwtProvider,
                accountAccessService
        );
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
    @DisplayName("실행 요청 필수값이 누락되면 INVALID_SIMULATION_REQUEST를 반환한다")
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

        assertEquals(
                "INVALID_SIMULATION_REQUEST",
                body(result).get("error").asText()
        );
    }

    @Test
    @DisplayName("과거 증여 예정일은 INVALID_SIMULATION_REQUEST를 반환한다")
    void rejectPastGiftDate() throws Exception {
        authenticate();

        MvcResult result = mockMvc.perform(post("/api/gs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "familyId": 31,
                                  "requestedAmount": 100000000,
                                  "taxPaymentMethod": "RECIPIENT_PAYS",
                                  "investmentPeriodMonths": 120,
                                  "giftDate": "2000-01-01"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertEquals(
                "INVALID_SIMULATION_REQUEST",
                body(result).get("error").asText()
        );
    }

    @Test
    @DisplayName("상품 버전 ID 형식이 잘못되면 INVALID_PRODUCT_VERSION_ID를 반환한다")
    void invalidProductVersionId() throws Exception {
        authenticate();

        MvcResult result = mockMvc.perform(
                        get("/api/gs/1/products/not-a-number"))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertEquals(
                "INVALID_PRODUCT_VERSION_ID",
                body(result).get("error").asText()
        );
    }

    @Test
    @DisplayName("최종 저장 필수값이 누락되면 INVALID_SAVE_REQUEST를 반환한다")
    void invalidSaveRequest() throws Exception {
        authenticate();

        MvcResult result = mockMvc.perform(patch("/api/gs/1/save")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "version": 1,
                                  "replaceExistingSaved": false,
                                  "productSelections": []
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertEquals(
                "INVALID_SAVE_REQUEST",
                body(result).get("error").asText()
        );
    }

    @Test
    @DisplayName("이력 조회의 수증자 ID 형식이 잘못되면 INVALID_FAMILY_ID를 반환한다")
    void invalidHistoryFamilyId() throws Exception {
        authenticate();

        MvcResult result = mockMvc.perform(
                        get("/api/gs").param("familyId", "not-a-number"))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertEquals("INVALID_FAMILY_ID", body(result).get("error").asText());
    }

    @Test
    @DisplayName("이력 조회의 페이지 형식이 잘못되면 INVALID_PAGE_REQUEST를 반환한다")
    void invalidHistoryPage() throws Exception {
        authenticate();

        MvcResult result = mockMvc.perform(
                        get("/api/gs").param("page", "not-a-number"))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertEquals("INVALID_PAGE_REQUEST", body(result).get("error").asText());
    }

    @Test
    @DisplayName("차단된 회원은 기존 Access Token으로도 증여 시뮬레이션에 접근할 수 없다")
    void rejectBlockedAccount() throws Exception {
        authenticate();
        accountAccessService.blocked = true;

        MvcResult result = mockMvc.perform(get("/api/gs/1"))
                .andExpect(status().isForbidden())
                .andReturn();

        assertEquals(403, body(result).get("statusCode").asInt());
    }

    @Test
    @DisplayName("차단된 회원도 기존 Access Token으로 자신의 시뮬레이션 이력을 조회할 수 있다")
    void allowBlockedAccountToReadHistory() throws Exception {
        authenticate();
        accountAccessService.blocked = true;

        MvcResult result = mockMvc.perform(get("/api/gs"))
                .andExpect(status().isOk())
                .andReturn();

        assertEquals(200, body(result).get("statusCode").asInt());
        assertEquals(
                101L,
                body(result).path("data").path("items").get(0).path("simulationId").asLong()
        );
        assertEquals(0, accountAccessService.accessCheckCount);
    }

    @Test
    @DisplayName("차단된 회원은 새로운 증여 시뮬레이션을 실행할 수 없다")
    void rejectBlockedAccountExecute() throws Exception {
        authenticate();
        accountAccessService.blocked = true;

        mockMvc.perform(post("/api/gs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "familyId": 31,
                                  "requestedAmount": 100000000,
                                  "taxPaymentMethod": "RECIPIENT_PAYS",
                                  "investmentPeriodMonths": 120,
                                  "giftDate": "2099-01-01"
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("차단된 회원은 시뮬레이션을 최종 저장할 수 없다")
    void rejectBlockedAccountSave() throws Exception {
        authenticate();
        accountAccessService.blocked = true;

        mockMvc.perform(patch("/api/gs/1/save")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "version": 1,
                                  "selectedPortfolioId": 11,
                                  "replaceExistingSaved": false,
                                  "productSelections": [
                                    {
                                      "simulationProductId": 21,
                                      "preferentialConditionCodes": []
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isForbidden());
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

    private static final class TestAccountAccessService extends AccountAccessService {

        private boolean blocked;
        private int accessCheckCount;

        private TestAccountAccessService() {
            super(
                    null, null, null,
                    com.example.project.support.PiiTestSupport.protectionService()
            );
        }

        @Override
        public void requireRestrictedFeatureAccess(Long userId) {
            accessCheckCount++;
            if (blocked) {
                throw new ServiceException(ResponseCode.FORBIDDEN);
            }
        }
    }

    private static final class TestSimulationHistoryService extends SimulationHistoryService {

        private TestSimulationHistoryService() {
            super(
                    null, null,
                    com.example.project.support.PiiTestSupport.protectionService()
            );
        }

        @Override
        public SimulationHistoryResponse getHistory(
                Long userId,
                String status,
                Long familyId,
                Integer page,
                Integer size
        ) {
            int resolvedPage = page == null ? 0 : page;
            int resolvedSize = size == null ? 10 : size;
            return new SimulationHistoryResponse(
                    List.of(new SimulationHistoryResponse.Item(
                            101L,
                            SimulationStatus.SAVED,
                            1L,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null
                    )),
                    Pagination.of(resolvedPage, resolvedSize, 1, 1)
            );
        }
    }
}
