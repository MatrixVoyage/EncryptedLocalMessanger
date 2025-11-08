import org.slf4j.Logger;

import javax.crypto.AEADBadTagException;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class ClientHandler implements Runnable {
    private static final Logger LOG = AppLogger.get(ClientHandler.class);

    private final Socket socket;
    private final BufferedReader in;
    private final BufferedWriter out;
    private final char[] password;
    private final EncryptedMultiServer server;
    private final String clientId;
    private final ScheduledExecutorService heartbeatScheduler;
    private final long heartbeatIntervalMs;
    private final long heartbeatTimeoutMs;

    private volatile long lastActivity;
    private volatile boolean running = true;
    private ScheduledFuture<?> heartbeatTask;

    public ClientHandler(Socket socket,
                         char[] password,
                         EncryptedMultiServer server,
                         ScheduledExecutorService heartbeatScheduler,
                         long heartbeatIntervalMs,
                         long heartbeatTimeoutMs) throws IOException {
        this.socket = socket;
        this.password = password;
        this.server = server;
        this.heartbeatScheduler = heartbeatScheduler;
        this.heartbeatIntervalMs = heartbeatIntervalMs;
        this.heartbeatTimeoutMs = heartbeatTimeoutMs;
        this.in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        this.out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
        this.clientId = socket.getInetAddress().getHostAddress() + ":" + socket.getPort();
        this.lastActivity = System.currentTimeMillis();
    }

    @Override
    public void run() {
        scheduleHeartbeat();
        try {
            String line;
            while (running && (line = in.readLine()) != null) {
                lastActivity = System.currentTimeMillis();
                try {
                    String plaintext = EncryptionUtil.decryptMessage(line, password);
                    server.onClientMessage(this, plaintext);
                } catch (AEADBadTagException tampered) {
                    LOG.warn("Discarded tampered payload from {}", clientId, tampered);
                } catch (Exception ex) {
                    LOG.error("Decrypt failed from {}", clientId, ex);
                }
            }
        } catch (IOException e) {
            LOG.info("Connection closed: {}", clientId);
        } finally {
            closeQuietly();
            server.removeClient(this);
        }
    }

    public String getClientId() {
        return clientId;
    }

    public void acknowledgePong() {
        lastActivity = System.currentTimeMillis();
    }

    public void sendAsync(String plaintext) {
        sendAsync(plaintext, password);
    }

    public void sendAsync(String plaintext, char[] encryptionPassword) {
        if (!running) return;
        try {
            String encrypted = EncryptionUtil.encryptMessage(plaintext, encryptionPassword);
            sendEncrypted(encrypted);
        } catch (Exception e) {
            LOG.warn("Failed to encrypt outbound message to {}", clientId, e);
        }
    }

    public void closeQuietly() {
        if (!running) return;
        running = false;
        if (heartbeatTask != null) {
            heartbeatTask.cancel(true);
        }
        try { socket.close(); } catch (IOException ignored) {}
        try { in.close(); } catch (IOException ignored) {}
        try { out.close(); } catch (IOException ignored) {}
    }

    private void sendEncrypted(String encryptedLine) {
        if (!running) return;
        synchronized (out) {
            try {
                out.write(encryptedLine);
                out.newLine();
                out.flush();
            } catch (IOException e) {
                LOG.warn("Failed to send payload to {}", clientId, e);
                closeQuietly();
            }
        }
    }

    private void scheduleHeartbeat() {
        if (heartbeatScheduler == null) {
            return;
        }
        heartbeatTask = heartbeatScheduler.scheduleAtFixedRate(() -> {
            if (!running) {
                return;
            }
            long idle = System.currentTimeMillis() - lastActivity;
            if (idle >= heartbeatTimeoutMs) {
                LOG.warn("Heartbeat timeout for {}; disconnecting", clientId);
                closeQuietly();
                return;
            }
            if (idle >= heartbeatIntervalMs) {
                sendAsync(FileTransferProtocol.PING);
            }
        }, heartbeatIntervalMs, heartbeatIntervalMs, TimeUnit.MILLISECONDS);
    }
}
