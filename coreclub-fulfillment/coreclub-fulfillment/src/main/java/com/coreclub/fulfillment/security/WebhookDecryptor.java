package com.coreclub.fulfillment.security;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;

public final class WebhookDecryptor {
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH_BITS = 128;

    private final SecretKey secretKey;

    public WebhookDecryptor(byte[] rawKey) {
        if (rawKey == null || rawKey.length == 0) {
            throw new IllegalArgumentException("Encryption key must not be empty");
        }
        this.secretKey = new SecretKeySpec(rawKey.clone(), "AES");
    }

    public String decrypt(String ivBase64, String cipherBase64, String authTagBase64) {
        try {
            byte[] iv = Base64.getDecoder().decode(ivBase64);
            byte[] ciphertext = Base64.getDecoder().decode(cipherBase64);
            byte[] authTag = Base64.getDecoder().decode(authTagBase64);
            byte[] payload = new byte[ciphertext.length + authTag.length];
            System.arraycopy(ciphertext, 0, payload, 0, ciphertext.length);
            System.arraycopy(authTag, 0, payload, ciphertext.length, authTag.length);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec);
            byte[] plaintext = cipher.doFinal(payload);
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException | GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to decrypt webhook payload", exception);
        }
    }
}
