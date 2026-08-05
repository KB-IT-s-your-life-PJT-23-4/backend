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
import java.util.regex.Matcher;
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

    /*
     * 닫는 태그. 여는 태그와 달리 줄을 바꿀 필요가 없어 그냥 지운다.
     * 태그 앞에는 TIGHT_ANGLE 이 넣은 공백이 남으므로 함께 걷어낸다.
     */
    private static final Pattern IMG_CLOSE_TAG = Pattern.compile("\\s*</img>");


    // {@code 삭제<2018.12.31>} 처럼 붙어 오는 꺾쇠 앞에 공백을 넣는다. 한글 뒤로만 한정한다. 표 안의 {@code │<과세표준>} 은 붙어 있는 게 맞다.

    private static final Pattern TIGHT_ANGLE = Pattern.compile("(?<=[가-힣])<");

    /*
     * 표 밖에 홀로 실린 수식의 분수선. 원문이 {@code 이익────────( 1 ＋ 10 )ⁿ────100} 처럼
     * 줄바꿈 없이 한 줄로 와서 그대로 두면 읽을 수 없다. 분수선 앞뒤에서 끊는다.
     * <p>괘선 표 안의 ─ 는 좌우가 다른 괘선 문자(┌ ┬ │ 등)나 정렬용 공백이라 이 조건에 걸리지 않는다.
     * 표를 건드리지 않기 위한 제약이므로 문자 집합을 넓히면 안 된다.
     */
    private static final Pattern FRACTION_BAR =
            Pattern.compile("(?<=[^─│┌┐└┘├┤┬┴┼\\s])(?=─{3,})|(?<=─)(?=[^─│┌┐└┘├┤┬┴┼\\s])");

    // 괘선 표는 한 줄로 실려 온다. 행이 닫히는 자리에서 끊는다.
    private static final Pattern TABLE_ROW_END = Pattern.compile("(?<=[┐┤┘])(?![\\n\\r]|$)");


     // 세로선으로 끝난 행 다음에 새 행이 열리는 자리.
    private static final Pattern TABLE_ROW_NEXT = Pattern.compile("(?<=│)(?=[│├└])");

    /*
     * 항번호는 ⑮ 까지만 원문자로 오고 16 항부터 {@code <16>} 형태로 바뀐다. 법제처 화면은 이것을
     * ⑯ 로 되돌려 보여주므로 그대로 두면 사람이 읽는 법령과 표기가 달라진다.
     * <p>본문 중간이 아니라 항이 시작하는 줄머리만 바꾼다. 번호 뒤 공백은 원문마다 달라서 손대지 않는다.
     */
    private static final Pattern PARAGRAPH_NO = Pattern.compile("(?m)^<(\\d{1,2})>");

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

    /*
     * 목이 호 아래가 아니라 항 레벨에 평평하게 오는 경우가 있다. 소속 호가 표시되지 않아
     * "가." 로 리셋되는 지점을 그룹 경계로 잡고 호에 순서대로 배분한다.
     * <p>"각 목" 을 언급한 호가 1순위다. 다만 그 조건만 걸면 배분되지 못하고 사라지는 목이 있다.
     * 원문이 "1. 주식등의 평가" 처럼 잘려 와 언급이 아예 없는 항이 실제로 존재한다(상증세법 제63조).
     * 그래서 언급한 호를 먼저 채우고, 그러고도 그룹이 남으면 언급 없는 호에도 순서대로 넘긴다.
     * 배분은 항상 문서 순서를 따르므로 앞뒤가 뒤집히지 않는다.
     */
    private void addItems(List<String> lines, Paragraph paragraph) {
        List<List<SubItem>> flatGroups = groupFlatSubItems(paragraph.getSubItems());
        List<Item> items = nullSafe(paragraph.getItems());

        int groupIndex = 0;
        int remainingMentions = countSubItemMentions(items);

        for (Item item : items) {
            String text = flatten(item.getContent());
            addIfPresent(lines, indent(ITEM_INDENT, item.getItemNo(), text));

            List<SubItem> subItems = nullSafe(item.getSubItems());

            if (subItems.isEmpty() && groupIndex < flatGroups.size()) {
                boolean mentioned = text.contains(SUB_ITEM_MENTION);

                if (mentioned) {
                    remainingMentions--;
                }

                // 남은 그룹이 뒤에 올 언급 호보다 많으면, 이 호가 받아야 할 몫이 있다는 뜻이다.
                if (mentioned || flatGroups.size() - groupIndex > remainingMentions) {
                    subItems = flatGroups.get(groupIndex++);
                }
            }

            addSubItems(lines, subItems);
        }

        // 어느 호에도 배분되지 못한 목은 버리지 않고 항 끝에 붙인다.
        // 위치가 어긋나는 것보다 조문 내용이 통째로 사라지는 쪽이 훨씬 나쁘다.
        while (groupIndex < flatGroups.size()) {
            addSubItems(lines, flatGroups.get(groupIndex++));
        }
    }

    /* 목을 받을 수 있는 호는 자기 목이 없는 호뿐이다. 배분 여유를 따질 때도 같은 기준으로 센다. */
    private int countSubItemMentions(List<Item> items) {
        int count = 0;

        for (Item item : items) {
            if (nullSafe(item.getSubItems()).isEmpty() && flatten(item.getContent()).contains(SUB_ITEM_MENTION)) {
                count++;
            }
        }

        return count;
    }

    private void addSubItems(List<String> lines, List<SubItem> subItems) {
        for (SubItem subItem : subItems) {
            addIfPresent(lines,
                    indent(SUB_ITEM_INDENT, subItem.getSubItemNo(), flatten(subItem.getContent())));
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
        String normalized = restoreParagraphNumbers(content);
        normalized = IMG_TAG.matcher(normalized).replaceAll("\n\n");
        normalized = IMG_CLOSE_TAG.matcher(normalized).replaceAll("");
        normalized = FRACTION_BAR.matcher(normalized).replaceAll("\n");
        normalized = TIGHT_ANGLE.matcher(normalized).replaceAll(" <");
        normalized = TABLE_ROW_END.matcher(normalized).replaceAll("\n");

        return TABLE_ROW_NEXT.matcher(normalized).replaceAll("\n");
    }

    /* 꺾쇠로 온 항번호를 원문자로 되돌린다. TIGHT_ANGLE 이 꺾쇠 앞에 공백을 넣기 전에 처리해야 한다. */
    private String restoreParagraphNumbers(String content) {
        Matcher matcher = PARAGRAPH_NO.matcher(content);
        StringBuilder restored = new StringBuilder();

        while (matcher.find()) {
            String circled = circledNumber(Integer.parseInt(matcher.group(1)));

            matcher.appendReplacement(restored,
                    circled == null ? Matcher.quoteReplacement(matcher.group()) : circled);
        }

        return matcher.appendTail(restored).toString();
    }

    /*
     * 원문자는 유니코드에서 세 구간에 흩어져 있다. ①~⑳ 다음이 ㉑~㉟, 그다음이 ㊱~㊿ 다.
     * 구간 밖(50 초과)은 대응하는 문자가 없으므로 null 을 돌려 원문을 그대로 남긴다.
     */
    private String circledNumber(int number) {
        if (number >= 1 && number <= 20) {
            return String.valueOf((char) ('①' + number - 1));
        }

        if (number >= 21 && number <= 35) {
            return String.valueOf((char) ('㉑' + number - 21));
        }

        if (number >= 36 && number <= 50) {
            return String.valueOf((char) ('㊱' + number - 36));
        }

        return null;
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
