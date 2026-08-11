package com.example.project.ocr.service;

import com.example.project.ocr.domain.OcrBlock;
import com.example.project.ocr.dto.response.GiftFilingOcrResponse;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 증여세과세표준신고 및 자진납부계산서에서 증여일·금액·종류·수증자를 뽑는다.
 *
 * <p>clova OCR 은 표의 칸을 조각조각 돌려주므로 먼저 y 좌표로 행을 복원한다. 그래야 "⑮증여재산가액"
 * 이라는 항목명과 그 오른쪽 칸의 금액이 한 줄로 이어져 "이 항목의 값"을 지목할 수 있다.
 */

@Component
public class GiftFilingParser {

    // 날짜 및 금액
    private static final Pattern DOTTED_DATE = Pattern.compile("(19|20)\\d{2}\\s*[.\\-/]\\s*\\d{1,2}\\s*[.\\-/]\\s*\\d{1,2}");
    private static final Pattern AMOUNT = Pattern.compile("\\d{1,3}(?:,\\d{3})+|\\d+");
    private static final Pattern GROUPED_AMOUNT = Pattern.compile("\\d{1,3}(?:,\\d{3})+");
    private static final Pattern KOREAN_WORD = Pattern.compile("[가-힣]{2,5}");

    /**
     * 서식 자체에 인쇄된 날짜. 이 서식은 첫 줄이 "[별지 제10호서식] &lt;개정 2015.3.13&gt;" 이라
     * 문서에서 가장 먼저 나오는 점 날짜가 증여일이 아니라 서식 개정일이다.
     */
    private static final Pattern FORM_BOILERPLATE = Pattern.compile("개정|제정|별지|서식|시행규칙|시행령");

    /**
     * 이 서식은 항목명에 법조문이 붙어 있어("증여재산가산액(상속세 및 증여세법 제47조제2항)") 그대로 두면 47 을 금액으로 읽어서 빼야됨
     */
    private static final Pattern LAW_REFERENCE = Pattern.compile("\\([^)]*\\)|제\\s*\\d+\\s*조(\\s*의\\s*\\d+)?|제\\s*\\d+\\s*항");
    private static final List<String> GIFT_TYPES = List.of(
            "현금", "예금", "적금", "아파트", "주택", "토지", "건물", "부동산",
            "주식", "채권", "펀드", "자동차", "회원권", "보험금");

    private static final String LABEL_TEXT = "수증자성명성별주민등록번호주소전자우편주소증여자와의관계관리번호증여재산";

    public GiftFilingOcrResponse parse(List<OcrBlock> blocks) {
        List<String> lines = lines(blocks);
        LocalDate giftDate = giftDate(lines);
        GiftFilingOcrResponse result = new GiftFilingOcrResponse();
        // 서비스가 수증자 매칭 경고를 덧붙이므로 응답이 들고 있는 리스트를 그대로 쓴다.
        List<String> warnings = result.getWarnings();

        if (giftDate == null) {
            warnings.add("증여일을 읽지 못했어요. 직접 입력해 주세요.");
        }
        result.setGiftDate(giftDate);

        applyAmount(lines, result, warnings);

        result.setGiftType(giftType(lines));
        result.setRecipientName(recipientName(lines));

        return result;
    }

    private List<String> lines(List<OcrBlock> blocks) {
        if (blocks.isEmpty()) {
            return List.of();
        }

        double tolerance = medianHeight(blocks) * 0.6;
        List<OcrBlock> sorted = blocks.stream().sorted(Comparator.comparingDouble(OcrBlock::getY))
                .collect(Collectors.toList());

        List<List<OcrBlock>> rows = new ArrayList<>();
        List<OcrBlock> current = new ArrayList<>();
        double anchor = sorted.get(0).getY();

        for (OcrBlock block : sorted) {
            if (!current.isEmpty() && Math.abs(block.getY() - anchor) > tolerance) {
                rows.add(current);
                current = new ArrayList<>();
                anchor = block.getY();
            }

            current.add(block);
        }
        rows.add(current);

        return rows.stream().map(row -> row.stream().sorted(Comparator.comparingDouble(OcrBlock::getX))
                        .map(OcrBlock::getText)
                        .collect(Collectors.joining(" ")))
                .collect(Collectors.toList());
    }

    private double medianHeight(List<OcrBlock> blocks) {
        double[] heights = blocks.stream().mapToDouble(OcrBlock::getHeight).sorted().toArray();

        return heights[heights.length / 2];
    }

    /**
     * 증여일. 맨 아래 신고일은 "2016 년 04 월 19 일" 형태라 점 날짜와 구분되지만, 맨 위 서식
     * 개정일은 "&lt;개정 2015.3.13&gt;" 이라 형태가 같고 증여일보다 먼저 나온다. 그래서 상용구 줄을
     * 후보에서 빼고, ⑨증여일이 실제로 있는 증여재산 명세 행을 먼저 본 뒤 없을 때만 나머지를 훑는다.
     */
    private LocalDate giftDate(List<String> lines) {
        List<String> candidates = lines.stream()
                .filter(line -> !FORM_BOILERPLATE.matcher(line).find())
                .collect(Collectors.toList());

        LocalDate propertyRowDate = firstPastDate(candidates.stream()
                .filter(this::looksLikePropertyRow)
                .collect(Collectors.toList()));

        return propertyRowDate != null ? propertyRowDate : firstPastDate(candidates);
    }

    /**
     * 명세 행에는 증여일과 함께 재산 종류나 콤마가 찍힌 금액이 같은 줄에 온다.
     * 주민등록번호·관리번호처럼 숫자만 있는 행과 구분하는 기준이다.
     */
    private boolean looksLikePropertyRow(String line) {
        return firstGiftType(line) != null || GROUPED_AMOUNT.matcher(line).find();
    }

