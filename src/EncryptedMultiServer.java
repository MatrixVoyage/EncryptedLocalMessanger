import org.slf4j.Logger;

import javax.swing.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class EncryptedMultiServer {
    private static final Logger LOG = AppLogger.get(EncryptedMultiServer.class);
    private static final long HEARTBEAT_INTERVAL_MS = TimeUnit.SECONDS.toMillis(30);
    private static final long HEARTBEAT_TIMEOUT_MS = TimeUnit.SECONDS.toMillis(90);
    private static final int CLIENT_QUEUE_CAPACITY = 64;

    private final CopyOnWriteArrayList<ClientHandler> clients = new CopyOnWriteArrayList<>();
    private final ChatWindow ui;
    private volatile boolean running = false;
    private final int port;
    private final String password;
    private final char[] passwordChars;
    private DiscoveryService discovery;
    private ServerSocket serverSocket;
    private Thread acceptThread;
    private final ExecutorService clientExecutor;
    private final ScheduledExecutorService heartbeatScheduler;

    public EncryptedMultiServer(int port, String password) {
        LookAndFeelUtil.installPreferredLaf();
        this.port = port;
        this.password = password != null ? password : "";
        this.passwordChars = this.password.toCharArray();

        this.clientExecutor = createClientExecutor();
        this.heartbeatScheduler = Executors.newScheduledThreadPool(1, new NamedThreadFactory("Server-Heartbeat"));

        Runtime.getRuntime().addShutdownHook(new Thread(this::stop, "LocalChatServer-Shutdown"));

        ui = new ChatWindow("Encrypted Local Messenger - SERVER (port " + port + ")");
        ui.setStatus("Starting server...");
        ui.setConnected(false);
        ui.setEncryptionMode("AES-GCM");
        ui.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                stop();
            }
        });

        ui.sendButton.addActionListener(e -> {
            if (e != null) e.getActionCommand();
            String txt = ui.grabInputAndClear();
            if (txt != null && !txt.trim().isEmpty()) {
                ui.appendMessage("You", txt);
                broadcastFromServer(txt);
            }
        });

        ui.setFileTransferHandler(this::broadcastFileFromServer);

        startAcceptThread();
        startDiscovery();
    }

    private void startDiscovery() {
        try {
            String uname = PrefsManager.getUsername("Server");
            discovery = new DiscoveryService(uname, true, password);
            discovery.start();
            ui.appendMessage("SYSTEM", "LAN discovery started on UDP " + DiscoveryService.DISCOVERY_PORT);
        } catch (Exception ex) {
            LOG.warn("Discovery start failed", ex);
            ui.appendMessage("ERROR", "Discovery start failed: " + ex.getMessage());
        }
    }

    private void startAcceptThread() {
        acceptThread = new Thread(() -> {
            try {
                serverSocket = new ServerSocket(port);
                running = true;
                ui.setStatus("Listening on port " + port + " ...");
                LOG.info("Server listening on port {}", port);
                while (running) {
                    Socket sock = serverSocket.accept();
                    configureSocket(sock);
                    ClientHandler handler = new ClientHandler(sock, passwordChars.clone(), this, heartbeatScheduler, HEARTBEAT_INTERVAL_MS, HEARTBEAT_TIMEOUT_MS);
                    clients.add(handler);
                    ui.appendMessage("SYSTEM", "Client connected: " + handler.getClientId());
                    ui.setConnected(true);
                    ui.updatePeerCount(clients.size());
                    LOG.info("Client connected: {} (active clients: {})", handler.getClientId(), clients.size());
                    try {
                        clientExecutor.execute(handler);
                    } catch (RejectedExecutionException rex) {
                        LOG.warn("Connection rejected (server busy): {}", handler.getClientId());
                        ui.appendMessage("ERROR", "Cannot accept more clients right now; connection rejected.");
                        handler.closeQuietly();
                        clients.remove(handler);
                    }
                }
            } catch (SocketException se) {
                if (running) {
                    LOG.error("Server socket failure", se);
                    ui.appendMessage("ERROR", "Server socket error: " + se.getMessage());
                }
            } catch (IOException e) {
                LOG.error("Server error", e);
                ui.appendMessage("ERROR", "Server error: " + e.getMessage());
            } finally {
                running = false;
                closeServerSocket();
                ui.setConnected(false);
                ui.updatePeerCount(0);
                ui.setStatus("Server stopped");
            }
        }, "Server-Accept-Thread");
        acceptThread.setDaemon(true);
        acceptThread.start();
    }

    // Called by a ClientHandler when a plaintext message is received
    public void onClientMessage(ClientHandler sender, String plaintext) {
        if (FileTransferProtocol.isFileMessage(plaintext)) {
            handleFileRelay(sender, plaintext);
            return;
        }

        if (FileTransferProtocol.PONG.equals(plaintext)) {
            sender.acknowledgePong();
            return;
        }

        if (FileTransferProtocol.PING.equals(plaintext)) {
            sender.sendAsync(FileTransferProtocol.PONG);
            return;
        }

        ui.appendMessage("Remote(" + sender.getClientId() + ")", plaintext);
        String payload = "[" + sender.getClientId() + "] " + plaintext;
        broadcastToOthers(sender, payload);
    }

    // Broadcast a server-originated message (server operator typed)
    private void broadcastFromServer(String plaintext) {
        String payload = "[SERVER] " + plaintext;
        for (ClientHandler c : clients) {
            c.sendAsync(payload, passwordChars);
        }
    }

    public void removeClient(ClientHandler handler) {
        clients.remove(handler);
        LOG.info("Client disconnected: {} (active clients: {})", handler.getClientId(), clients.size());
        ui.appendMessage("SYSTEM", "Client disconnected: " + handler.getClientId() + " | Active: " + clients.size());
        ui.updatePeerCount(clients.size());
        if (clients.isEmpty()) ui.setConnected(false);
    }

    public void stop() {
        if (!running) {
            closeServerSocket();
        }
        running = false;
        closeServerSocket();
        if (discovery != null) {
            try { discovery.stop(); } catch (Exception ignored) {}
        }
        clientExecutor.shutdownNow();
        heartbeatScheduler.shutdownNow();
        for (ClientHandler client : clients) {
            client.closeQuietly();
        }
        if (acceptThread != null) {
            acceptThread.interrupt();
        }
    }

    private void broadcastToOthers(ClientHandler sender, String plaintext) {
        dispatchToRecipients(clients, sender, plaintext, passwordChars);
    }

    private void handleFileRelay(ClientHandler sender, String frame) {
        if (FileTransferProtocol.isHeader(frame)) {
            try {
                FileTransferProtocol.Header header = FileTransferProtocol.parseHeader(frame);
                ui.appendMessage("SYSTEM", "Incoming file from " + sender.getClientId() + ": " + header.filename() + " (" + header.size() + " bytes)");
            } catch (Exception ex) {
                LOG.warn("Discarding malformed file header from {}", sender.getClientId(), ex);
                return;
            }
        }
        broadcastToOthers(sender, frame);
    }

    private void broadcastFileFromServer(File file, ChatWindow.TransferMonitor monitor) throws Exception {
        if (file == null || !file.exists() || !file.isFile()) {
            ui.appendMessage("ERROR", "Invalid file selected.");
            return;
        }

        Path path = file.toPath();
        try {
            long size = Files.size(path);
            String hash = FileTransferProtocol.computeSha256(path);
            String header = FileTransferProtocol.buildHeader(file.getName(), size, hash);
            ui.appendMessage("You", "Streaming file: " + file.getName() + " (" + size + " bytes)");
            broadcastToAll(header);

            byte[] buffer = new byte[FileTransferProtocol.CHUNK_SIZE];
            int seq = 0;
            try (BufferedInputStream in = new BufferedInputStream(Files.newInputStream(path))) {
                int read;
                long transferred = 0;
                while ((read = in.read(buffer)) != -1) {
                    if (monitor != null && monitor.isCancelled()) {
                        throw new IOException("Transfer cancelled by operator");
                    }
                    String chunk = FileTransferProtocol.buildChunk(hash, seq++, buffer, read);
                    broadcastToAll(chunk);
                    transferred += read;
                    if (monitor != null) {
                        monitor.onProgress(transferred, size);
                    }
                }
            }
            broadcastToAll(FileTransferProtocol.buildEof(hash));
        } catch (IOException ex) {
            LOG.error("File broadcast failed", ex);
            ui.appendMessage("ERROR", "File send failed: " + ex.getMessage());
            throw ex;
        }
    }

    private void broadcastToAll(String plaintext) {
        for (ClientHandler client : clients) {
            client.sendAsync(plaintext, passwordChars);
        }
    }

    // Visible for tests
    static void dispatchToRecipients(Iterable<ClientHandler> recipients,
                                     ClientHandler sender,
                                     String plaintext,
                                     char[] password) {
        for (ClientHandler client : recipients) {
            if (client == sender) {
                continue;
            }
            client.sendAsync(plaintext, password);
        }
    }

    private void configureSocket(Socket sock) throws SocketException {
        sock.setTcpNoDelay(true);
        sock.setKeepAlive(true);
    }

    private ExecutorService createClientExecutor() {
        int cores = Math.max(2, Runtime.getRuntime().availableProcessors());
        ThreadFactory factory = new NamedThreadFactory("Client-Worker");
        return new ThreadPoolExecutor(
                cores,
                cores * 2,
                60L,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(CLIENT_QUEUE_CAPACITY),
                factory,
                new ThreadPoolExecutor.AbortPolicy());
    }

    private void closeServerSocket() {
        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException ignored) {
            }
            serverSocket = null;
        }
    }

    private static final class NamedThreadFactory implements ThreadFactory {
        private final String prefix;
        private int counter = 0;

        private NamedThreadFactory(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public synchronized Thread newThread(Runnable r) {
            Thread t = new Thread(r, prefix + "-" + (++counter));
            t.setDaemon(true);
            return t;
        }
    }

    // Main launcher for the server
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            String portStr = JOptionPane.showInputDialog(null, "Enter port to listen on:", "5000");
            if (portStr == null) return;
            int port = Integer.parseInt(portStr.trim());

            String password = JOptionPane.showInputDialog(null, "Enter shared password (clients must match):", "changeit");
            if (password == null) return;

            new EncryptedMultiServer(port, password);
        });
    }
}
