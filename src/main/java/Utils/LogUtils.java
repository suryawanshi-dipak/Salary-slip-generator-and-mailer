package Utils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Utility class to provide simple, one-line logging functionality across the codebase.
 */
public class LogUtils {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Logs an INFO level message.
     * @param message The message to log.
     */
    public static void info(String message) {
        log("INFO", message);
    }

    /**
     * Logs an ERROR level message.
     * @param message The message to log.
     */
    public static void error(String message) {
        log("ERROR", message);
    }

    /**
     * Logs an ERROR level message with an exception.
     * @param message The message to log.
     * @param t The exception to log.
     */
    public static void error(String message, Throwable t) {
        log("ERROR", message + " | Exception: " + t.toString());
    }

    /**
     * Logs a DEBUG level message.
     * @param message The message to log.
     */
    public static void debug(String message) {
        log("DEBUG", message);
    }

    /**
     * Logs a WARN level message.
     * @param message The message to log.
     */
    public static void warn(String message) {
        log("WARN", message);
    }

    /**
     * Internal method to format and print the log in a single line.
     * @param level The log level.
     * @param message The actual log message.
     */
    private static void log(String level, String message) {
        String timestamp = LocalDateTime.now().format(FORMATTER);
        System.out.printf("[%s] [%s] %s%n", timestamp, level, message);
    }
}
