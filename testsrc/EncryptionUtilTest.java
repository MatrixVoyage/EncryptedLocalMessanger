import org.junit.jupiter.api.Test;

import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EncryptionUtilTest {

    @Test
    void encryptDecryptRoundTripReturnsOriginalMessage() throws GeneralSecurityException {
        String message = "LocalChat rocks!";
        char[] password = "s3cr3t".toCharArray();

        List<String> ciphertexts = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            String encrypted = EncryptionUtil.encryptMessage(message, password);
            ciphertexts.add(encrypted);
            String decrypted = EncryptionUtil.decryptMessage(encrypted, password);
            assertEquals(message, decrypted, "Decrypted text must match the original");
        }

        long distinctCount = ciphertexts.stream().distinct().count();
        assertTrue(distinctCount > 1, "Encryptions should include randomness");
    }

    @Test
    void decryptWithWrongPasswordFails() throws GeneralSecurityException {
        String encrypted = EncryptionUtil.encryptMessage("payload", "one".toCharArray());
        assertThrows(GeneralSecurityException.class,
                () -> EncryptionUtil.decryptMessage(encrypted, "two".toCharArray()));
    }
}
