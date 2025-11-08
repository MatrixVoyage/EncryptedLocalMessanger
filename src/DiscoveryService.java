import org.slf4j.Logger;

import java.io.Closeable;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/**
 * DiscoveryService
 *
 * Optional LAN/Wi-Fi discovery and invitation service for Encrypted Local Messenger.
 * - Periodically broadcasts presence via UDP so peers can discover this client.
 * - Listens for presence and invitation packets from peers.
 * - Maintains a thread-safe list of discovered clients and notifies listeners on updates.
 * - Can send invitation packets for peers to join a given TCP chat server.
 *
 * Notes:
 * - Uses UDP port 8888 for broadcast/listen.
 * - Presence (HELLO) packets are plaintext to allow open discovery on LAN.
 * - Invitation (INVITE) packets can be optionally AES-encrypted using the existing chat password.
 */
public class DiscoveryService implements Closeable {
    private static final Logger LOG = AppLogger.get(DiscoveryService.class);
    // UDP communication
    public static final int DISCOVERY_PORT = 8888;
    private static final int MAX_UDP_SIZE = 2048;
    private static final long HELLO_INTERVAL_MS = 5000;   // how often we announce presence
    private static final long CLEANUP_INTERVAL_MS = 10000; // stale entries cleanup
    private static final long STALE_AFTER_MS = 15000;       // consider offline if no hello within

    private final String username;
    private final boolean encryptInvites;
    private final String inviteEncryptionPassword; // used only for INVITE encryption

    private final ConcurrentHashMap<String, DiscoveredPeer> peers = new ConcurrentHashMap<>(); // key: ip
    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();

    private volatile boolean running = false;
    private DatagramSocket socket; // shared for recv; sending uses new DatagramSocket per send for simplicity
    private Thread recvThread;
    private ScheduledExecutorService scheduler;

    /**
     * Create a discovery service.
     * @param username Display name to share on LAN discovery.
     * @param encryptInvites If true, invitation payloads are AES-encrypted using inviteEncryptionPassword.
     * @param inviteEncryptionPassword Password to use for encrypting INVITE packets (typically the same chat password).
     */
    public DiscoveryService(String username, boolean encryptInvites, String inviteEncryptionPassword) {
        this.username = username != null ? username : safeDefaultUsername();
        this.encryptInvites = encryptInvites && inviteEncryptionPassword != null && !inviteEncryptionPassword.isEmpty();
        this.inviteEncryptionPassword = inviteEncryptionPassword;
    }

