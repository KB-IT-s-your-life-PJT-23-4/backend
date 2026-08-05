package com.example.project.batch.law.chunk.processor;

import com.example.project.batch.law.dto.raw.AppendixUnit;
import com.example.project.batch.law.dto.raw.ArticleUnit;
import com.example.project.batch.law.dto.raw.Item;
import com.example.project.batch.law.dto.raw.Paragraph;
import com.example.project.batch.law.dto.raw.SubItem;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/*
 * 조문내용 + 항 + 호 + 목을 content 한 덩어리로 조립한다. 1 조 = 1 청크라 이 문자열이 곧 RAG 청크다.
 * <p>들여쓰기로 계층을 표현한다. 조문내용·항내용은 들여쓰기 없음, 호는 2칸, 목은 4칸이다.
 * 번호(항번호·호번호·목번호)는 내용 앞에 이미 들어 있어 새로 붙이지 않고,
 * 번호와 본문 사이 간격만 두 칸으로 맞춘다.
 * <p>개정 태그({@code <개정 …>})는 여기서 건드리지 않는다. 본문에서 떼어내 따로 보존하는 일은
 * {@link RevisionTagParser} 담당이다.
 */
@Component
public class ArticleContentAssembler {

    private static final String ITEM_INDENT = "  ";
    private static final String SUB_ITEM_INDENT = "    ";
    private static final String NUMBER_GAP = "  ";

    // 목이 항 레벨에 평평하게 올 때 그룹 경계를 여는 첫 번째 목번호.

    private static final String FIRST_SUB_ITEM_NO = "가.";


    // 호가 자기 밑의 목을 가리킬 때 쓰는 표현. "다음 각 목의", "다음 각 목에" 등.
    private static final String SUB_ITEM_MENTION = "각 목";


    // 수식·표 이미지. 태그를 지우고 줄을 바꿔 뒤따라오는 표 문자가 본문과 붙지 않게 한다.
    private static final Pattern IMG_TAG = Pattern.compile("<img[^>]*>");


    // {@code 삭제<2018.12.31>} 처럼 붙어 오는 꺾쇠 앞에 공백을 넣는다. 한글 뒤로만 한정한다. 표 안의 {@code │<과세표준>} 은 붙어 있는 게 맞다.

    private static final Pattern TIGHT_ANGLE = Pattern.compile("(?<=[가-힣])<");

    // 괘선 표는 한 줄로 실려 온다. 행이 닫히는 자리에서 끊는다.
    private static final Pattern TABLE_ROW_END = Pattern.compile("(?<=[┐┤┘])(?![\\n\\r]|$)");


     // 세로선으로 끝난 행 다음에 새 행이 열리는 자리.
    private static final Pattern TABLE_ROW_NEXT = Pattern.compile("(?<=│)(?=[│├└])");

    public String assembleArticle(ArticleUnit article) {
        List<String> lines = new ArrayList<>();

        addIfPresent(lines, flatten(article.getContent()));

        for (Paragraph paragraph : nullSafe(article.getParagraphs())) {
            addIfPresent(lines, flatten(paragraph.getContent()));
            addItems(lines, paragraph);
        }

        return normalize(String.join("\n", lines));
    }

    /* 별표는 표 그대로가 본문이라 계층 조립 없이 펼치기만 한다. */
    public String assembleAppendix(AppendixUnit appendix) {
        return normalize(flatten(appendix.getContent()));
    }

    private void addItems(List<String> lines, Paragraph paragraph) {
        // 목이 호 아래가 아니라 항 레벨에 평평하게 오는 경우가 있다. 소속 호가 표시되지 않아 "가." 로 리셋되는 지점을 그룹 경계로 잡고, "각 목" 을 언급한 호에 순서대로 배분한다.
        List<List<SubItem>> flatGroups = groupFlatSubItems(paragraph.getSubItems());
        int groupIndex = 0;

        for (Item item : nullSafe(paragraph.getItems())) {
            String text = flatten(item.getContent());
            addIfPresent(lines, indent(ITEM_INDENT, item.getItemNo(), text));

            List<SubItem> subItems = nullSafe(item.getSubItems());

            if (subItems.isEmpty() && groupIndex < flatGroups.size() && text.contains(SUB_ITEM_MENTION)) {
                subItems = flatGroups.get(groupIndex++);
            }

            for (SubItem subItem : subItems) {
                addIfPresent(lines,
                        indent(SUB_ITEM_INDENT, subItem.getSubItemNo(), flatten(subItem.getContent())));
            }
        }
    }

    private List<List<SubItem>> groupFlatSubItems(List<SubItem> subItems) {
        List<List<SubItem>> groups = new ArrayList<>();

        for (SubItem subItem : nullSafe(subItems)) {
            String no = trimToEmpty(subItem.getSubItemNo());

            if (groups.isEmpty() || no.startsWith(FIRST_SUB_ITEM_NO)) {
                groups.add(new ArrayList<>());
            }

            groups.get(groups.size() - 1).add(subItem);
        }

        return groups;
    }

    /*
     * 들여쓰기 + 번호 + 본문. 번호는 본문 앞에 이미 붙어 있어 떼었다가 간격을 맞춰 다시 붙인다.
     * 본문이 여러 줄이면 첫 줄에만 들여쓰기가 걸린다. 표·수식은 원본 정렬을 살려야 하기 때문이다.
     */
    private String indent(String indent, String number, String text) {
        if (text.isEmpty()) {
            return "";
        }

        String no = trimToEmpty(number);

        if (no.isEmpty()) {
            return indent + text;
        }

        String body = text.startsWith(no) ? text.substring(no.length()).stripLeading() : text;

        // "1." 뒤가 비는 조문이 있어(삭제된 호) 간격만 남지 않도록 끝을 정리한다.
        return (indent + no + NUMBER_GAP + body).stripTrailing();
    }

    /*
     * 조문내용·항내용·호내용은 문자열 하나로 올 때도, 배열이나 중첩 배열로 올 때도 있다. 어느 쪽이든 줄바꿈으로 이어 하나의 문자열로 만든다.
     */
    private String flatten(JsonNode node) {
        if (node == null || node.isNull()) {
            return "";
        }

        if (node.isTextual()) {
            return node.asText().trim();
        }

        if (node.isArray()) {
            List<String> parts = new ArrayList<>();

            for (JsonNode child : node) {
                String part = flatten(child);

                if (!part.isEmpty()) {
                    parts.add(part);
                }
            }

            return String.join("\n", parts);
        }

        return "";
    }

    private String normalize(String content) {
        String normalized = IMG_TAG.matcher(content).replaceAll("\n\n");
        normalized = TIGHT_ANGLE.matcher(normalized).replaceAll(" <");
        normalized = TABLE_ROW_END.matcher(normalized).replaceAll("\n");

        return TABLE_ROW_NEXT.matcher(normalized).replaceAll("\n");
    }

    private void addIfPresent(List<String> lines, String line) {
        if (!line.isEmpty()) {
            lines.add(line);
        }
    }

    private <T> List<T> nullSafe(List<T> values) {
        return values == null ? List.of() : values;
    }

    private String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
