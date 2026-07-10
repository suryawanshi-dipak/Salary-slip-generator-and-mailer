package Utils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import Services.CsvReaderService; // Import your service to access the global UI toggle flag

/**
 * Utility class to provide simple, one-line logging functionality across the
 * codebase.
 */
public class LogUtils {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static void info(String message) {
        log("INFO", message);
    }

    public static void error(String message) {
        log("ERROR", message);
    }

    public static void error(String message, Throwable t) {
        log("ERROR", message + " | Exception: " + t.toString());
    }

    public static void debug(String message) {
        log("DEBUG", message);
    }

    public static void warn(String message) {
        log("WARN", message);
    }

    /**
     * Internal method to format and print the log in a single line if logging is
     * enabled.
     */
    private static void log(String level, String message) {
        // Connects your teammate's logs directly to your UI log toggle flag
        if (CsvReaderService.isLoggingEnabled) {
            String timestamp = LocalDateTime.now().format(FORMATTER);
            System.out.printf("[%s] [%s] %s%n", timestamp, level, message);
        }
    }
}