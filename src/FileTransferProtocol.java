import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Locale;
import java.util.Objects;

/**
 * Simple helpers for streaming file transfers over the line-oriented chat protocol.
 */
public final class FileTransferProtocol {
    public static final int CHUNK_SIZE = 64 * 1024; // 64 KiB chunks
    public static final String HEADER_PREFIX = "[FILE-HEADER]|";
    public static final String CHUNK_PREFIX = "[FILE-CHUNK]|";
    public static final String EOF_PREFIX = "[FILE-EOF]|";
    public static final String PING = "[PING]";
    public static final String PONG = "[PONG]";

    private FileTransferProtocol() {
    }

    public static String sanitizeFilename(String name) {
        Objects.requireNonNull(name, "name");
        return name.replace('|', '_');
    }

    public static String buildHeader(String filename, long size, String sha256Hex) {
        return HEADER_PREFIX + sanitizeFilename(filename) + "|" + size + "|" + sha256Hex;
    }

    public static String buildChunk(String sha256Hex, int sequence, byte[] buffer, int length) {
        Base64.Encoder encoder = Base64.getEncoder();
        String payload = encoder.encodeToString(length == buffer.length ? buffer : java.util.Arrays.copyOf(buffer, length));
        return CHUNK_PREFIX + sha256Hex + "|" + sequence + "|" + payload;
    }

    public static String buildEof(String sha256Hex) {
        return EOF_PREFIX + sha256Hex;
    }

    public static boolean isFileMessage(String plaintext) {
        return plaintext != null && (plaintext.startsWith(HEADER_PREFIX)
                || plaintext.startsWith(CHUNK_PREFIX)
                || plaintext.startsWith(EOF_PREFIX));
    }

    public static boolean isHeader(String plaintext) {
        return plaintext != null && plaintext.startsWith(HEADER_PREFIX);
    }

    public static boolean isChunk(String plaintext) {
        return plaintext != null && plaintext.startsWith(CHUNK_PREFIX);
    }

    public static boolean isEof(String plaintext) {
        return plaintext != null && plaintext.startsWith(EOF_PREFIX);
    }

    public static Header parseHeader(String header) throws IllegalArgumentException {
        if (!isHeader(header)) {
            throw new IllegalArgumentException("Not a header frame: " + header);
        }
        String[] parts = header.substring(HEADER_PREFIX.length()).split("\\|", 3);
        if (parts.length != 3) {
            throw new IllegalArgumentException("Malformed header frame");
        }
        String filename = parts[0];
        long size = Long.parseLong(parts[1]);
        String hash = parts[2].toLowerCase(Locale.ROOT);
        if (hash.length() != 64) {
            throw new IllegalArgumentException("Unexpected hash length");
        }
        return new Header(filename, size, hash);
    }

    public static Chunk parseChunk(String chunk) {
        if (!isChunk(chunk)) {
            throw new IllegalArgumentException("Not a chunk frame: " + chunk);
        }
        String[] parts = chunk.substring(CHUNK_PREFIX.length()).split("\\|", 3);
        if (parts.length != 3) {
            throw new IllegalArgumentException("Malformed chunk frame");
        }
        String hash = parts[0].toLowerCase(Locale.ROOT);
        int sequence = Integer.parseInt(parts[1]);
        byte[] data = Base64.getDecoder().decode(parts[2]);
        return new Chunk(hash, sequence, data);
    }

    public static String parseEofHash(String eof) {
        if (!isEof(eof)) {
            throw new IllegalArgumentException("Not an EOF frame: " + eof);
        }
        return eof.substring(EOF_PREFIX.length()).toLowerCase(Locale.ROOT);
    }

    public static String computeSha256(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream in = Files.newInputStream(path);
                 DigestInputStream dis = new DigestInputStream(in, digest)) {
                byte[] buffer = new byte[CHUNK_SIZE];
                while (dis.read(buffer) != -1) {
                    // reading via DigestInputStream updates the digest automatically
                }
            }
            return bytesToHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format(Locale.ROOT, "%02x", b));
        }
        return sb.toString();
    }

    public record Header(String filename, long size, String hash) { }

    public record Chunk(String hash, int sequence, byte[] data) { }
}
