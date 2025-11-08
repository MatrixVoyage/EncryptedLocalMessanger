import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Centralized factory for LocalChat loggers.
 */
public final class AppLogger {
    private AppLogger() {
    }

    public static Logger get(Class<?> type) {
        return LoggerFactory.getLogger(type);
    }

    public static Logger get(String name) {
        return LoggerFactory.getLogger(name);
    }
}
