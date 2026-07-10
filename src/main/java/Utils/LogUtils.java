package Utils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import Services.CsvReaderService; // Import your service to access the global UI toggle flag

/**
 * Utility class to provide simple, unified logging functionality across the
 * codebase. Logs are written to both the console and a timestamped log file.
 */
public class LogUtils {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter FILE_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    
    private static java.io.PrintWriter fileWriter;

    static {
        try {
            java.io.File logDir = new java.io.File("logs");
            if (!logDir.exists()) {
                logDir.mkdirs();
            }
            String fileName = "logs/app-" + LocalDateTime.now().format(FILE_DATE_FORMATTER) + ".log";
            fileWriter = new java.io.PrintWriter(new java.io.FileWriter(fileName, true), true);
            
            // Add a shutdown hook to close the writer gracefully
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                if (fileWriter != null) {
                    fileWriter.close();
                }
            }));
        } catch (java.io.IOException e) {
            System.err.println("Could not initialize LogUtils file writer: " + e.getMessage());
        }
    }

    /**
     * Logs an informational message.
     * 
     * @param message The message string to be logged.
     */
    public static void info(String message) {
        log("INFO", message);
    }
    
    /**
     * Logs an informational message with parameterized arguments.
     * Use {} placeholders in the format string.
     * 
     * @param format    The format string containing {} placeholders.
     * @param arguments The variables to be injected into the placeholders.
     */
    public static void info(String format, Object... arguments) {
        log("INFO", formatMessage(format, arguments));
    }

    /**
     * Logs an error message.
     * 
     * @param message The error message string to be logged.
     */
    public static void error(String message) {
        log("ERROR", message);
    }
    
    /**
     * Logs an error message with parameterized arguments.
     * If the last argument is a Throwable (Exception), it will automatically append
     * the exception details to the end of the log.
     * 
     * @param format    The format string containing {} placeholders.
     * @param arguments The variables to be injected into the placeholders.
     */
    public static void error(String format, Object... arguments) {
        // Check if the last argument is a Throwable
        if (arguments != null && arguments.length > 0 && arguments[arguments.length - 1] instanceof Throwable) {
            Throwable t = (Throwable) arguments[arguments.length - 1];
            Object[] trimmedArgs = new Object[arguments.length - 1];
            System.arraycopy(arguments, 0, trimmedArgs, 0, trimmedArgs.length);
            log("ERROR", formatMessage(format, trimmedArgs) + " | Exception: " + t.toString());
        } else {
            log("ERROR", formatMessage(format, arguments));
        }
    }

    /**
     * Logs an error message along with an exception stack trace trace element.
     * 
     * @param message The error message string.
     * @param t       The exception that was thrown.
     */
    public static void error(String message, Throwable t) {
        log("ERROR", message + " | Exception: " + t.toString());
    }

    /**
     * Logs a debug message.
     * 
     * @param message The debug message string to be logged.
     */
    public static void debug(String message) {
        log("DEBUG", message);
    }
    
    /**
     * Logs a debug message with parameterized arguments.
     * Use {} placeholders in the format string.
     * 
     * @param format    The format string containing {} placeholders.
     * @param arguments The variables to be injected into the placeholders.
     */
    public static void debug(String format, Object... arguments) {
        log("DEBUG", formatMessage(format, arguments));
    }

    /**
     * Logs a warning message.
     * 
     * @param message The warning message string to be logged.
     */
    public static void warn(String message) {
        log("WARN", message);
    }
    
    /**
     * Logs a warning message with parameterized arguments.
     * Use {} placeholders in the format string.
     * 
     * @param format    The format string containing {} placeholders.
     * @param arguments The variables to be injected into the placeholders.
     */
    public static void warn(String format, Object... arguments) {
        log("WARN", formatMessage(format, arguments));
    }

    /**
     * Internal utility method that replaces {} placeholders in a format string
     * with the string representation of provided arguments.
     * 
     * @param format    The format string containing {} placeholders.
     * @param arguments The variables to be injected.
     * @return The fully constructed string.
     */
    private static String formatMessage(String format, Object... arguments) {
        if (format == null || arguments == null || arguments.length == 0) {
            return format;
        }
        StringBuilder sb = new StringBuilder();
        int argIndex = 0;
        int i = 0;
        while (i < format.length()) {
            int index = format.indexOf("{}", i);
            if (index == -1) {
                sb.append(format.substring(i));
                break;
            }
            sb.append(format.substring(i, index));
            if (argIndex < arguments.length) {
                Object arg = arguments[argIndex++];
                sb.append(arg == null ? "null" : arg.toString());
            } else {
                sb.append("{}");
            }
            i = index + 2;
        }
        return sb.toString();
    }

    /**
     * Internal method to format and write the log to available outputs (Console and File).
     * Output generation is controlled by the CsvReaderService.isLoggingEnabled UI toggle flag.
     * 
     * @param level   The severity level of the log (e.g., INFO, ERROR, WARN).
     * @param message The fully constructed message to be logged.
     */
    private static void log(String level, String message) {
        // Connects your teammate's logs directly to your UI log toggle flag
        if (CsvReaderService.isLoggingEnabled) {
            String timestamp = LocalDateTime.now().format(FORMATTER);
            String formattedLog = String.format("[%s] [%s] %s", timestamp, level, message);
            
            // Log to console
            System.out.println(formattedLog);
            
            // Log to file
            if (fileWriter != null) {
                fileWriter.println(formattedLog);
            }
        }
    }
}