    /** 미래 날짜는 인식 오류로 보고 버린다(증여일은 이미 일어난 일자). */
    private LocalDate firstPastDate(List<String> lines) {
        LocalDate today = LocalDate.now();

        for (String line : lines) {
            Matcher matcher = DOTTED_DATE.matcher(line);

            while (matcher.find()) {
                LocalDate date = toDate(matcher.group());
                if (date != null && !date.isAfter(today)) {
                    return date;
                }
            }
        }

        return null;
    }

    /**
     * 날짜 뽑기
     */
    private LocalDate toDate(String token) {
        String[] parts = token.split("[.\\-/]");

        if (parts.length != 3) {
            return null;
        }

        try {
            return LocalDate.of(
                    Integer.parseInt(parts[0].trim()),
                    Integer.parseInt(parts[1].trim()),
                    Integer.parseInt(parts[2].trim())
            );
        } catch (RuntimeException exception) {
            return null;
        }
    }

    /**
     * 금액을 세 곳에서 뽑아 대조한 뒤 결과에 채운다. 같은 값이 여러 항목에 반복되는 서식이라
     * 하나만 읽고 믿으면 숫자 한 자리를 잘못 읽어도 알 수 없다.
     *
     * <ul>
     *   <li>⑮ 증여재산가액</li>
     *   <li>㉒ 증여세과세가액 (가산·비과세·채무가 없으면 ⑮ 와 같다)</li>
     *   <li>증여재산 명세의 합계 행</li>
     * </ul>
     */
    private void applyAmount(List<String> lines, GiftFilingOcrResponse result, List<String> warnings) {
        LinkedHashMap<String, Long> candidates = new LinkedHashMap<>();
        putIfPresent(candidates, "증여재산가액", amountAfterKeyword(lines, "증여재산가액"));
        putIfPresent(candidates, "증여세과세가액", amountAfterKeyword(lines, "증여세과세가액"));
        putIfPresent(candidates, "합계", totalRowAmount(lines));

        if (candidates.isEmpty()) {
            warnings.add("증여 금액을 읽지 못했어요. 직접 입력해 주세요.");
            return;
        }

        result.setAmount(candidates.values().iterator().next());

        if (new HashSet<>(candidates.values()).size() > 1) {
            String detail = candidates.entrySet().stream()
                    .map(entry -> entry.getKey() + " " + String.format("%,d", entry.getValue()) + "원")
                    .collect(Collectors.joining(", "));
            warnings.add("신고서의 금액 항목이 서로 달라요(" + detail + "). 확인 후 입력해 주세요.");
            return;
        }

        // 한 곳에서만 읽은 것은 "확인해서 맞은 것"과 다르다. 값은 쓰되 검증됐다고 하지 않는다.
        if (candidates.size() == 1) {
            warnings.add("금액을 신고서 한 곳에서만 읽어 교차 확인을 못 했어요. 값을 확인해 주세요.");
            return;
        }

        result.setAmountVerified(true);
    }

    private void putIfPresent(LinkedHashMap<String, Long> candidates, String key, Long value) {
        if (value != null) {
            candidates.put(key, value);
        }
    }

    private Long amountAfterKeyword(List<String> lines, String keyword) {
        for (String line : lines) {
            String compact = compact(line);
            int index = compact.indexOf(keyword);

            if (index < 0) {
                continue;
            }

            Matcher matcher = AMOUNT.matcher(compact);
            if (matcher.find(index + keyword.length())) {
                return parseAmount(matcher.group());
            }
        }

        return null;
    }

    /**
     * 합계 행은 항목명이 "계" 하나뿐이다. 금액 칸이 오른쪽 끝이라 마지막 숫자를 쓴다.
     */
    private Long totalRowAmount(List<String> lines) {
        for (String line : lines) {
            if (!"계".equals(line.replaceAll("[^가-힣]", ""))) {
                continue;
            }

            Long last = null;
            Matcher matcher = AMOUNT.matcher(compact(line));
            while (matcher.find()) {
                last = parseAmount(matcher.group());
            }

            if (last != null) {
                return last;
            }
        }


        return null;
    }

    private String compact(String line) {
        return LAW_REFERENCE.matcher(line).replaceAll(" ").replaceAll("\\s+", "");
    }

    private Long parseAmount(String token) {
        try {
            return Long.parseLong(token.replaceAll(",", ""));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    /**
     * 증여일이 적힌 행이 재산 명세 행이라 종류 칸도 같은 행에 있다.
     */
    private String giftType(List<String> lines) {
        for (String line : lines) {
            if (!DOTTED_DATE.matcher(line).find()) {
                continue;
            }

            String found = firstGiftType(line);
            if (found != null) {
                return found;
            }
        }

        for (String line : lines) {
            String found = firstGiftType(line);
            if (found != null) {
                return found;
            }
        }

        return null;
    }

    private String firstGiftType(String line) {
        String compact = compact(line);

        return GIFT_TYPES.stream().filter(compact::contains).findFirst().orElse(null);
    }

    private String recipientName(List<String> lines) {
        for (String line : lines) {
            String compact = compact(line);

            if (!compact.contains("수증자") || !compact.contains("성명")) {
                continue;
            }
            Matcher matcher = KOREAN_WORD.matcher(compact.substring(compact.indexOf("성명") + 2));
            while (matcher.find()) {
                String candidate = matcher.group();
                if (!LABEL_TEXT.contains(candidate)) {
                    return candidate;
                }
            }
        }

        return null;
    }
}
