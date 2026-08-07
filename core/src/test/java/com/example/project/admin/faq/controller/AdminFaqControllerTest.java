package com.example.project.admin.faq.controller;

import com.example.project.admin.faq.dto.response.AdminFaqItemResponse;
import com.example.project.admin.faq.mapper.AdminFaqMapper;
import com.example.project.admin.faq.service.AdminFaqService;
import com.example.project.common.exception.CommonExceptionAdvice;
import com.example.project.consultation.domain.Faq;
import com.example.project.consultation.domain.FaqCategory;
import com.example.project.consultation.mapper.FaqCategoryMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class AdminFaqControllerTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private InMemoryAdminFaqMapper adminFaqMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        adminFaqMapper = new InMemoryAdminFaqMapper();
        adminFaqMapper.addCategory(category(1L, "증여세"));
        adminFaqMapper.addCategory(category(2L, "상품"));
        adminFaqMapper.addFaq(faq(10L, 1L, "증여세 신고는 언제 하나요?", "증여세 신고", "증여일이 속한 달의 말일부터 3개월 이내입니다."));
        adminFaqMapper.addFaq(faq(20L, 2L, "추천 상품이 있나요?", "상품 추천", "상황에 맞는 상품을 확인해 주세요."));

        AdminFaqService service = new AdminFaqService(
                adminFaqMapper,
                adminFaqMapper
        );
        AdminFaqController controller = new AdminFaqController(service);
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = standaloneSetup(controller)
                .setControllerAdvice(new CommonExceptionAdvice())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .setValidator(validator)
                .build();
    }

    @Test
    @DisplayName("FAQ 목록을 카테고리와 검색어로 필터링하고 페이지 정보와 함께 반환한다")
    void getFaqPage() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/admin/faq")
                        .param("page", "0")
                        .param("size", "1")
                        .param("categoryId", "1")
                        .param("keyword", "신고"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = body(result);
        assertSuccess(body, "/api/admin/faq");
        assertEquals(1, body.at("/data/faqs").size());
        assertEquals(10L, body.at("/data/faqs/0/faqId").asLong());
        assertEquals(1L, body.at("/data/faqs/0/categoryId").asLong());
        assertEquals(1L, body.at("/data/pagination/totalElements").asLong());
        assertEquals(1, body.at("/data/pagination/totalPages").asInt());
        assertTrue(body.at("/data/pagination/first").asBoolean());
        assertTrue(body.at("/data/pagination/last").asBoolean());
    }

    @Test
    @DisplayName("FAQ 목록의 잘못된 페이지 요청은 검증 오류를 반환한다")
    void rejectInvalidPagination() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/admin/faq")
                        .param("page", "-1")
                        .param("size", "101"))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertEquals(400, body(result).get("statusCode").asInt());
    }

    @Test
    @DisplayName("FAQ 카테고리 목록을 반환한다")
    void getFaqCategories() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/admin/faq/category"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = body(result);
        assertSuccess(body, "/api/admin/faq/category");
        assertEquals(2, body.at("/data/categories").size());
        assertEquals("증여세", body.at("/data/categories/0/categoryName").asText());
    }

    @Test
    @DisplayName("FAQ 카테고리를 생성하고 생성된 ID를 반환한다")
    void createCategory() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/faq/category")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "categoryId": null,
                                  "categoryName": "  이용 안내  "
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = body(result);
        assertSuccess(body, "/api/admin/faq/category");
        assertEquals(3L, body.at("/data/categoryId").asLong());
        assertEquals("이용 안내", body.at("/data/categoryName").asText());
        assertEquals("이용 안내", adminFaqMapper.categories.get(3L).getCategoryName());
    }

    @Test
    @DisplayName("빈 카테고리 이름은 생성하지 않는다")
    void rejectBlankCategoryName() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/faq/category")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "categoryName": "   "
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertEquals(407, body(result).get("statusCode").asInt());
        assertEquals(2, adminFaqMapper.categories.size());
    }

    @Test
    @DisplayName("FAQ 카테고리를 수정한다")
    void updateCategory() throws Exception {
        MvcResult result = mockMvc.perform(patch("/api/admin/faq/category")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "categoryId": 1,
                                  "categoryName": "  세금 신고  "
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = body(result);
        assertEquals(1L, body.at("/data/categoryId").asLong());
        assertEquals("세금 신고", body.at("/data/categoryName").asText());
        assertEquals("세금 신고", adminFaqMapper.categories.get(1L).getCategoryName());
    }

    @Test
    @DisplayName("카테고리 ID 없이 수정하면 잘못된 요청 오류를 반환한다")
    void rejectCategoryUpdateWithoutId() throws Exception {
        MvcResult result = mockMvc.perform(patch("/api/admin/faq/category")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "categoryName": "세금 신고"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertEquals(400, body(result).get("statusCode").asInt());
    }

    @Test
    @DisplayName("FAQ 카테고리를 삭제한다")
    void deleteCategory() throws Exception {
        MvcResult result = mockMvc.perform(delete("/api/admin/faq/category/{categoryId}", 2L))
                .andExpect(status().isOk())
                .andReturn();

        assertSuccess(body(result), "/api/admin/faq/category/2");
        assertFalse(adminFaqMapper.categories.containsKey(2L));
    }

    @Test
    @DisplayName("존재하지 않는 FAQ 카테고리를 삭제하면 찾을 수 없음 오류를 반환한다")
    void deleteMissingCategory() throws Exception {
        MvcResult result = mockMvc.perform(delete("/api/admin/faq/category/{categoryId}", 999L))
                .andExpect(status().isNotFound())
                .andReturn();

        assertEquals(404, body(result).get("statusCode").asInt());
    }

    @Test
    @DisplayName("FAQ를 생성하고 정규화된 내용을 반환한다")
    void createFaq() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/faq")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validFaqRequest(1L, "  새 질문  ", "  새 프롬프트  ", "  새 답변  ")))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = body(result);
        assertSuccess(body, "/api/admin/faq");
        assertEquals(21L, body.at("/data/faqId").asLong());
        assertEquals("새 질문", body.at("/data/question").asText());
        assertEquals("새 프롬프트", body.at("/data/prompt").asText());
        assertEquals("새 답변", body.at("/data/answer").asText());
        assertTrue(body.at("/data/showBranchButton").asBoolean());
    }

    @Test
    @DisplayName("필수 내용이 없는 FAQ는 생성하지 않는다")
    void rejectInvalidFaqCreation() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/faq")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "categoryId": 0,
                                  "question": "",
                                  "prompt": "",
                                  "answer": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertEquals(407, body(result).get("statusCode").asInt());
        assertEquals(2, adminFaqMapper.faqs.size());
    }

    @Test
    @DisplayName("FAQ를 수정한다")
    void updateFaq() throws Exception {
        MvcResult result = mockMvc.perform(patch("/api/admin/faq/{faqId}", 10L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validFaqRequest(2L, "수정 질문", "수정 프롬프트", "수정 답변")))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = body(result);
        assertEquals(10L, body.at("/data/faqId").asLong());
        assertEquals(2L, body.at("/data/categoryId").asLong());
        assertEquals("수정 질문", adminFaqMapper.faqs.get(10L).getQuestion());
    }

    @Test
    @DisplayName("존재하지 않는 FAQ를 수정하면 찾을 수 없음 오류를 반환한다")
    void updateMissingFaq() throws Exception {
        MvcResult result = mockMvc.perform(patch("/api/admin/faq/{faqId}", 999L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validFaqRequest(1L, "질문", "프롬프트", "답변")))
                .andExpect(status().isNotFound())
                .andReturn();

        assertEquals(404, body(result).get("statusCode").asInt());
    }

    @Test
    @DisplayName("FAQ를 삭제한다")
    void deleteFaq() throws Exception {
        MvcResult result = mockMvc.perform(delete("/api/admin/faq/{faqId}", 10L))
                .andExpect(status().isOk())
                .andReturn();

        assertSuccess(body(result), "/api/admin/faq/10");
        assertFalse(adminFaqMapper.faqs.containsKey(10L));
    }

    @Test
    @DisplayName("존재하지 않는 FAQ를 삭제하면 찾을 수 없음 오류를 반환한다")
    void deleteMissingFaq() throws Exception {
        MvcResult result = mockMvc.perform(delete("/api/admin/faq/{faqId}", 999L))
                .andExpect(status().isNotFound())
                .andReturn();

        assertEquals(404, body(result).get("statusCode").asInt());
    }

    private String validFaqRequest(Long categoryId, String question, String prompt, String answer) throws Exception {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("categoryId", categoryId);
        request.put("question", question);
        request.put("prompt", prompt);
        request.put("answer", answer);
        request.put("showBranchButton", true);
        request.put("showTaxOfficeButton", false);
        return objectMapper.writeValueAsString(request);
    }

    private JsonNode body(MvcResult result) throws Exception {
        return objectMapper.readTree(
                result.getResponse().getContentAsString(StandardCharsets.UTF_8)
        );
    }

    private void assertSuccess(JsonNode body, String path) {
        assertEquals(200, body.get("statusCode").asInt());
        assertEquals(path, body.get("path").asText());
        assertNotNull(body.get("timestamp"));
    }

    private FaqCategory category(Long id, String name) {
        return FaqCategory.builder()
                .id(id)
                .categoryName(name)
                .build();
    }

    private Faq faq(Long id, Long categoryId, String question, String prompt, String answer) {
        return Faq.builder()
                .id(id)
                .categoryId(categoryId)
                .question(question)
                .prompt(prompt)
                .answer(answer)
                .showBranchButton(false)
                .showTaxOfficeButton(false)
                .build();
    }

    private static class InMemoryAdminFaqMapper implements AdminFaqMapper, FaqCategoryMapper {

        private final Map<Long, FaqCategory> categories = new LinkedHashMap<>();
        private final Map<Long, Faq> faqs = new LinkedHashMap<>();
        private long categorySequence;
        private long faqSequence;

        void addCategory(FaqCategory category) {
            categories.put(category.getId(), category);
            categorySequence = Math.max(categorySequence, category.getId());
        }

        void addFaq(Faq faq) {
            faqs.put(faq.getId(), faq);
            faqSequence = Math.max(faqSequence, faq.getId());
        }

        @Override
        public List<AdminFaqItemResponse> selectFaqPage(
                Long categoryId,
                String keyword,
                long offset,
                int size
        ) {
            return filteredFaqs(categoryId, keyword).stream()
                    .skip(offset)
                    .limit(size)
                    .map(faq -> AdminFaqItemResponse.builder()
                            .faqId(faq.getId())
                            .categoryId(faq.getCategoryId())
                            .categoryName(categories.get(faq.getCategoryId()).getCategoryName())
                            .question(faq.getQuestion())
                            .showBranchButton(faq.isShowBranchButton())
                            .showTaxOfficeButton(faq.isShowTaxOfficeButton())
                            .createdAt(LocalDateTime.of(2026, 8, 7, 10, 0))
                            .updatedAt(LocalDateTime.of(2026, 8, 7, 10, 0))
                            .build())
                    .toList();
        }

        @Override
        public long countFaqs(Long categoryId, String keyword) {
            return filteredFaqs(categoryId, keyword).size();
        }

        @Override
        public long totalFaqCount(Long categoryId) {
            return filteredFaqs(categoryId, null).size();
        }

        @Override
        public Faq findFaqDetail(Long faqId) {
            return faqs.get(faqId);
        }

        @Override
        public int createCategory(FaqCategory faqCategory) {
            long id = ++categorySequence;
            setId(faqCategory, id);
            categories.put(id, faqCategory);
            return 1;
        }

        @Override
        public int updateCategory(FaqCategory faqCategory) {
            if (!categories.containsKey(faqCategory.getId())) {
                return 0;
            }
            categories.put(faqCategory.getId(), faqCategory);
            return 1;
        }

        @Override
        public int deleteByCategoryId(long categoryId) {
            return categories.remove(categoryId) == null ? 0 : 1;
        }

        @Override
        public int insertFaq(Faq faq) {
            long id = ++faqSequence;
            setId(faq, id);
            faqs.put(id, faq);
            return 1;
        }

        @Override
        public int updateFaq(Faq faq) {
            if (!faqs.containsKey(faq.getId())) {
                return 0;
            }
            faqs.put(faq.getId(), faq);
            return 1;
        }

        @Override
        public int deleteByFaqId(long faqId) {
            return faqs.remove(faqId) == null ? 0 : 1;
        }

        @Override
        public List<FaqCategory> findAll() {
            return List.copyOf(categories.values());
        }

        private List<Faq> filteredFaqs(Long categoryId, String keyword) {
            String normalizedKeyword = keyword == null ? null : keyword.trim();
            return faqs.values().stream()
                    .filter(faq -> categoryId == null || categoryId.equals(faq.getCategoryId()))
                    .filter(faq -> normalizedKeyword == null
                            || normalizedKeyword.isEmpty()
                            || faq.getQuestion().contains(normalizedKeyword)
                            || faq.getPrompt().contains(normalizedKeyword))
                    .sorted(Comparator.comparing(Faq::getId).reversed())
                    .toList();
        }

        private static void setId(Object target, long id) {
            try {
                Field field = target.getClass().getDeclaredField("id");
                field.setAccessible(true);
                field.set(target, id);
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException(exception);
            }
        }
    }
}
