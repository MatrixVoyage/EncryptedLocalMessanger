import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.*;

class FileTransferProtocolTest {

    @Test
    void chunkRoundTripPreservesData() {
        byte[] data = new byte[FileTransferProtocol.CHUNK_SIZE];
        new SecureRandom().nextBytes(data);

        String hash = HexFormat.of().formatHex(data).substring(0, 64);
        String frame = FileTransferProtocol.buildChunk(hash, 7, data, data.length);
        FileTransferProtocol.Chunk parsed = FileTransferProtocol.parseChunk(frame);

        assertEquals(hash.toLowerCase(), parsed.hash());
        assertEquals(7, parsed.sequence());
        assertArrayEquals(data, parsed.data());
    }

    @Test
    void computeSha256MatchesMessageDigest() throws IOException, NoSuchAlgorithmException {
        Path temp = Files.createTempFile("localchat-test", ".bin");
        try {
            byte[] content = new byte[8192];
            new SecureRandom().nextBytes(content);
            Files.write(temp, content);

            String utilHash = FileTransferProtocol.computeSha256(temp);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String expected = HexFormat.of().formatHex(digest.digest(content));
            assertEquals(expected, utilHash);
        } finally {
            Files.deleteIfExists(temp);
        }
    }
}
