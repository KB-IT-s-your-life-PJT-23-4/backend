package com.example.project.batch.law.chunk.processor;

import lombok.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.stream.Collectors;

// 실제 본문 파싱
@Component
public class RevisionTagParser {

    //  \s* : 공백 0개 이상, < : < 문자, (전문개정|본조신설|제목개정|개정|신설) : 5개 중 하나, ([^>]*) : >가 아닌 문자 0개 이상, > : >문자
    private static final Pattern TAG = Pattern.compile("\\s*<(전문개정|본조신설|제목개정|개정|신설)([^>]*)>");
    private static final Pattern DATE = Pattern.compile("(\\d{4})\\.\\s*(\\d{1,2})\\.\\s*(\\d{1,2})");

    public Result parse(String text) {
        if (text == null || text.isEmpty()) {
            return new Result(text, null, null);
        }

        TreeSet<Revision> revisions = new TreeSet<>();
        Matcher tagMatcher = TAG.matcher(text);

        while (tagMatcher.find()) {
            String type = tagMatcher.group(1);
            Matcher dateMatcher = DATE.matcher(tagMatcher.group(2));

            while (dateMatcher.find()) {
                revisions.add(new Revision(toLocalDate(dateMatcher), type));
            }
        }

        String cleaned = TAG.matcher(text).replaceAll("");

        if (revisions.isEmpty()) {
            return new Result(cleaned, null, null);
        }

        String history = revisions.stream()
                .map(r -> r.type + " " + r.date)
                .collect(Collectors.joining("; "));
        LocalDate latest = revisions.last().date;

        return new Result(cleaned, history, latest);
    }


    public LocalDate toLocalDate(Matcher dateMatcher) {
        return LocalDate.of(
                Integer.parseInt(dateMatcher.group(1)),
                Integer.parseInt(dateMatcher.group(2)),
                Integer.parseInt(dateMatcher.group(3))
        );
    }

    @Value
    public static class Result {
        String cleanedContent;
        String revisionHistory;
        LocalDate latestRevisionDate;
    }

    private static final class Revision implements Comparable<Revision> {

        private final LocalDate date;

        // 개정 / 신설 / 전문개정
        private final String type;

        public Revision(LocalDate date, String type) {
            this.date = date;
            this.type = type;
        }

        @Override
        public int compareTo(Revision o) {
            int c = date.compareTo(o.date);
            return c != 0 ? c : type.compareTo(o.type);
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Revision)) {
                return false;
            }

            Revision r = (Revision) o;
            return date.equals(r.date) && type.equals(r.type);
        }

        @Override
        public int hashCode() {
            return date.hashCode() * 31 + type.hashCode();
        }
    }
}
