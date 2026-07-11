package de.johannesrabauer.steuererklaerung2025.infrastructure.security;

import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public class EncryptionServices {

    private static EncryptionService encryptionService;

    public EncryptionServices(EncryptionService encryptionService) {
        EncryptionServices.encryptionService = encryptionService;
    }

    public static EncryptionService getEncryptionService() {
        return Objects.requireNonNull(encryptionService, "EncryptionService has not been initialized yet.");
    }
}
