package com.example.project.consultation.service;

import com.example.project.common.api.ResponseCode;
import com.example.project.common.exception.ServiceException;
import com.example.project.consultation.client.FastApiClient;
import com.example.project.consultation.domain.EtfVO;
import com.example.project.consultation.domain.FamilyPreviousGiftVO;
import com.example.project.consultation.domain.PreparedConversation;
import com.example.project.consultation.domain.ProductVO;
import com.example.project.consultation.dto.fastapi.*;
import com.example.project.consultation.dto.request.ConsultClarificationRequest;
import com.example.project.consultation.dto.response.ConsultResponse;
import com.example.project.consultation.dto.response.ConversationHistoryResponse;
import com.example.project.consultation.mapper.ConsultationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDate;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Log4j2
@Service
@RequiredArgsConstructor
public class ConsultService {

    private static final int MIN_LENGTH = 2;
    private static final int MAX_LENGTH = 500;
    private static final int MINOR_AGE_STANDARD = 19;
    private static final String PARENT_TO_MINOR_CHILD = "parent_to_minor_child";
    private static final String PARENT_TO_ADULT_CHILD = "parent_to_adult_child";
    private static final Map<String, Object> INITIAL_FACTS = Map.of(
            "residency", "국내 거주자",
            "use_latest_tax_rate", true
    );

    private final FastApiClient fastApiClient;
    private final ConsultationMapper consultationMapper;
    private final ConversationPersistenceService conversationPersistenceService;

    // 최초 질문
    //public ConsultResponse consult(String question, Long userId) { // 동기 처리
    public Mono<ConsultResponse> consult(String question, Long userId) {
        validateQuestion(question);
        /* 동기처리
        ChatRequest request = new ChatRequest(
                null, // 최초 요청은 conversation_id가 null
                question,
                fetchFamilies(userId),
                fetchProduct(userId),
                INITIAL_FACTS               // 초기 facts
        );

        ChatResponse response = fastApiClient.startChat(request);
        return ConsultResponse.from(response);*/
        return fetchFamiliesAsync(userId)
                .flatMap(families -> {
                    List<String> familyNames = extractFamilyNames(families);

                    Map<String, Object> rawUserPayload = Map.of("question", question);

                    return prepareTurnAsync(
                            userId,
                            "QUESTION",
                            question,
                            rawUserPayload,
                            familyNames
                    ).flatMap(prepared -> {
                            Map<String, Object> facts = mergeFacts(
                                    null,
                                    prepared.getFacts()
                            );

                            ChatRequest request = new ChatRequest(
                                prepared.getConversationId(),
                                question,
                                families,
                                fetchAllProducts(),
                                fetchAllEtfProducts(),
                                facts,
                                prepared.getConversationHistory()
                        );

                        Mono<ChatResponse> responseMono = fastApiClient.startChat(request)
                                .onErrorResume(exception ->
                                        failTurnAsync(prepared)
                                                .then(Mono.error(exception)
                                                )
                                );

                        return responseMono.flatMap(
                                response -> completeTurnAsync(prepared, response)
                                        .thenReturn(response)
                        );
                        });
                })
                .map(ConsultResponse::from);
    }

