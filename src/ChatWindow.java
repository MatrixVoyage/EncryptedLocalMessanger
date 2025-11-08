import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ChatWindow extends JFrame {
    private JTextArea chatArea;
    private JTextField inputField;
    public JButton sendButton;
    public JButton fileButton;
    private JButton saveButton;
    private JButton prefsButton;
    private JLabel statusLabel;
    private JLabel peersLabel;
    private JLabel encryptionLabel;
    private JLabel connStateLabel;
    private final Color colorConnected = new Color(76, 175, 80);
    private final Color colorConnecting = new Color(255, 193, 7);
    private final Color colorDisconnected = new Color(244, 67, 54);

    public JButton findUsersButton;
    public JList<String> discoveredList;
    public JButton inviteButton;
    public JTextField manualIpField;
    public JButton connectIpButton;
    private DefaultListModel<String> discoveredModel;

    private JPanel transfersPanel;
    private JScrollPane transfersScroll;
    private final Map<String, TransferPanel> transferPanels = new ConcurrentHashMap<>();
    private FileTransferHandler fileTransferHandler;

    private TrayIcon trayIcon;
    private boolean trayInstalled;
    private int connectedPeerCount;
    private int discoveredPeerCount = -1;

    public ChatWindow(String title) {
        super(title);
        initUI();
        initTray();
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                removeTray();
            }
        });
        setVisible(true);
    }

    private void initUI() {
        setSize(900, 600);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        chatArea = new JTextArea();
        chatArea.setEditable(false);
        chatArea.setLineWrap(true);
        chatArea.setWrapStyleWord(true);
        chatArea.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        JScrollPane chatScroll = new JScrollPane(chatArea);
        chatScroll.setBorder(new EmptyBorder(6, 6, 6, 6));

        transfersPanel = new JPanel();
        transfersPanel.setLayout(new BoxLayout(transfersPanel, BoxLayout.Y_AXIS));
        transfersScroll = new JScrollPane(transfersPanel);
        transfersScroll.setBorder(new EmptyBorder(0, 6, 0, 6));
        transfersScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        transfersScroll.setVisible(false);

        inputField = new JTextField();
        sendButton = new JButton("Send");
        fileButton = new JButton("Send File");
        saveButton = new JButton("Save Chat");
        prefsButton = new JButton("Preferences");

        JPanel bottom = new JPanel(new BorderLayout(6, 6));
        bottom.add(inputField, BorderLayout.CENTER);

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        connStateLabel = new JLabel("Disconnected");
        connStateLabel.setIcon(new DotIcon(colorDisconnected, 8));
        connStateLabel.setIconTextGap(6);
        connStateLabel.setBorder(new EmptyBorder(0, 0, 0, 6));
        btns.add(connStateLabel);
        btns.add(saveButton);
        btns.add(fileButton);
        btns.add(prefsButton);
        btns.add(sendButton);
        bottom.add(btns, BorderLayout.EAST);

        statusLabel = new JLabel("Not connected");
        peersLabel = new JLabel("Peers: 0");
        encryptionLabel = new JLabel("Encryption: AES-GCM");
        JPanel statusBar = new JPanel(new BorderLayout(8, 0));
        statusBar.setBorder(new EmptyBorder(4, 8, 4, 8));
        statusBar.add(statusLabel, BorderLayout.WEST);
        statusBar.add(peersLabel, BorderLayout.CENTER);
        statusBar.add(encryptionLabel, BorderLayout.EAST);

        JPanel rightPanel = buildRightPanel();

        JPanel center = new JPanel(new BorderLayout(6, 6));
        center.add(transfersScroll, BorderLayout.NORTH);
        center.add(chatScroll, BorderLayout.CENTER);
        center.add(bottom, BorderLayout.SOUTH);

        getContentPane().setLayout(new BorderLayout(6, 6));
    getContentPane().add(center, BorderLayout.CENTER);
    getContentPane().add(rightPanel, BorderLayout.EAST);
    getContentPane().add(statusBar, BorderLayout.SOUTH);

        getContentPane().setBackground(new Color(245, 245, 245));
        bottom.setBackground(new Color(245, 245, 245));
        statusBar.setBackground(new Color(245, 245, 245));
        sendButton.setBackground(new Color(33, 150, 243));
        sendButton.setForeground(Color.WHITE);

        saveButton.addActionListener(e -> handleSaveChat());
        prefsButton.addActionListener(e -> {
            PreferencesDialog dlg = new PreferencesDialog(this);
            dlg.setVisible(true);
            SwingUtilities.updateComponentTreeUI(this);
        });
        fileButton.addActionListener(e -> handleFileSendAction());
        inputField.addActionListener(e -> sendButton.doClick());
    }

    private JPanel buildRightPanel() {
        JPanel rightPanel = new JPanel(new BorderLayout(6, 6));
        rightPanel.setBorder(new EmptyBorder(6, 6, 6, 6));

        JLabel rightTitle = new JLabel("Connections");
        rightTitle.setFont(new Font("Segoe UI", Font.BOLD, 14));
        findUsersButton = new JButton("Find Users on LAN");
        JPanel rightTop = new JPanel(new BorderLayout(6, 6));
        rightTop.add(rightTitle, BorderLayout.WEST);
        rightTop.add(findUsersButton, BorderLayout.EAST);

        discoveredModel = new DefaultListModel<>();
        discoveredList = new JList<>(discoveredModel);
        discoveredList.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        discoveredList.setCellRenderer(new UserCellRenderer());
        JScrollPane discoveredScroll = new JScrollPane(discoveredList);

        inviteButton = new JButton("Invite to Chat");
        manualIpField = new JTextField();
        manualIpField.setToolTipText("Manual IP (e.g., 192.168.1.42)");
        connectIpButton = new JButton("Connect by IP...");

        JPanel rightBottom = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 0, 4, 0);
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        rightBottom.add(inviteButton, gbc);
        gbc.gridy++;
        rightBottom.add(manualIpField, gbc);
        gbc.gridy++;
    rightBottom.add(connectIpButton, gbc);

        JPanel north = new JPanel(new BorderLayout(6, 6));
        north.add(rightTop, BorderLayout.NORTH);

        rightPanel.add(north, BorderLayout.NORTH);
        rightPanel.add(discoveredScroll, BorderLayout.CENTER);
        rightPanel.add(rightBottom, BorderLayout.SOUTH);
        return rightPanel;
    }

    public void appendMessage(String who, String message) {
        SwingUtilities.invokeLater(() -> {
            SimpleDateFormat fmt = new SimpleDateFormat("HH:mm:ss");
            String time = fmt.format(new Date());
            chatArea.append("[" + time + "] " + who + ": " + message + "\n");
            chatArea.setCaretPosition(chatArea.getDocument().getLength());
        });
        maybeNotifyTray(who, message);
    }

    public String grabInputAndClear() {
        String txt = inputField.getText();
        inputField.setText("");
        return txt;
    }

    public void setStatus(String status) {
        SwingUtilities.invokeLater(() -> statusLabel.setText(status));
    }

    public void setConnected(boolean connected) {
        SwingUtilities.invokeLater(() -> {
            if (connected) {
                connStateLabel.setText("Connected");
                connStateLabel.setIcon(new DotIcon(colorConnected, 8));
            } else {
                connStateLabel.setText("Disconnected");
                connStateLabel.setIcon(new DotIcon(colorDisconnected, 8));
            }
        });
    }

    public void setConnecting() {
        SwingUtilities.invokeLater(() -> {
            connStateLabel.setText("Connecting…");
            connStateLabel.setIcon(new DotIcon(colorConnecting, 8));
        });
    }

    public void setDiscoveredPeers(Map<String, DiscoveryService.DiscoveredPeer> peers) {
        SwingUtilities.invokeLater(() -> {
            discoveredModel.clear();
            if (peers == null || peers.isEmpty()) {
                return;
            }
            peers.values().stream()
                    .sorted((a, b) -> a.username.compareToIgnoreCase(b.username))
                    .forEach(p -> discoveredModel.addElement(p.username + " (" + p.ip + ")"));
        });
    }

    public String getSelectedPeerIp() {
        String sel = discoveredList.getSelectedValue();
        if (sel == null) {
            return null;
        }
        int lb = sel.lastIndexOf('(');
        int rb = sel.lastIndexOf(')');
        if (lb >= 0 && rb > lb) {
            return sel.substring(lb + 1, rb);
        }
        return null;
    }

    public void setFileTransferHandler(FileTransferHandler handler) {
        fileTransferHandler = handler;
    }

    public TransferProgressHandle createIncomingTransfer(String id, String name, long totalBytes) {
        TransferPanel panel = addTransferPanel(id, name + " (incoming)", totalBytes, false);
        return new TransferProgressHandle() {
            @Override
            public void update(long transferred) {
                panel.updateProgress(transferred, totalBytes);
            }

            @Override
            public void complete(String detail) {
                panel.markCompleted(detail == null ? "Received" : detail);
                scheduleRemoval(id, panel);
            }

            @Override
            public void fail(String reason) {
                panel.markFailed(reason == null ? "Failed" : reason);
                scheduleRemoval(id, panel);
            }
        };
    }

    public void updatePeerCount(int count) {
        connectedPeerCount = Math.max(0, count);
        SwingUtilities.invokeLater(this::refreshPeerSummary);
    }

    public void updateDiscoveryCount(int count) {
        discoveredPeerCount = count < 0 ? -1 : Math.max(0, count);
        SwingUtilities.invokeLater(this::refreshPeerSummary);
    }

    public void setEncryptionMode(String mode) {
        SwingUtilities.invokeLater(() -> encryptionLabel.setText("Encryption: " + mode));
    }

    private void refreshPeerSummary() {
        String text = "Peers: " + connectedPeerCount;
        if (discoveredPeerCount >= 0) {
            text += " (LAN " + discoveredPeerCount + ")";
        }
        peersLabel.setText(text);
    }

    private void handleFileSendAction() {
        if (fileTransferHandler == null) {
            JOptionPane.showMessageDialog(this, "File sending is not available right now.");
            return;
        }
        JFileChooser chooser = new JFileChooser();
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File file = chooser.getSelectedFile();
        if (file == null) {
            return;
        }
        startOutgoingTransfer(file);
    }

    private void startOutgoingTransfer(File file) {
        long totalBytes = file.length();
        String transferId = UUID.randomUUID().toString();
        TransferPanel panel = addTransferPanel(transferId, file.getName(), totalBytes, true);

        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                final SwingWorker<Void, Void> self = this;
                fileTransferHandler.transfer(file, new TransferMonitor() {
                    @Override
                    public void onProgress(long transferred, long total) {
                        panel.updateProgress(transferred, total);
                    }

                    @Override
                    public boolean isCancelled() {
                        return self.isCancelled();
                    }
                });
                return null;
            }

            @Override
            protected void done() {
                try {
                    if (isCancelled()) {
                        panel.markFailed("Cancelled");
                    } else {
                        get();
                        panel.markCompleted("Sent");
                    }
                } catch (Exception ex) {
                    panel.markFailed(ex.getMessage());
                }
                scheduleRemoval(transferId, panel);
            }
        };
        panel.bind(worker);
        worker.execute();
    }

    private TransferPanel addTransferPanel(String id, String name, long totalBytes, boolean cancellable) {
        TransferPanel panel = new TransferPanel(name, totalBytes, cancellable);
        transferPanels.put(id, panel);
        SwingUtilities.invokeLater(() -> {
            transfersPanel.add(panel);
            transfersPanel.revalidate();
            transfersPanel.repaint();
            updateTransferVisibility();
        });
        return panel;
    }

    private void scheduleRemoval(String id, TransferPanel panel) {
        Timer timer = new Timer("transfer-remove", true);
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                SwingUtilities.invokeLater(() -> {
                    transferPanels.remove(id);
                    transfersPanel.remove(panel);
                    transfersPanel.revalidate();
                    transfersPanel.repaint();
                    updateTransferVisibility();
                });
            }
        }, 1500);
    }

    private void updateTransferVisibility() {
        transfersScroll.setVisible(!transferPanels.isEmpty());
        transfersScroll.revalidate();
    }

    private void handleSaveChat() {
        JFileChooser chooser = new JFileChooser();
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File target = chooser.getSelectedFile();
        SwingWorker<Void, Void> writer = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                try (BufferedWriter bw = new BufferedWriter(new FileWriter(target))) {
                    bw.write(chatArea.getText());
                }
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    JOptionPane.showMessageDialog(ChatWindow.this, "Saved to " + target.getAbsolutePath());
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(ChatWindow.this, "Error saving: " + ex.getMessage());
                }
            }
        };
        writer.execute();
    }

    private void initTray() {
        if (!SystemTray.isSupported()) {
            return;
        }
        try {
            Image image = createTrayImage();
            trayIcon = new TrayIcon(image, "LocalChat");
            trayIcon.setImageAutoSize(true);
            trayIcon.addActionListener(e -> EventQueue.invokeLater(() -> {
                setExtendedState(JFrame.NORMAL);
                toFront();
                requestFocus();
            }));
            SystemTray.getSystemTray().add(trayIcon);
            trayInstalled = true;
        } catch (Exception ignored) {
            trayInstalled = false;
        }
    }

    private Image createTrayImage() {
        Icon icon = UIManager.getIcon("OptionPane.informationIcon");
        if (icon instanceof ImageIcon) {
            return ((ImageIcon) icon).getImage();
        }
        int w = icon != null ? icon.getIconWidth() : 16;
        int h = icon != null ? icon.getIconHeight() : 16;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        if (icon != null) {
            icon.paintIcon(null, g, 0, 0);
        } else {
            g.setColor(new Color(33, 150, 243));
            g.fillOval(0, 0, w, h);
        }
        g.dispose();
        return img;
    }

    private void maybeNotifyTray(String who, String message) {
        if (!trayInstalled || trayIcon == null) {
            return;
        }
        if (isActive() || "You".equalsIgnoreCase(who) || who.startsWith("SYSTEM")) {
            return;
        }
        trayIcon.displayMessage("Message from " + who, message, TrayIcon.MessageType.INFO);
    }

    private void removeTray() {
        if (trayInstalled && trayIcon != null) {
            SystemTray.getSystemTray().remove(trayIcon);
            trayInstalled = false;
        }
    }

    @Override
    public void dispose() {
        removeTray();
        super.dispose();
    }

    public interface FileTransferHandler {
        void transfer(File file, TransferMonitor monitor) throws Exception;
    }

    public interface TransferMonitor {
        void onProgress(long transferred, long total);
        boolean isCancelled();
    }

    public interface TransferProgressHandle {
        void update(long transferred);
        void complete(String detail);
        void fail(String reason);
    }

    private static final class TransferPanel extends JPanel {
        private final JProgressBar progressBar = new JProgressBar(0, 100);
        private final JLabel statusLabel = new JLabel();
        private final JButton cancelButton = new JButton("Cancel");
        private final boolean cancellable;
        private SwingWorker<?, ?> worker;
        private final long totalBytes;

        TransferPanel(String name, long totalBytes, boolean cancellable) {
            super(new BorderLayout(6, 0));
            this.cancellable = cancellable;
            this.totalBytes = totalBytes;
            JLabel nameLabel = new JLabel(name);
            nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD));
            progressBar.setStringPainted(true);
            updateProgress(0L, totalBytes);
            statusLabel.setText(cancellable ? "Preparing…" : "Receiving…");
            cancelButton.setVisible(cancellable);
            add(nameLabel, BorderLayout.NORTH);
            add(progressBar, BorderLayout.CENTER);
            JPanel south = new JPanel(new BorderLayout(6, 0));
            south.add(statusLabel, BorderLayout.CENTER);
            south.add(cancelButton, BorderLayout.EAST);
            add(south, BorderLayout.SOUTH);
            setBorder(new EmptyBorder(6, 6, 6, 6));
        }

        void bind(SwingWorker<?, ?> worker) {
            this.worker = worker;
            if (cancellable) {
                ActionListener listener = e -> {
                    if (this.worker != null) {
                        this.worker.cancel(true);
                    }
                    cancelButton.setEnabled(false);
                    statusLabel.setText("Cancelling…");
                };
                cancelButton.addActionListener(listener);
            }
        }

        void updateProgress(long transferred, long total) {
            SwingUtilities.invokeLater(() -> {
                long boundedTotal = total <= 0 ? 1 : total;
                int percent = (int) Math.min(100, Math.round((double) transferred * 100.0 / boundedTotal));
                progressBar.setValue(percent);
                String totalText = total > 0 ? humanReadable(total) : "unknown";
                progressBar.setString(percent + "% - " + humanReadable(transferred) + " / " + totalText);
                statusLabel.setText(cancellable ? "Sending…" : "Receiving…");
            });
        }

        void markCompleted(String message) {
            SwingUtilities.invokeLater(() -> {
                progressBar.setValue(100);
                progressBar.setString("100% - " + humanReadable(totalBytes));
                statusLabel.setText(message);
                cancelButton.setVisible(false);
            });
        }

        void markFailed(String reason) {
            SwingUtilities.invokeLater(() -> {
                statusLabel.setText(reason == null ? "Failed" : reason);
                progressBar.setValue(0);
                progressBar.setString("Failed");
                cancelButton.setVisible(false);
            });
        }

        private String humanReadable(long bytes) {
            if (bytes < 1024) {
                return bytes + " B";
            }
            double kb = bytes / 1024.0;
            if (kb < 1024) {
                return String.format("%.1f KB", kb);
            }
            double mb = kb / 1024.0;
            if (mb < 1024) {
                return String.format("%.1f MB", mb);
            }
            double gb = mb / 1024.0;
            return String.format("%.1f GB", gb);
        }
    }
}

class UserCellRenderer extends DefaultListCellRenderer {
    private final Color online = new Color(76, 175, 80);
    private final int dot = 8;

    @Override
    public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
        JLabel lbl = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        lbl.setIcon(new DotIcon(online, dot));
        lbl.setIconTextGap(8);
        return lbl;
    }
}

class DotIcon implements Icon {
    private final Color color;
    private final int size;

    DotIcon(Color color, int size) {
        this.color = color;
        this.size = size;
    }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(color);
        g2.fillOval(x, y + (getIconHeight() - size) / 2, size, size);
        g2.dispose();
    }

    @Override
    public int getIconWidth() {
        return size;
    }

    @Override
    public int getIconHeight() {
        return size;
    }
}