    /** Start the background receiver and periodic tasks. Safe to call once. */
    public synchronized void start() {
        if (running) return;
        running = true;
        try {
            // Use reuse address to allow multiple instances (server + client) on same machine when possible
            socket = new DatagramSocket(null);
            socket.setReuseAddress(true);
            socket.setBroadcast(true);
            socket.bind(new InetSocketAddress(DISCOVERY_PORT));
        } catch (SocketException e) {
            throw new RuntimeException("Failed to bind UDP port " + DISCOVERY_PORT + ": " + e.getMessage(), e);
        }

        // Receiver thread
        recvThread = new Thread(this::recvLoop, "Discovery-Recv");
        recvThread.setDaemon(true);
        recvThread.start();

        // Schedulers for HELLO broadcast and cleanup
        scheduler = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(this::sendHelloBroadcastSafe, 200, HELLO_INTERVAL_MS, TimeUnit.MILLISECONDS);
        scheduler.scheduleAtFixedRate(this::cleanupStalePeers, CLEANUP_INTERVAL_MS, CLEANUP_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    /** Stops all background activity and closes sockets. */
    public synchronized void stop() {
        running = false;
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
        if (socket != null) {
            try { socket.close(); } catch (Exception ignored) {}
            socket = null;
        }
    }

    @Override
    public void close() { stop(); }

    /** Listener for discovery events. */
    public interface Listener {
        /** Called when the discovered peers set changes. Map key is IP string. */
        void onPeersChanged(Map<String, DiscoveredPeer> snapshot);

        /** Called when an invitation is received. */
        void onInvitation(Invitation invitation);
    }

    /** Holds discovered peer info. */
    public static class DiscoveredPeer {
        public final String ip;
        public final String username;
        public volatile long lastSeen;
        public volatile String status; // optional field, currently "online"

        public DiscoveredPeer(String ip, String username, long lastSeen, String status) {
            this.ip = ip;
            this.username = username;
            this.lastSeen = lastSeen;
            this.status = status;
        }
    }

    /** Represents an invitation payload. */
    public static class Invitation {
        public final String fromUsername;
        public final String fromIp;
        public final String serverHost;
        public final int serverPort;
        public final String serverPassword;

        public Invitation(String fromUsername, String fromIp, String serverHost, int serverPort, String serverPassword) {
            this.fromUsername = fromUsername;
            this.fromIp = fromIp;
            this.serverHost = serverHost;
            this.serverPort = serverPort;
            this.serverPassword = serverPassword;
        }
    }

    /** Register a listener for updates. */
    public void addListener(Listener l) { if (l != null) listeners.addIfAbsent(l); }
    /** Unregister a listener. */
    public void removeListener(Listener l) { listeners.remove(l); }

    /** Get a snapshot copy of peers. */
    public Map<String, DiscoveredPeer> getPeersSnapshot() { return new HashMap<>(peers); }

    /** Trigger an immediate HELLO broadcast. */
    public void refreshNow() { sendHelloBroadcastSafe(); }

    /** Send an invitation to a specific IP address. */
    public void sendInviteToIp(String ip, String serverHost, int serverPort, String serverPassword) {
        try {
            InetAddress addr = InetAddress.getByName(ip);
            sendInvite(addr, serverHost, serverPort, serverPassword);
        } catch (Exception e) {
            LOG.warn("Failed to resolve invitation target {}", ip, e);
        }
    }

    /** Send an invitation to a discovered peer. */
    public void sendInvite(InetAddress address, String serverHost, int serverPort, String serverPassword) {
        String payload = encodeInvite(username, serverHost, serverPort, serverPassword);
        byte[] data = payload.getBytes(StandardCharsets.UTF_8);
        try (DatagramSocket sendSock = new DatagramSocket()) {
            DatagramPacket p = new DatagramPacket(data, data.length, address, DISCOVERY_PORT);
            sendSock.send(p);
        } catch (Exception e) {
            LOG.warn("Failed to send invitation to {}", address, e);
        }
    }

    // ============ Internal methods ============

    private void recvLoop() {
        byte[] buf = new byte[MAX_UDP_SIZE];
        while (running && socket != null && !socket.isClosed()) {
            try {
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                socket.receive(packet);
                String fromIp = packet.getAddress().getHostAddress();
                // Ignore our own packets if they loop back
                if (isLocalAddress(packet.getAddress())) continue;

                String msg = new String(packet.getData(), packet.getOffset(), packet.getLength(), StandardCharsets.UTF_8).trim();

                // Try decrypt if it's an encrypted invitation (prefixed) and we have password
                if (msg.startsWith("ENC:")) {
                    if (!encryptInvites) {
                        // We are not configured to decrypt invites; skip
                        continue;
                    }
                    String enc = msg.substring(4);
                    try {
                        msg = EncryptionUtil.decryptMessage(enc, inviteEncryptionPassword);
                    } catch (Exception ex) {
                        // Not decryptable by us
                        continue;
                    }
                }

                handleIncomingMessage(fromIp, msg);
            } catch (SocketException se) {
                // socket closed during stop
                break;
            } catch (Exception e) {
                LOG.warn("Discovery receive error", e);
            }
        }
    }

    private void handleIncomingMessage(String fromIp, String msg) {
        // Expected formats:
        // HELLO:  ELM|HELLO|<username>|<ts>
        // INVITE: ELM|INVITE|<fromUsername>|<serverHost>|<serverPort>|<passwordB64>|<ts>
        try {
            if (!msg.startsWith("ELM|")) return; // ignore foreign packets
            String[] parts = msg.split("\\|", -1);
            if (parts.length < 3) return;
            String type = parts[1];
            if ("HELLO".equals(type)) {
                String peerUser = parts.length > 2 ? parts[2] : "?";
                long now = System.currentTimeMillis();
                peers.compute(fromIp, (key, v) -> {
                    if (v == null) return new DiscoveredPeer(key, peerUser, now, "online");
                    v.lastSeen = now;
                    // update username if changed
                    if (!Objects.equals(v.username, peerUser)) {
                        return new DiscoveredPeer(key, peerUser, now, v.status);
                    }
                    return v;
                });
                notifyPeersChanged();
            } else if ("INVITE".equals(type)) {
                if (parts.length < 7) return;
                String fromUser = parts[2];
                String serverHost = parts[3];
                int serverPort = Integer.parseInt(parts[4]);
                String passwordB64 = parts[5];
                String serverPassword = new String(Base64.getDecoder().decode(passwordB64), StandardCharsets.UTF_8);
                Invitation inv = new Invitation(fromUser, fromIp, serverHost, serverPort, serverPassword);
                for (Listener l : listeners) {
                    try { l.onInvitation(inv); } catch (Exception ignored) {}
                }
            }
        } catch (Exception e) {
            LOG.warn("Discovery message handling error", e);
        }
    }

    private void notifyPeersChanged() {
        Map<String, DiscoveredPeer> snap = getPeersSnapshot();
        for (Listener l : listeners) {
            try { l.onPeersChanged(snap); } catch (Exception ignored) {}
        }
    }

    private void cleanupStalePeers() {
        long cutoff = System.currentTimeMillis() - STALE_AFTER_MS;
        boolean changed = false;
        Iterator<Map.Entry<String, DiscoveredPeer>> it = peers.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, DiscoveredPeer> e = it.next();
            if (e.getValue().lastSeen < cutoff) {
                it.remove();
                changed = true;
            }
        }
        if (changed) notifyPeersChanged();
    }

    private void sendHelloBroadcastSafe() { try { sendHelloBroadcast(); } catch (Exception ignored) {} }

    /** Broadcast a plaintext HELLO packet to 255.255.255.255 and all interface broadcasts. */
    private void sendHelloBroadcast() {
        String payload = encodeHello(username);
        byte[] data = payload.getBytes(StandardCharsets.UTF_8);
        // Try universal broadcast first
        try (DatagramSocket sendSock = new DatagramSocket()) {
            sendSock.setBroadcast(true);
            DatagramPacket p = new DatagramPacket(data, data.length, InetAddress.getByName("255.255.255.255"), DISCOVERY_PORT);
            sendSock.send(p);
        } catch (Exception ignored) {}

        // Also broadcast to each interface's broadcast address
        try (DatagramSocket sendSock = new DatagramSocket()) {
            sendSock.setBroadcast(true);
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                if (!ni.isUp() || ni.isLoopback()) continue;
                for (InterfaceAddress ia : ni.getInterfaceAddresses()) {
                    InetAddress bcast = ia.getBroadcast();
                    if (bcast == null) continue;
                    try {
                        DatagramPacket p = new DatagramPacket(data, data.length, bcast, DISCOVERY_PORT);
                        sendSock.send(p);
                    } catch (Exception ignored) {}
                }
            }
        } catch (Exception ignored) {}
    }

