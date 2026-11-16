package com.example.familytree.config;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/** Encrypts sensitive values before they are written to MySQL. */
@Converter
public class SensitiveStringConverter implements AttributeConverter<String,String> {
    private static final String PREFIX = "v1:";
    private static final SecureRandom RANDOM = new SecureRandom();

    @Override public String convertToDatabaseColumn(String value) {
        if (value == null || value.isBlank()) return value;
        try {
            byte[] iv = new byte[12];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(128, iv));
            byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            byte[] payload = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, payload, 0, iv.length);
            System.arraycopy(encrypted, 0, payload, iv.length, encrypted.length);
            return PREFIX + Base64.getEncoder().encodeToString(payload);
        } catch (Exception e) {
            throw new IllegalStateException("敏感数据加密失败", e);
        }
    }

    @Override public String convertToEntityAttribute(String value) {
        if (value == null || value.isBlank()) return value;
        if (!value.startsWith(PREFIX)) throw new IllegalStateException("敏感数据不是受支持的加密格式");
        try {
            byte[] payload = Base64.getDecoder().decode(value.substring(PREFIX.length()));
            byte[] iv = Arrays.copyOfRange(payload, 0, 12);
            byte[] encrypted = Arrays.copyOfRange(payload, 12, payload.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("敏感数据解密失败", e);
        }
    }

    private SecretKeySpec key() throws Exception {
        String configured = System.getenv("APP_DATA_ENCRYPTION_KEY");
        if (configured == null || configured.length() < 32) {
            throw new IllegalStateException("APP_DATA_ENCRYPTION_KEY 必须至少为 32 个字符");
        }
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(configured.getBytes(StandardCharsets.UTF_8));
        return new SecretKeySpec(digest, "AES");
    }
}
