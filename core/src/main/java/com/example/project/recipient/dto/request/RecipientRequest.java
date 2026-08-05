package com.example.project.recipient.dto.request;

import lombok.Data;
import lombok.RequiredArgsConstructor;

import javax.validation.constraints.PastOrPresent;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import java.time.LocalDate;

@Data
@RequiredArgsConstructor
public class RecipientRequest {

    @Size(min = 2, max = 100, message = "수증자 이름은 2자 이상 100자 이하여야 합니다")
    @Pattern(regexp = "^\\S(?:.*\\S)?$", message = "수증자 이름 앞뒤에 공백을 입력할 수 없습니다")
    private String familyName;

    @Pattern(regexp = "LINEAL_DESCENDANT|OTHER", message = "지원하지 않는 수증자 관계입니다")
    private String relation;

    @PastOrPresent(message = "생년월일은 오늘 이후일 수 없습니다")
    private LocalDate birthDate;

    @Size(max = 255, message = "프로필 이미지는 255자 이하여야 합니다")
    private String familyImg;

}
