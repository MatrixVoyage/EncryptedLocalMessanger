import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;
import java.util.Objects;

public final class EncryptionUtil {
    private static final String CIPHER_ALGO = "AES/GCM/NoPadding";
    private static final String KDF_ALGO = "PBKDF2WithHmacSHA256";
    private static final int KEY_SIZE_BITS = 256;
    private static final int ITERATIONS = 150_000;
    private static final int IV_SIZE_BYTES = 12;
    private static final int TAG_LENGTH_BITS = 128;
    private static final int SALT_SIZE_BYTES = 16;

    private static final SecureRandom SECURE_RANDOM = createSecureRandom();
    private static final Base64.Encoder B64_ENCODER = Base64.getEncoder();
    private static final Base64.Decoder B64_DECODER = Base64.getDecoder();

    private EncryptionUtil() {
    }

    private static SecureRandom createSecureRandom() {
        try {
            return SecureRandom.getInstanceStrong();
        } catch (NoSuchAlgorithmException ignored) {
            return new SecureRandom();
        }
    }

    /**
     * Derive an AES key using PBKDF2-HMAC-SHA256 with the configured iteration count.
     */
    public static SecretKey deriveKey(char[] password, byte[] salt) throws GeneralSecurityException {
        Objects.requireNonNull(password, "password");
        Objects.requireNonNull(salt, "salt");

        PBEKeySpec spec = new PBEKeySpec(password, salt, ITERATIONS, KEY_SIZE_BITS);
        try {
            SecretKeyFactory skf = SecretKeyFactory.getInstance(KDF_ALGO);
            SecretKey tmp = skf.generateSecret(spec);
            return new SecretKeySpec(tmp.getEncoded(), "AES");
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new GeneralSecurityException("Failed to derive key", e);
        } finally {
            spec.clearPassword();
        }
    }

    /**
     * Encrypt plaintext with a password-derived AES-GCM key. Returns base64(salt):base64(iv):base64(ciphertext+tag).
     */
    public static String encryptMessage(String plaintext, char[] password) throws GeneralSecurityException {
        Objects.requireNonNull(plaintext, "plaintext");
        Objects.requireNonNull(password, "password");

        byte[] salt = new byte[SALT_SIZE_BYTES];
        SECURE_RANDOM.nextBytes(salt);

        SecretKey key = deriveKey(password, salt);

        byte[] iv = new byte[IV_SIZE_BYTES];
        SECURE_RANDOM.nextBytes(iv);

        Cipher cipher = Cipher.getInstance(CIPHER_ALGO);
        GCMParameterSpec gcmSpec = new GCMParameterSpec(TAG_LENGTH_BITS, iv);
        cipher.init(Cipher.ENCRYPT_MODE, key, gcmSpec);

        byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

        return B64_ENCODER.encodeToString(salt) + ':' +
                B64_ENCODER.encodeToString(iv) + ':' +
                B64_ENCODER.encodeToString(ciphertext);
    }

    public static String encryptMessage(String plaintext, String password) throws GeneralSecurityException {
        return encryptMessage(plaintext, safePasswordChars(password));
    }

    /**
     * Decrypt a message produced by {@link #encryptMessage(String, char[])}.
     */
    public static String decryptMessage(String combined, char[] password) throws GeneralSecurityException {
        Objects.requireNonNull(combined, "combined");
        Objects.requireNonNull(password, "password");

        String[] parts = combined.split(":");
        if (parts.length != 3) {
            throw new IllegalArgumentException("Invalid encrypted message format");
        }

        byte[] salt = B64_DECODER.decode(parts[0]);
        byte[] iv = B64_DECODER.decode(parts[1]);
        byte[] cipherText = B64_DECODER.decode(parts[2]);

        if (iv.length != IV_SIZE_BYTES) {
            throw new IllegalArgumentException("Unexpected IV length: " + iv.length);
        }

        SecretKey key = deriveKey(password, salt);
        Cipher cipher = Cipher.getInstance(CIPHER_ALGO);
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));

        byte[] decrypted = cipher.doFinal(cipherText);
        return new String(decrypted, StandardCharsets.UTF_8);
    }

    public static String decryptMessage(String combined, String password) throws GeneralSecurityException {
        return decryptMessage(combined, safePasswordChars(password));
    }

    /**
     * Deprecated alias maintained for backward compatibility.
     */
    public static String encrypt(String plaintext, String password) throws GeneralSecurityException {
        return encryptMessage(plaintext, password);
    }

    /**
     * Deprecated alias maintained for backward compatibility.
     */
    public static String decrypt(String combined, String password) throws GeneralSecurityException {
        return decryptMessage(combined, password);
    }

    private static char[] safePasswordChars(String password) {
        return password != null ? password.toCharArray() : new char[0];
    }
}
