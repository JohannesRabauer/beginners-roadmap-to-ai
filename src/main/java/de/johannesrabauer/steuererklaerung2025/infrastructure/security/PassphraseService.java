package de.johannesrabauer.steuererklaerung2025.infrastructure.security;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class PassphraseService {

    private static final int DERIVED_KEY_LENGTH_BITS = 256;
    private static final int SALT_LENGTH_BYTES = 16;
    private static final int RECOMMENDED_MINIMUM_PASSPHRASE_LENGTH = 12;

    private static final Logger log = LoggerFactory.getLogger(PassphraseService.class);

    private final Path passphraseFile;
    private final int minimumPassphraseLength;
    private final int keyDerivationIterations;
    private final SecureRandom secureRandom;

    private byte[] activeEncryptionKey;

    @Autowired
    public PassphraseService(
            @Value("${steuererklaerung.security.passphrase-file}") String passphraseFile,
            @Value("${steuererklaerung.security.minimum-passphrase-length}") int minimumPassphraseLength,
            @Value("${steuererklaerung.security.key-derivation-iterations}") int keyDerivationIterations) {
        this(Path.of(passphraseFile), minimumPassphraseLength, keyDerivationIterations, new SecureRandom());
    }

    PassphraseService(Path passphraseFile, int minimumPassphraseLength, int keyDerivationIterations, SecureRandom secureRandom) {
        this.passphraseFile = Objects.requireNonNull(passphraseFile, "passphraseFile must not be null");
        if (minimumPassphraseLength < RECOMMENDED_MINIMUM_PASSPHRASE_LENGTH) {
            throw new IllegalArgumentException("The configured minimum passphrase length must be at least 12 characters.");
        }
        if (keyDerivationIterations <= 0) {
            throw new IllegalArgumentException("The key derivation iteration count must be positive.");
        }
        this.minimumPassphraseLength = minimumPassphraseLength;
        this.keyDerivationIterations = keyDerivationIterations;
        this.secureRandom = Objects.requireNonNull(secureRandom, "secureRandom must not be null");
    }

    public synchronized PassphraseState getState() {
        if (!Files.exists(passphraseFile)) {
            return PassphraseState.UNINITIALIZED;
        }
        return activeEncryptionKey == null ? PassphraseState.LOCKED : PassphraseState.UNLOCKED;
    }

    public synchronized void initialize(String passphrase) {
        validatePassphrase(passphrase);
        if (Files.exists(passphraseFile)) {
            throw new IllegalStateException("An application passphrase is already configured.");
        }

        byte[] encryptionSalt = randomBytes(SALT_LENGTH_BYTES);
        byte[] verificationSalt = randomBytes(SALT_LENGTH_BYTES);
        byte[] encryptionKey = deriveKey(passphrase, encryptionSalt);
        byte[] verificationHash = deriveKey(passphrase, verificationSalt);

        Properties properties = new Properties();
        properties.setProperty("version", "1");
        properties.setProperty("encryptionSalt", Base64.getEncoder().encodeToString(encryptionSalt));
        properties.setProperty("verificationSalt", Base64.getEncoder().encodeToString(verificationSalt));
        properties.setProperty("verificationHash", Base64.getEncoder().encodeToString(verificationHash));

        writeProperties(properties);
        setActiveEncryptionKey(encryptionKey);
        wipe(encryptionSalt, verificationSalt, verificationHash, encryptionKey);
    }

    public synchronized boolean unlock(String passphrase) {
        if (!Files.exists(passphraseFile)) {
            throw new IllegalStateException("No application passphrase is configured yet.");
        }

        Properties properties = readProperties();
        byte[] verificationSalt = decode(properties.getProperty("verificationSalt"));
        byte[] verificationHash = decode(properties.getProperty("verificationHash"));
        byte[] derivedVerificationHash = deriveKey(passphrase, verificationSalt);

        if (!MessageDigest.isEqual(verificationHash, derivedVerificationHash)) {
            wipe(verificationSalt, verificationHash, derivedVerificationHash);
            return false;
        }

        byte[] encryptionSalt = decode(properties.getProperty("encryptionSalt"));
        byte[] encryptionKey = deriveKey(passphrase, encryptionSalt);
        setActiveEncryptionKey(encryptionKey);
        wipe(verificationSalt, verificationHash, derivedVerificationHash, encryptionSalt, encryptionKey);
        return true;
    }

    public synchronized void lock() {
        if (activeEncryptionKey != null) {
            Arrays.fill(activeEncryptionKey, (byte) 0);
            activeEncryptionKey = null;
        }
    }

    public synchronized byte[] requireActiveEncryptionKey() {
        if (activeEncryptionKey == null) {
            throw new IllegalStateException("The application is locked.");
        }
        return Arrays.copyOf(activeEncryptionKey, activeEncryptionKey.length);
    }

    public int getMinimumPassphraseLength() {
        return minimumPassphraseLength;
    }

    public void deletePassphraseFileForTests() throws IOException {
        lock();
        Files.deleteIfExists(passphraseFile);
    }

    private void validatePassphrase(String passphrase) {
        if (passphrase == null || passphrase.isBlank()) {
            throw new IllegalArgumentException("The passphrase must not be empty.");
        }
        if (passphrase.length() < minimumPassphraseLength) {
            throw new IllegalArgumentException("The passphrase is too short.");
        }
    }

    private byte[] deriveKey(String passphrase, byte[] salt) {
        try {
            PBEKeySpec spec = new PBEKeySpec(passphrase.toCharArray(), salt, keyDerivationIterations, DERIVED_KEY_LENGTH_BITS);
            try {
                SecretKeyFactory keyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
                return keyFactory.generateSecret(spec).getEncoded();
            } finally {
                spec.clearPassword();
            }
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Failed to derive an encryption key from the passphrase.", exception);
        }
    }

    private Properties readProperties() {
        Properties properties = new Properties();
        try (InputStream inputStream = Files.newInputStream(passphraseFile, StandardOpenOption.READ)) {
            properties.load(inputStream);
            return properties;
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read the application passphrase metadata.", exception);
        }
    }

    private void writeProperties(Properties properties) {
        try {
            Path parentDirectory = passphraseFile.getParent();
            if (parentDirectory != null) {
                Files.createDirectories(parentDirectory);
            }
            try (OutputStream outputStream = Files.newOutputStream(
                    passphraseFile,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE)) {
                properties.store(outputStream, "Application passphrase metadata");
            }
            applyOwnerOnlyPermissions();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to persist the application passphrase metadata.", exception);
        }
    }

    private void applyOwnerOnlyPermissions() {
        try {
            Set<PosixFilePermission> permissions = PosixFilePermissions.fromString("rw-------");
            Files.setPosixFilePermissions(passphraseFile, permissions);
        } catch (UnsupportedOperationException ignored) {
            log.warn("POSIX file permissions are not supported for {}. Verify that the passphrase file is protected by the operating system.", passphraseFile);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to protect the application passphrase metadata file.", exception);
        }
    }

    private void setActiveEncryptionKey(byte[] encryptionKey) {
        byte[] previousKey = activeEncryptionKey;
        activeEncryptionKey = Arrays.copyOf(encryptionKey, encryptionKey.length);
        if (previousKey != null) {
            Arrays.fill(previousKey, (byte) 0);
        }
    }

    private byte[] randomBytes(int size) {
        byte[] bytes = new byte[size];
        secureRandom.nextBytes(bytes);
        return bytes;
    }

    private byte[] decode(String value) {
        return Base64.getDecoder().decode(value);
    }

    private void wipe(byte[]... byteArrays) {
        for (byte[] byteArray : byteArrays) {
            if (byteArray != null) {
                Arrays.fill(byteArray, (byte) 0);
            }
        }
    }
}
