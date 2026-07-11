package de.johannesrabauer.steuererklaerung2025.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EncryptionServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void encrypts_and_decrypts_values_with_the_active_passphrase() {
        PassphraseService passphraseService = new PassphraseService(tempDir.resolve("passphrase.properties"), 12, 10_000, new java.security.SecureRandom());
        passphraseService.initialize("SehrSicherePassphrase123");
        EncryptionService encryptionService = new EncryptionService(passphraseService, new java.security.SecureRandom());

        String encryptedValue = encryptionService.encrypt("Anna Muster 50000.00");

        assertThat(encryptedValue).startsWith("enc:v1:");
        assertThat(encryptedValue).doesNotContain("Anna Muster 50000.00");
        assertThat(encryptionService.decrypt(encryptedValue)).isEqualTo("Anna Muster 50000.00");
    }

    @Test
    void refuses_to_encrypt_when_the_application_is_locked() {
        PassphraseService passphraseService = new PassphraseService(tempDir.resolve("passphrase.properties"), 12, 10_000, new java.security.SecureRandom());
        passphraseService.initialize("SehrSicherePassphrase123");
        passphraseService.lock();
        EncryptionService encryptionService = new EncryptionService(passphraseService, new java.security.SecureRandom());

        assertThatThrownBy(() -> encryptionService.encrypt("Anna Muster"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("locked");
    }
}
