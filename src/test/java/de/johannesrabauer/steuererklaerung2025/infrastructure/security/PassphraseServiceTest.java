package de.johannesrabauer.steuererklaerung2025.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PassphraseServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void initializes_unlocks_and_relocks_the_application() {
        PassphraseService passphraseService = new PassphraseService(tempDir.resolve("passphrase.properties"), 12, 10_000, new java.security.SecureRandom());

        passphraseService.initialize("SehrSicherePassphrase123");

        assertThat(passphraseService.getState()).isEqualTo(PassphraseState.UNLOCKED);

        passphraseService.lock();

        assertThat(passphraseService.getState()).isEqualTo(PassphraseState.LOCKED);
        assertThat(passphraseService.unlock("SehrSicherePassphrase123")).isTrue();
        assertThat(passphraseService.getState()).isEqualTo(PassphraseState.UNLOCKED);
        assertThat(passphraseService.unlock("FalschePassphrase123")).isFalse();
    }

    @Test
    void rejects_too_short_passphrases() {
        PassphraseService passphraseService = new PassphraseService(tempDir.resolve("passphrase.properties"), 12, 10_000, new java.security.SecureRandom());

        assertThatThrownBy(() -> passphraseService.initialize("zu-kurz"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("too short");
    }
}
