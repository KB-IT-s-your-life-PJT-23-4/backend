package com.example.project.batch.law.chunk.processor;

import com.example.project.batch.law.dto.raw.ArticleUnit;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

public class Test {
    private static final String JSON = """
            {
              "조문번호": "2",
              "조문키": "0002001",
              "조문제목": "정의",
              "조문여부": "조문",
              "조문시행일자": "20251001",
              "조문내용": "제2조(정의) 이 법에서 사용하는 용어의 뜻은 다음과 같다. <개정 2020.12.22>",
              "항": {
                "호": [
                  { "호번호": "1.", "호내용": "1. \\"상속\\"이란 「민법」 제5편에 따른 상속을 말하며, 다음 각 목의 것을 포함한다." },
                  { "호번호": "2.", "호내용": "2. \\"상속개시일\\"이란 피상속인이 사망한 날을 말한다." },
                  { "호번호": "3.", "호내용": "3. \\"상속재산\\"이란 피상속인에게 귀속되는 모든 재산을 말하며, 다음 각 목의 물건을 포함한다." },
                  { "호번호": "4.", "호내용": "4. 삭제<2018.12.31>" }
                ],
                "목": [
                  { "목번호": "가.", "목내용": "가. 유증(遺贈)" },
                  { "목번호": "나.", "목내용": "나. 「민법」 제562조에 따른 증여" },
                  { "목번호": "가.", "목내용": "가. 금전으로 환산할 수 있는 경제적 가치가 있는 모든 물건" }
                ]
              }
            }
            """;

    public static void main(String[] args) throws JsonProcessingException {

        ObjectMapper objectMapper = new ObjectMapper()
                .configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true);
        ArticleUnit unit = objectMapper.readValue(JSON, ArticleUnit.class);

        System.out.println(new ArticleContentAssembler().assembleArticle(unit));
    }
}
