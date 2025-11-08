import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;

import javax.swing.*;

/**
 * Utility to install FlatLaf look-and-feel if the library is present on the classpath.
 */
public class LookAndFeelUtil {
    public static final String THEME_LIGHT = "light";
    public static final String THEME_DARK = "dark";

    public static void installPreferredLaf() {
        applyTheme(PrefsManager.getTheme(THEME_LIGHT));
    }

    public static void applyTheme(String theme) {
        try {
            if (THEME_DARK.equalsIgnoreCase(theme)) {
                FlatDarkLaf.setup();
            } else {
                FlatLightLaf.setup();
            }
            UIManager.put("laf.localchat.theme", theme);
            PrefsManager.setTheme(theme);
        } catch (Throwable t) {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
            }
        }
    }
}