    // 추가 답변 제출
    //public ConsultResponse answerClarification(ConsultClarificationRequest req, Long userId) { // 동가 처리
    public Mono<ConsultResponse> answerClarification(ConsultClarificationRequest req, Long userId) {
        if (req.getAnswers() == null || req.getAnswers().isEmpty()) {
            throw new ServiceException(ResponseCode.BAD_REQUEST);
        }
        /* 동기처리
        ClarificationRequest request = new ClarificationRequest(
                req.conversationId(),
                req.question(),
                req.intent(),
                req.requiresCalculation(),
                req.facts(),
                req.answers(),
                fetchFamilies(userId),
                fetchProduct(userId)
        );

        ChatResponse response = fastApiClient.submitClarification(request);
        return ConsultResponse.from(response);*/

        validateQuestion(req.getQuestion());

        return fetchFamiliesAsync(userId)
                .flatMap(families -> {
                    List<String> familyNames = extractFamilyNames(families);

                    return prepareTurnAsync(
                            userId,
                            "CLARIFICATION",
                            req.getQuestion(),
                            req,
                            familyNames
                    ).flatMap(prepared -> {
                        Map<String, Object> facts = mergeFacts(
                                req.getFacts(),
                                prepared.getFacts()
                        );

                        ClarificationRequest fastApiRequest = new ClarificationRequest(
                                prepared.getConversationId(),
                                req.getQuestion(),
                                req.getIntent(),
                                req.isRequiresCalculation(),
                                facts,
                                req.getAnswers(),
                                families,
                                fetchAllProducts(),
                                fetchAllEtfProducts(),
                                prepared.getConversationHistory()
                        );

                        Mono<ChatResponse> responseMono = fastApiClient
                                .submitClarification(fastApiRequest)
                        .onErrorResume(exception ->
                            failTurnAsync(prepared)
                                    .then(Mono.error(exception))
                        );

                        return responseMono.flatMap(response ->
                                completeTurnAsync(prepared, response)
                                        .thenReturn(response)
                                );
                    });
                })
                .map(ConsultResponse::from);
    }

