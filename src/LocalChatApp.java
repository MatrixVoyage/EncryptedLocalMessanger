import javax.swing.*;

public class LocalChatApp {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            LookAndFeelUtil.installPreferredLaf();

            Object[] options = {"Client", "Server", "Both"};
            int choice = JOptionPane.showOptionDialog(null,
                    "Choose how to run LocalChat:",
                    "LocalChat Launcher",
                    JOptionPane.DEFAULT_OPTION,
                    JOptionPane.QUESTION_MESSAGE,
                    null,
                    options,
                    options[0]);

            if (choice == 1) { // Server
                runServer();
            } else if (choice == 0) { // Client
                runClient();
            } else if (choice == 2) { // Both
                // Start server then start a client to localhost with same port/password
                String[] sp = promptServerPortAndPassword();
                if (sp == null) return;
                int port = Integer.parseInt(sp[0]);
                String password = sp[1];
                new EncryptedMultiServer(port, password);

                // Small delay to let server start listening
                try { Thread.sleep(300); } catch (InterruptedException ignored) {}

                new EncryptedClient("localhost", port, password);
            }
        });
    }

    private static void runServer() {
        String[] sp = promptServerPortAndPassword();
        if (sp == null) return;
        int port = Integer.parseInt(sp[0]);
        String password = sp[1];
        new EncryptedMultiServer(port, password);
    }

    private static void runClient() {
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
    }

    private static String[] promptServerPortAndPassword() {
        String portStr = JOptionPane.showInputDialog(null, "Enter port to listen on:", String.valueOf(PrefsManager.getPort(5000)));
        if (portStr == null) return null;
        int port;
        try { port = Integer.parseInt(portStr.trim()); } catch (Exception e) { JOptionPane.showMessageDialog(null, "Invalid port"); return null; }
        String password = JOptionPane.showInputDialog(null, "Enter shared password (clients must match):", PrefsManager.getPassword("changeit"));
        if (password == null) return null;
        PrefsManager.setPort(port);
        PrefsManager.setPassword(password);
        return new String[]{String.valueOf(port), password};
    }
}
