package de.johannesrabauer.steuererklaerung2025.infrastructure.security;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class EncryptionService {

    private static final String ENCRYPTION_PREFIX = "enc:v1:";
    private static final String AES_TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final int IV_LENGTH_BYTES = 12;

    private final PassphraseService passphraseService;
    private final SecureRandom secureRandom;

    @Autowired
    public EncryptionService(PassphraseService passphraseService) {
        this(passphraseService, new SecureRandom());
    }

    EncryptionService(PassphraseService passphraseService, SecureRandom secureRandom) {
        this.passphraseService = Objects.requireNonNull(passphraseService, "passphraseService must not be null");
        this.secureRandom = Objects.requireNonNull(secureRandom, "secureRandom must not be null");
    }

    public String encrypt(String value) {
        if (value == null) {
            return null;
        }

        byte[] iv = new byte[IV_LENGTH_BYTES];
        secureRandom.nextBytes(iv);
        byte[] encryptionKey = passphraseService.requireActiveEncryptionKey();

        try {
            Cipher cipher = Cipher.getInstance(AES_TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(encryptionKey, "AES"), new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] cipherText = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            byte[] payload = ByteBuffer.allocate(iv.length + cipherText.length)
                    .put(iv)
                    .put(cipherText)
                    .array();
            return ENCRYPTION_PREFIX + Base64.getEncoder().encodeToString(payload);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Failed to encrypt stored tax data.", exception);
        } finally {
            java.util.Arrays.fill(encryptionKey, (byte) 0);
        }
    }

    public String decrypt(String encryptedValue) {
        if (encryptedValue == null) {
            return null;
        }
        if (!encryptedValue.startsWith(ENCRYPTION_PREFIX)) {
            String preview = encryptedValue.substring(0, Math.min(encryptedValue.length(), 20));
            throw new IllegalStateException("Stored tax data uses an unsupported encryption format. Expected prefix '"
                    + ENCRYPTION_PREFIX + "' but found '" + preview + "'.");
        }

        byte[] payload = Base64.getDecoder().decode(encryptedValue.substring(ENCRYPTION_PREFIX.length()));
        byte[] iv = new byte[IV_LENGTH_BYTES];
        byte[] cipherText = new byte[payload.length - IV_LENGTH_BYTES];
        System.arraycopy(payload, 0, iv, 0, IV_LENGTH_BYTES);
        System.arraycopy(payload, IV_LENGTH_BYTES, cipherText, 0, cipherText.length);

        byte[] encryptionKey = passphraseService.requireActiveEncryptionKey();
        try {
            Cipher cipher = Cipher.getInstance(AES_TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(encryptionKey, "AES"), new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Stored tax data could not be decrypted with the current passphrase.", exception);
        } finally {
            java.util.Arrays.fill(encryptionKey, (byte) 0);
        }
    }
}
