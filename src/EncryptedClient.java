import org.slf4j.Logger;

import javax.crypto.AEADBadTagException;
import javax.swing.*;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Enumeration;
import java.util.concurrent.ConcurrentHashMap;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class EncryptedClient {
    private static final Logger LOG = AppLogger.get(EncryptedClient.class);

    private Socket socket;
    private ChatWindow ui;
    private final String password;
    private final char[] passwordChars;
    private BufferedReader br;
    private BufferedWriter bw;
    private DiscoveryService discovery;
    private String username;
    private String serverHost;
    private int serverPort;
    private final ConcurrentHashMap<String, IncomingFileTransfer> incomingTransfers = new ConcurrentHashMap<>();

    public EncryptedClient(String host, int port, String password) {
        this.password = password != null ? password : "";
        this.passwordChars = this.password.toCharArray();
        this.serverHost = host;
        this.serverPort = port;
        // Apply look and feel
        LookAndFeelUtil.installPreferredLaf();

        // Ask for a local display name for LAN discovery (prefill from prefs)
        this.username = PrefsManager.getUsername(System.getProperty("user.name", "User"));
        try {
            String u = JOptionPane.showInputDialog(null, "Enter your display name:", username);
            if (u != null && !u.trim().isEmpty()) username = u.trim();
            PrefsManager.setUsername(username);
        } catch (Exception ignored) {}

    ui = new ChatWindow("Encrypted Local Messenger - CLIENT (" + host + ":" + port + ")");
    ui.setStatus("Connecting to " + host + ":" + port + " ...");
    ui.setConnecting();
    ui.setEncryptionMode("AES-GCM");

        ui.sendButton.addActionListener(e -> {
            if (e != null) e.getActionCommand();
            String txt = ui.grabInputAndClear();
            if (txt != null && !txt.trim().isEmpty()) {
                sendText(txt);
                ui.appendMessage("You", txt);
            }
        });

        ui.setFileTransferHandler(this::sendFile);

        // LAN discovery button wires
        ui.findUsersButton.addActionListener(e -> {
            if (e != null) e.getActionCommand();
            if (discovery != null) discovery.refreshNow();
        });

        ui.inviteButton.addActionListener(e -> {
            if (e != null) e.getActionCommand();
            String ip = ui.getSelectedPeerIp();
            if (ip == null) {
                JOptionPane.showMessageDialog(ui, "Select a user from the list first.");
                return;
            }
            if (discovery != null) {
                String hostForInvite = resolveHostForInvite(this.serverHost);
                discovery.sendInviteToIp(ip, hostForInvite, this.serverPort, this.password);
                LOG.info("Invitation dispatched to {}", ip);
                ui.appendMessage("SYSTEM", "Invitation sent to " + ip);
            }
        });

        ui.connectIpButton.addActionListener(e -> {
            if (e != null) e.getActionCommand();
            String ip = ui.manualIpField.getText();
            if (ip == null || ip.trim().isEmpty()) {
                JOptionPane.showMessageDialog(ui, "Enter a target IP.");
                return;
            }
            // Attempt new TCP connection to current server port on the manual IP
            // Note: This launches a new client window to that IP/port with same password
            String target = ip.trim();
            SwingUtilities.invokeLater(() -> new EncryptedClient(target, this.serverPort, this.password));
        });

        startClientThread(host, port);

        // Start optional discovery service
        startDiscoveryService();
    }

    private void startClientThread(String host, int port) {
        new Thread(() -> {
            try {
                socket = new Socket(host, port);
                br = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                bw = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));

                ui.setStatus("Connected to " + socket.getRemoteSocketAddress());
                ui.setConnected(true);
                ui.updatePeerCount(1);
                ui.appendMessage("SYSTEM", "Connected to " + socket.getRemoteSocketAddress());

                // Receiving loop
                String line;
                while ((line = br.readLine()) != null) {
                    try {
                        String dec = EncryptionUtil.decryptMessage(line, passwordChars);
                        if (FileTransferProtocol.PING.equals(dec)) {
                            sendPlainPayload(FileTransferProtocol.PONG);
                            continue;
                        }
                        if (FileTransferProtocol.isHeader(dec)) {
                            handleIncomingFileHeader(dec);
                            continue;
                        }
                        if (FileTransferProtocol.isChunk(dec)) {
                            handleIncomingFileChunk(dec);
                            continue;
                        }
                        if (FileTransferProtocol.isEof(dec)) {
                            handleIncomingFileEof(dec);
                            continue;
                        }
                        if (FileTransferProtocol.PONG.equals(dec)) {
                            continue;
                        }
                        ui.appendMessage("Remote", dec);
                    } catch (AEADBadTagException tampered) {
                        LOG.warn("Discarded tampered message from {}", socket.getRemoteSocketAddress(), tampered);
                        ui.appendMessage("REMOTE (encrypted)", line);
                        ui.appendMessage("ERROR", "Message integrity check failed; content discarded.");
                    } catch (Exception ex) {
                        LOG.error("Failed to decrypt message from {}", socket.getRemoteSocketAddress(), ex);
                        ui.appendMessage("REMOTE (encrypted)", line);
                        ui.appendMessage("ERROR", "Decrypt failed: " + ex.getMessage());
                    }
                }
                ui.appendMessage("SYSTEM", "Connection closed by server.");
                ui.setStatus("Disconnected");
                ui.setConnected(false);
                ui.updatePeerCount(0);
            } catch (Exception e) {
                LOG.error("Client connection error", e);
                ui.appendMessage("ERROR", e.getMessage());
                ui.setStatus("Connection failed: " + e.getMessage());
                ui.setConnected(false);
                ui.updatePeerCount(0);
            } finally {
                abortAllTransfers();
                ui.updateDiscoveryCount(-1);
                try { if (socket != null) socket.close(); } catch (Exception ignored) {}
            }
        }, "Client-Thread").start();
    }

    // Send plaintext payload (it will be encrypted)
    private void sendPlainPayload(String payload) {
        try {
            if (bw == null || socket == null || !socket.isConnected()) {
                ui.appendMessage("ERROR", "Not connected yet — message not sent.");
                return;
            }
            String enc = EncryptionUtil.encryptMessage(payload, passwordChars);
            bw.write(enc);
            bw.newLine();
            bw.flush();
        } catch (Exception ex) {
            LOG.error("Failed to send payload to {}", (socket != null ? socket.getRemoteSocketAddress() : "unknown"), ex);
            ui.appendMessage("ERROR", "Send failed: " + ex.getMessage());
        }
    }

    private void sendText(String txt) {
        sendPlainPayload(txt);
    }

    private void sendFile(File file, ChatWindow.TransferMonitor monitor) throws Exception {
        if (file == null || !file.exists() || !file.isFile()) {
            ui.appendMessage("ERROR", "Invalid file selected.");
            return;
        }
        try {
            Path path = file.toPath();
            long size = Files.size(path);
            String hash = FileTransferProtocol.computeSha256(path);
            sendPlainPayload(FileTransferProtocol.buildHeader(file.getName(), size, hash));
            byte[] buffer = new byte[FileTransferProtocol.CHUNK_SIZE];
            int seq = 0;
            try (BufferedInputStream in = new BufferedInputStream(Files.newInputStream(path))) {
                int read;
                long transferred = 0;
                while ((read = in.read(buffer)) != -1) {
                    if (monitor != null && monitor.isCancelled()) {
                        throw new IOException("Transfer cancelled by user");
                    }
                    String chunk = FileTransferProtocol.buildChunk(hash, seq++, buffer, read);
                    sendPlainPayload(chunk);
                    transferred += read;
                    if (monitor != null) {
                        monitor.onProgress(transferred, size);
                    }
                }
            }
            sendPlainPayload(FileTransferProtocol.buildEof(hash));
            ui.appendMessage("You", "Sent file: " + file.getName());
        } catch (IOException ex) {
            LOG.error("File send failed for {}", file.getAbsolutePath(), ex);
            ui.appendMessage("ERROR", "File send failed: " + ex.getMessage());
            throw ex;
        }
    }

    private void handleIncomingFileHeader(String frame) {
        try {
            FileTransferProtocol.Header header = FileTransferProtocol.parseHeader(frame);
            ChatWindow.TransferProgressHandle handle = ui.createIncomingTransfer(header.hash(), header.filename(), header.size());
            IncomingFileTransfer transfer = new IncomingFileTransfer(header, handle);
            IncomingFileTransfer previous = incomingTransfers.put(header.hash(), transfer);
            if (previous != null) {
                previous.abortWithReason("Transfer reset");
            }
            ui.appendMessage("Remote", "Incoming file: " + header.filename() + " (" + header.size() + " bytes)");
        } catch (Exception ex) {
            LOG.error("Failed to initialize incoming file transfer", ex);
            ui.appendMessage("ERROR", "Unable to prepare file transfer: " + ex.getMessage());
        }
    }

    private void handleIncomingFileChunk(String frame) {
        final FileTransferProtocol.Chunk chunk;
        try {
            chunk = FileTransferProtocol.parseChunk(frame);
        } catch (Exception ex) {
            LOG.error("Malformed file chunk frame", ex);
            ui.appendMessage("ERROR", "Malformed file chunk received.");
            return;
        }
        IncomingFileTransfer transfer = incomingTransfers.get(chunk.hash());
        if (transfer == null) {
            LOG.warn("Chunk received for unknown transfer {}", chunk.hash());
            return;
        }
        try {
            transfer.appendChunk(chunk);
        } catch (IOException ex) {
            LOG.error("File chunk write failed for {}", chunk.hash(), ex);
            incomingTransfers.remove(chunk.hash());
            transfer.abortWithReason(ex.getMessage());
            ui.appendMessage("ERROR", "File transfer aborted: " + ex.getMessage());
        }
    }

    private void handleIncomingFileEof(String frame) {
        final String hash;
        try {
            hash = FileTransferProtocol.parseEofHash(frame);
        } catch (Exception ex) {
            LOG.error("Malformed EOF frame", ex);
            ui.appendMessage("ERROR", "Malformed file footer received.");
            return;
        }
        IncomingFileTransfer transfer = incomingTransfers.remove(hash);
        if (transfer == null) {
            LOG.warn("EOF received for unknown transfer {}", hash);
            return;
        }
        try {
            Path target = resolveDownloadTarget(transfer.getFilename());
            Path saved = transfer.complete(target);
            ui.appendMessage("Remote", "Received file: " + saved.getFileName() + " (saved to " + saved.toAbsolutePath() + ")");
            transfer.onCompleted();
        } catch (Exception ex) {
            transfer.abortWithReason(ex.getMessage());
            LOG.error("File transfer verification failed for {}", hash, ex);
            ui.appendMessage("ERROR", "File transfer failed: " + ex.getMessage());
        }
    }

    private void abortAllTransfers() {
        incomingTransfers.values().forEach(t -> t.abortWithReason("Connection closed"));
        incomingTransfers.clear();
    }

    private Path resolveDownloadTarget(String filename) throws IOException {
        Path dir = Paths.get(System.getProperty("user.home", "."), "Downloads");
        if (!Files.isDirectory(dir)) {
            dir = Paths.get(".").toAbsolutePath().normalize();
        }
        Files.createDirectories(dir);
        String sanitized = FileTransferProtocol.sanitizeFilename(filename);
        Path candidate = dir.resolve(sanitized);
        if (!Files.exists(candidate)) {
            return candidate;
        }
        String base = sanitized;
        String ext = "";
        int dot = sanitized.lastIndexOf('.');
        if (dot > 0) {
            base = sanitized.substring(0, dot);
            ext = sanitized.substring(dot);
        }
        int counter = 1;
        do {
            candidate = dir.resolve(base + "(" + counter++ + ")" + ext);
        } while (Files.exists(candidate));
        return candidate;
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private static final class IncomingFileTransfer {
        private final FileTransferProtocol.Header header;
        private final Path tempFile;
        private final OutputStream out;
        private final MessageDigest digest;
        private long bytesReceived = 0;
        private int nextSequence = 0;
        private final ChatWindow.TransferProgressHandle progressHandle;

        IncomingFileTransfer(FileTransferProtocol.Header header, ChatWindow.TransferProgressHandle progressHandle) throws IOException, NoSuchAlgorithmException {
            this.header = header;
            this.tempFile = Files.createTempFile("localchat-", ".part");
            this.out = Files.newOutputStream(this.tempFile);
            this.digest = MessageDigest.getInstance("SHA-256");
            this.progressHandle = progressHandle;
        }

        synchronized void appendChunk(FileTransferProtocol.Chunk chunk) throws IOException {
            if (chunk.sequence() != nextSequence) {
                throw new IOException("Unexpected chunk sequence: expected " + nextSequence + " but got " + chunk.sequence());
            }
            out.write(chunk.data());
            digest.update(chunk.data());
            bytesReceived += chunk.data().length;
            nextSequence++;
            if (progressHandle != null) {
                progressHandle.update(bytesReceived);
            }
        }

        synchronized Path complete(Path targetPath) throws IOException {
            try {
                out.flush();
            } finally {
                out.close();
            }
            if (bytesReceived != header.size()) {
                throw new IOException("Size mismatch: expected " + header.size() + " bytes but received " + bytesReceived);
            }
            String computed = bytesToHex(digest.digest());
            if (!computed.equals(header.hash())) {
                throw new IOException("Hash mismatch: expected " + header.hash() + " but computed " + computed);
            }
            return Files.move(tempFile, targetPath, StandardCopyOption.REPLACE_EXISTING);
        }

        synchronized void abortWithReason(String reason) {
            try { out.close(); } catch (IOException ignored) {}
            try { Files.deleteIfExists(tempFile); } catch (IOException ignored) {}
            if (progressHandle != null) {
                progressHandle.fail(reason == null ? "Aborted" : reason);
            }
        }

        String getFilename() {
            return header.filename();
        }

        void onCompleted() {
            if (progressHandle != null) {
                progressHandle.complete(null);
            }
        }

    }

    // Initialize and start discovery service with UI listeners
    private void startDiscoveryService() {
        try {
            discovery = new DiscoveryService(username, true, password);
            discovery.addListener(new DiscoveryService.Listener() {
                @Override
                public void onPeersChanged(Map<String, DiscoveryService.DiscoveredPeer> snapshot) {
                    ui.setDiscoveredPeers(snapshot);
                    int count = snapshot != null ? snapshot.size() : 0;
                    ui.updateDiscoveryCount(count);
                }

                @Override
                public void onInvitation(DiscoveryService.Invitation invitation) {
                    SwingUtilities.invokeLater(() -> {
                        int res = JOptionPane.showConfirmDialog(ui,
                                "'" + invitation.fromUsername + "' (" + invitation.fromIp + ") invites you to join:\n" +
                                        invitation.serverHost + ":" + invitation.serverPort + "\nAccept?",
                                "Chat Invitation",
                                JOptionPane.YES_NO_OPTION);
                        if (res == JOptionPane.YES_OPTION) {
                            // Use inviter's IP if invite host is loopback or empty
                            String targetHost = chooseInvitationHost(invitation.serverHost, invitation.fromIp);
                            new EncryptedClient(targetHost, invitation.serverPort, invitation.serverPassword);
                        }
                    });
                }
            });
            discovery.start();
            ui.updateDiscoveryCount(0);
            ui.appendMessage("SYSTEM", "LAN discovery started on UDP " + DiscoveryService.DISCOVERY_PORT);
        } catch (Exception ex) {
            LOG.error("Discovery service start failure", ex);
            ui.appendMessage("ERROR", "Discovery start failed: " + ex.getMessage());
        }
    }

    // Replace localhost/127.* with our LAN IPv4 when sending invites, so peers can reach us
    private String resolveHostForInvite(String host) {
        if (host == null) return host;
        String h = host.trim();
        if (isLoopbackHost(h)) {
            String lan = getLocalLanAddress();
            return (lan != null ? lan : h);
        }
        return h;
    }

    // Prefer inviter's fromIp if the advertised serverHost is loopback or blank
    private String chooseInvitationHost(String serverHost, String fromIp) {
        if (serverHost == null || serverHost.isEmpty() || isLoopbackHost(serverHost)) {
            return (fromIp != null && !fromIp.isEmpty()) ? fromIp : (serverHost == null ? "" : serverHost);
        }
        return serverHost;
    }

    private boolean isLoopbackHost(String h) {
        String x = h.toLowerCase();
        return "localhost".equals(x) || x.startsWith("127.");
    }

    private String getLocalLanAddress() {
        try {
            Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces();
            while (ifaces.hasMoreElements()) {
                NetworkInterface ni = ifaces.nextElement();
                if (!ni.isUp() || ni.isLoopback() || ni.isVirtual()) continue;
                Enumeration<InetAddress> addrs = ni.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress addr = addrs.nextElement();
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress() && addr.isSiteLocalAddress()) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            LookAndFeelUtil.installPreferredLaf();
            String host = JOptionPane.showInputDialog(null, "Enter server host or IP:", PrefsManager.getHost("localhost"));
            if (host == null) return;
            String portStr = JOptionPane.showInputDialog(null, "Enter server port:", String.valueOf(PrefsManager.getPort(5000)));
            if (portStr == null) return;
            int port = Integer.parseInt(portStr.trim());
            String password = JOptionPane.showInputDialog(null, "Enter shared password (must match server):", PrefsManager.getPassword("changeit"));
            if (password == null) return;
            PrefsManager.setHost(host);
            PrefsManager.setPort(port);
            PrefsManager.setPassword(password);
            new EncryptedClient(host, port, password);
        });
    }
}
