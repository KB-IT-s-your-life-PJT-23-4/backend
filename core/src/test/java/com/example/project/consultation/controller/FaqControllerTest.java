package com.example.project.consultation.controller;

import com.example.project.common.exception.CommonExceptionAdvice;
import com.example.project.consultation.domain.Faq;
import com.example.project.consultation.domain.FaqCategory;
import com.example.project.consultation.mapper.FaqCategoryMapper;
import com.example.project.consultation.mapper.FaqMapper;
import com.example.project.consultation.service.FaqService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class FaqControllerTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

        FaqCategoryMapper categoryMapper = () -> List.of(
                category(1L, "증여세"),
                category(2L, "상품"),
                category(3L, "빈 카테고리")
        );
        InMemoryFaqMapper faqMapper = new InMemoryFaqMapper(List.of(
                faq(10L, 1L, "신고 기한은 언제인가요?", "신고 기한", "3개월 이내입니다.", false, true),
                faq(11L, 1L, "공제 한도는 얼마인가요?", "공제 한도", "관계에 따라 다릅니다.", false, false),
                faq(20L, 2L, "상품은 어떻게 고르나요?", "상품 선택", "목적에 맞게 선택해 주세요.", true, false)
        ));
        FaqController controller = new FaqController(
                new FaqService(categoryMapper, faqMapper)
        );

        mockMvc = standaloneSetup(controller)
                .setControllerAdvice(new CommonExceptionAdvice())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @Test
    @DisplayName("사용자 FAQ 목록을 카테고리 순서대로 그룹화해 반환한다")
    void getFaqList() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/ai/faq"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = body(result);
        assertEquals(200, body.get("statusCode").asInt());
        assertEquals("/api/ai/faq", body.get("path").asText());
        assertEquals(3, body.at("/data/categories").size());
        assertEquals("증여세", body.at("/data/categories/0/title").asText());
        assertEquals(2, body.at("/data/categories/0/items").size());
        assertEquals(10L, body.at("/data/categories/0/items/0/faqId").asLong());
        assertEquals("상품", body.at("/data/categories/1/title").asText());
        assertEquals(1, body.at("/data/categories/1/items").size());
        assertEquals("빈 카테고리", body.at("/data/categories/2/title").asText());
        assertEquals(0, body.at("/data/categories/2/items").size());
    }

    @Test
    @DisplayName("사용자 FAQ 답변과 버튼 표시 정보를 반환한다")
    void getFaqAnswer() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/ai/faq/{faqId}/answer", 10L))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = body(result);
        assertEquals(200, body.get("statusCode").asInt());
        assertEquals("/api/ai/faq/10/answer", body.get("path").asText());
        assertEquals(10L, body.at("/data/faqId").asLong());
        assertEquals("신고 기한은 언제인가요?", body.at("/data/question").asText());
        assertEquals("3개월 이내입니다.", body.at("/data/answer").asText());
        assertFalse(body.at("/data/showBranchButton").asBoolean());
        assertTrue(body.at("/data/showTaxOfficeButton").asBoolean());
    }

    @Test
    @DisplayName("존재하지 않는 사용자 FAQ 답변은 찾을 수 없음 오류를 반환한다")
    void getMissingFaqAnswer() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/ai/faq/{faqId}/answer", 999L))
                .andExpect(status().isNotFound())
                .andReturn();

        JsonNode body = body(result);
        assertEquals(404, body.get("statusCode").asInt());
        assertEquals("/api/ai/faq/999/answer", body.get("path").asText());
    }

    private JsonNode body(MvcResult result) throws Exception {
        return objectMapper.readTree(
                result.getResponse().getContentAsString(StandardCharsets.UTF_8)
        );
    }

    private FaqCategory category(Long id, String name) {
        return FaqCategory.builder()
                .id(id)
                .categoryName(name)
                .build();
    }

    private Faq faq(
            Long id,
            Long categoryId,
            String question,
            String prompt,
            String answer,
            boolean showBranchButton,
            boolean showTaxOfficeButton
    ) {
        return Faq.builder()
                .id(id)
                .categoryId(categoryId)
                .question(question)
                .prompt(prompt)
                .answer(answer)
                .showBranchButton(showBranchButton)
                .showTaxOfficeButton(showTaxOfficeButton)
                .build();
    }

    private static class InMemoryFaqMapper implements FaqMapper {

        private final Map<Long, Faq> faqs = new LinkedHashMap<>();

        private InMemoryFaqMapper(List<Faq> faqs) {
            faqs.forEach(faq -> this.faqs.put(faq.getId(), faq));
        }

        @Override
        public List<Faq> findAll() {
            return List.copyOf(faqs.values());
        }

        @Override
        public Faq findById(Long faqId) {
            return faqs.get(faqId);
        }
    }
}
