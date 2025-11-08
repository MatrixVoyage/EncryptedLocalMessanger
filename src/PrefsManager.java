import java.util.prefs.Preferences;

/**
 * Simple preferences helper to store user settings locally.
 */
public class PrefsManager {
    private static final String NODE = "localchat";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_HOST = "defaultHost";
    private static final String KEY_PORT = "defaultPort";
    private static final String KEY_PASSWORD = "defaultPassword"; // stored in plain prefs; for demo only
    private static final String KEY_THEME = "uiTheme";

    private static Preferences prefs() { return Preferences.userRoot().node(NODE); }

    public static String getUsername(String defVal) { return prefs().get(KEY_USERNAME, defVal); }
    public static void setUsername(String v) { prefs().put(KEY_USERNAME, nvl(v)); }

    public static String getHost(String defVal) { return prefs().get(KEY_HOST, defVal); }
    public static void setHost(String v) { prefs().put(KEY_HOST, nvl(v)); }

    public static int getPort(int defVal) { return prefs().getInt(KEY_PORT, defVal); }
    public static void setPort(int v) { prefs().putInt(KEY_PORT, v); }

    public static String getPassword(String defVal) { return prefs().get(KEY_PASSWORD, defVal); }
    public static void setPassword(String v) { prefs().put(KEY_PASSWORD, nvl(v)); }

    public static String getTheme(String defVal) { return prefs().get(KEY_THEME, defVal); }
    public static void setTheme(String v) { prefs().put(KEY_THEME, nvl(v)); }

    private static String nvl(String s) { return s == null ? "" : s; }
}
