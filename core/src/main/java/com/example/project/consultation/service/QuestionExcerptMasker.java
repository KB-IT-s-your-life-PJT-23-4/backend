package com.example.project.consultation.service;

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

@Component
public class QuestionExcerptMasker {

    private static final int MAX_LENGTH = 1000;

    private static final Pattern RESIDENT_NUMBER = Pattern
            .compile("(?<!\\d)\\d{6}[- ]?[1-4]\\d{6}(?!\\d)");

    private static final Pattern PHONE_NUMBER =
            Pattern.compile("(?<!\\d)01[016789][ -]?\\d{3,4}[ -]?\\d{4}(?!\\d)");

    private static final Pattern EMAIL =
            Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

    private static final Pattern BIRTH_DATE =
            Pattern.compile("(?<!\\d)(?:19|20)\\d{2}[-./]\\d{1,2}[-./]\\d{1,2}(?!\\d)");

    public String mask(
            String question,
            Collection<String> knownNames
    ) {
        if (question == null || question.isBlank()){
            return null;
        }

        String masked = question.strip();

        masked = RESIDENT_NUMBER.matcher(masked).replaceAll("[주민등록번호]");

        masked = PHONE_NUMBER.matcher(masked).replaceAll("[전화번호]");

        masked = EMAIL.matcher(masked).replaceAll("[이메일]");

        masked = BIRTH_DATE.matcher(masked).replaceAll("[날짜]");

        if(knownNames != null) {
            List<String> names = knownNames.stream().filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(name -> name.length() >= 2)
                    .sorted(
                            Comparator.comparingInt(String::length).reversed()
                    )
                    .toList();

            for (String name : names) {
                masked = masked.replace(name, "[이름]");
            }
        }

        if (masked.length() > MAX_LENGTH) {
            masked = masked.substring(0, MAX_LENGTH);
        }

        return masked;
    }
}