    private void validateQuestion(String question) {
        log.debug("AI 질문 검증 시작. length={}",
                question == null ? -1 : question.length()
        );
        if (question == null || question.isBlank()) {
            throw new ServiceException(ResponseCode.BAD_REQUEST);
        }

        String trimmed = question.replaceAll("\\s", "");

        if (trimmed.length() < MIN_LENGTH) {
            throw new ServiceException(ResponseCode.VALIDATION_FAILED);
        }

        if (question.length() > MAX_LENGTH) {
            throw new ServiceException(ResponseCode.VALIDATION_FAILED);
        }
    }
    /* 동기 처리
    private List<FamilyData> fetchFamilies(Long userId) {
        if(userId == null){
            throw new ServiceException(ResponseCode.UNAUTHORIZED);
        }

        List<FamilyPreviousGiftVO> families = consultationMapper.selectAllByUserId(userId);

        return families.stream()
                .map(this::toFamilyData)
                .toList();
    }*/
    private Mono<List<FamilyData>> fetchFamiliesAsync(Long userId) {
        return Mono.fromCallable(() -> fetchFamilies(userId))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private List<FamilyData> fetchFamilies(Long userId) {
        if (userId == null) {
            throw new ServiceException(ResponseCode.UNAUTHORIZED);
        }

        List<FamilyPreviousGiftVO> families = consultationMapper.selectAllByUserId(userId);

        return families.stream()
                .map(this::toFamilyData)
                .toList();
    }

    private FamilyData toFamilyData(FamilyPreviousGiftVO family){
        Integer recipientAge = family.getRecipientAge();

        if(recipientAge == null || recipientAge < 0){
            throw new ServiceException(ResponseCode.DATABASE_ERROR);
        }

        boolean recipientIsMinor = recipientAge < MINOR_AGE_STANDARD;

        String relationshipType = recipientIsMinor ? PARENT_TO_MINOR_CHILD : PARENT_TO_ADULT_CHILD;

        boolean hasPreviousGifts = family.isHasPreviousGifts();

        Long previousGiftAmount = hasPreviousGifts ? family.getPreviousGiftAmount() : null;

        LocalDate previousGiftDate = hasPreviousGifts ? family.getPreviousGiftDate() : null;

        LocalDate deductionRenewDate = hasPreviousGifts ? family.getDeductionRenewalDate() : null;

        Boolean previousGiftSameDonor = hasPreviousGifts ? Boolean.TRUE : null;

        Long previouslyUsedDeduction = hasPreviousGifts ? requiredPreviousGiftAmount(previousGiftAmount) : 0L;

        return FamilyData.builder()
                .familyId(family.getFamilyId())
                .name(family.getName())
                .relationshipType(relationshipType)
                .giftAmount(null)
                .recipientIsMinor(recipientIsMinor)
                .hasPreviousGifts(hasPreviousGifts)
                .previousGiftAmount(previousGiftAmount)
                .previousGiftDate(previousGiftDate)
                .previousGiftSameDonor(previousGiftSameDonor)
                .previouslyUsedDeduction(previouslyUsedDeduction)
                .deductionRenewalDate(deductionRenewDate)
                .build();
    }

    private Long requiredPreviousGiftAmount(Long previousGiftAmount){
        if (previousGiftAmount == null) {
            throw new ServiceException(ResponseCode.DATABASE_ERROR);
        }

        return previousGiftAmount;
    }

    private List<ProductData> fetchAllProducts() {
        List<ProductVO> products = consultationMapper.selectAllOnSaleProducts();
        return products.stream()
                .map(p -> ProductData.builder()
                        .productName(p.getProductName())
                        .interestRate(p.getBaseRatePercent())
                        .preferentialCondition(p.getPreferentialCondition())
                        .build())
                .toList();
    }
    private List<EtfProductData> fetchAllEtfProducts() {
        List<EtfVO> etfProducts = consultationMapper.selectAllOnSaleEtfProducts();
        return etfProducts.stream()
                .map(e -> EtfProductData.builder()
                        .productName(e.getProductName())
                        .trackingIndex(e.getTrackingIndex())
                        .annualReturn10y(e.getAnnualReturn10y())
                        .build())
                .toList();
    }

    private List<String> extractFamilyNames(List<FamilyData> families) {
        return families.stream()
                .map(FamilyData::getName)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(name -> !name.isBlank())
                .distinct()
                .toList();
    }

    private Map<String, Object> mergeFacts(
            Map<String, Object> requestFacts,
            Map<String, Object> storedFacts
    ) {
        Map<String, Object> merged = new HashMap<>(INITIAL_FACTS);

        if (requestFacts != null) {
            merged.putAll(requestFacts);
        }

        /*
         * 서버가 보관한 사실을 마지막에 병합하여 클라이언트가 이전
         * 상담에서 확정된 값을 임의로 덮어쓰지 못하게 합니다.
         */
        if (storedFacts != null) {
            merged.putAll(storedFacts);
        }

        // FastAPI가 사실 값으로 null을 반환할 수 있으므로 null을 금지하는 Map.copyOf를 사용하지 않습니다.
        return Collections.unmodifiableMap(merged);
    }

    private Mono<PreparedConversation> prepareTurnAsync(
            Long userId,
            String turnType,
            String question,
            Object rawUserPayload,
            List<String> familyNames
    ) {
        return Mono.fromCallable(() ->
                conversationPersistenceService.prepareTurn(
                        userId,
                        turnType,
                        question,
                        rawUserPayload,
                        familyNames
                )
            ).subscribeOn(
                    Schedulers.boundedElastic()
        );
    }

    private Mono<Void> completeTurnAsync(PreparedConversation prepared, ChatResponse response) {
        return Mono.fromRunnable(() ->
                conversationPersistenceService.completeTurn(prepared, response)
        ).subscribeOn(Schedulers.boundedElastic()).then();
    }

    private Mono<Void> failTurnAsync(PreparedConversation prepared) {
        return Mono.fromRunnable(() ->
            conversationPersistenceService.failTurn(prepared)
        ).subscribeOn(Schedulers.boundedElastic()
        ).then();
    }

    public Mono<ConversationHistoryResponse> getHistory(Long userId) {
        if (userId == null) {
            return Mono.error(
                    new ServiceException(ResponseCode.UNAUTHORIZED)
            );
        }

        return Mono.fromCallable(() ->
            conversationPersistenceService.getCurrentHistory(userId)
            ).subscribeOn(Schedulers.boundedElastic()
        );
    }
}