    private String encodeHello(String username) {
        long ts = System.currentTimeMillis();
        return "ELM|HELLO|" + safe(username) + "|" + ts;
    }

    private String encodeInvite(String fromUsername, String serverHost, int serverPort, String serverPassword) {
        long ts = System.currentTimeMillis();
        String body = "ELM|INVITE|" + safe(fromUsername) + "|" + safe(serverHost) + "|" + serverPort + "|" + Base64.getEncoder().encodeToString(serverPassword.getBytes(StandardCharsets.UTF_8)) + "|" + ts;
        if (encryptInvites) {
            try {
                String enc = EncryptionUtil.encryptMessage(body, inviteEncryptionPassword);
                return "ENC:" + enc;
            } catch (Exception e) {
                // Fallback to plaintext if encryption fails
                return body;
            }
        }
        return body;
    }

    private static String safe(String s) { return s == null ? "" : s.replace('|', ' '); }

    private static String safeDefaultUsername() {
        String u = System.getProperty("user.name", "User");
        return u != null && !u.isEmpty() ? u : "User";
    }

    private static boolean isLocalAddress(InetAddress addr) {
        try {
            if (addr.isLoopbackAddress()) return true;
            Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces();
            while (ifaces.hasMoreElements()) {
                NetworkInterface ni = ifaces.nextElement();
                Enumeration<InetAddress> addrs = ni.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress a = addrs.nextElement();
                    if (a.equals(addr)) return true;
                }
            }
        } catch (SocketException ignored) {}
        return false;
    }
}
