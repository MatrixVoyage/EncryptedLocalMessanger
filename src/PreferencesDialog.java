import javax.swing.*;
import java.awt.*;

/**
 * Modal dialog to edit LocalChat preferences.
 */
public class PreferencesDialog extends JDialog {
    private final JTextField usernameField = new JTextField(20);
    private final JTextField hostField = new JTextField(20);
    private final JSpinner portSpinner = new JSpinner(new SpinnerNumberModel(5000, 1, 65535, 1));
    private final JPasswordField passwordField = new JPasswordField(20);
    private final JComboBox<ThemeOption> themeCombo = new JComboBox<>(ThemeOption.values());

    public PreferencesDialog(Window owner) {
        super(owner, "Preferences", ModalityType.APPLICATION_MODAL);
        initUI();
        loadPrefs();
    }

    private void initUI() {
        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridx = 0; gbc.gridy = 0;
        form.add(new JLabel("Username:"), gbc);
        gbc.gridx = 1; form.add(usernameField, gbc);

        gbc.gridx = 0; gbc.gridy++; form.add(new JLabel("Default Host:"), gbc);
        gbc.gridx = 1; form.add(hostField, gbc);

        gbc.gridx = 0; gbc.gridy++; form.add(new JLabel("Default Port:"), gbc);
        gbc.gridx = 1; form.add(portSpinner, gbc);

    gbc.gridx = 0; gbc.gridy++; form.add(new JLabel("Default Password:"), gbc);
    gbc.gridx = 1; form.add(passwordField, gbc);

    gbc.gridx = 0; gbc.gridy++; form.add(new JLabel("Theme:"), gbc);
    gbc.gridx = 1; form.add(themeCombo, gbc);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton cancel = new JButton("Cancel");
        JButton save = new JButton("Save");
        buttons.add(cancel);
        buttons.add(save);

    cancel.addActionListener(e -> { if (e != null) e.getActionCommand(); dispose(); });
    save.addActionListener(e -> { if (e != null) e.getActionCommand(); savePrefs(); dispose(); });

        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(form, BorderLayout.CENTER);
        getContentPane().add(buttons, BorderLayout.SOUTH);

        pack();
        setLocationRelativeTo(getOwner());
    }

    private void loadPrefs() {
        usernameField.setText(PrefsManager.getUsername(System.getProperty("user.name", "User")));
        hostField.setText(PrefsManager.getHost("localhost"));
        portSpinner.setValue(PrefsManager.getPort(5000));
        passwordField.setText(PrefsManager.getPassword("changeit"));
        String theme = PrefsManager.getTheme(LookAndFeelUtil.THEME_LIGHT);
        themeCombo.setSelectedItem(ThemeOption.fromKey(theme));
    }

    private void savePrefs() {
        PrefsManager.setUsername(usernameField.getText());
        PrefsManager.setHost(hostField.getText());
        PrefsManager.setPort((Integer) portSpinner.getValue());
        PrefsManager.setPassword(new String(passwordField.getPassword()));
        ThemeOption opt = (ThemeOption) themeCombo.getSelectedItem();
        if (opt != null) {
            LookAndFeelUtil.applyTheme(opt.themeKey);
        }
    }

    private enum ThemeOption {
        LIGHT("Light", LookAndFeelUtil.THEME_LIGHT),
        DARK("Dark", LookAndFeelUtil.THEME_DARK);

        private final String label;
        private final String themeKey;

        ThemeOption(String label, String themeKey) {
            this.label = label;
            this.themeKey = themeKey;
        }

        @Override
        public String toString() {
            return label;
        }

        static ThemeOption fromKey(String key) {
            for (ThemeOption option : values()) {
                if (option.themeKey.equalsIgnoreCase(key)) {
                    return option;
                }
            }
            return LIGHT;
        }
    }
}
