package com.example.project.consultation.crypto.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


/*
 * 암호문 VO
 * JSON 안에 저장하는 암호문 묶음입니다.
 *
 * GCM 인증 태그는 Java Cipher 결과의 ciphertext에 함께 포함됩니다.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EncryptedPayload {
    private String algorithm;
    private String keyId;
    private String iv;
    private String ciphertext;
}
