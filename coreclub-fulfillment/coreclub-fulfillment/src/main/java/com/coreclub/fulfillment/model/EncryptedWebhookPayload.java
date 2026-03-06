package com.coreclub.fulfillment.model;

public record EncryptedWebhookPayload(
    String iv,
    String ciphertext,
    String authTag
) {

    public boolean isValid() {
        return isPresent(iv) && isPresent(ciphertext) && isPresent(authTag);
    }

    private boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }
}
