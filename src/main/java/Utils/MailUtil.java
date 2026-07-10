package Utils;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.mail.AuthenticationFailedException;
import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.Multipart;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;

/**
 * ============================================================================
 * PROJECT UNDERSTANDING - MailUtil
 * ============================================================================
 * ROLE:
 * This utility handles SMTP configurations, ledger logs, and secure email dispatches.
 * It is responsible for sending password-protected PDF slips directly to employee emails.
 *
 * DETAILED CAPABILITIES:
 * - SMTP Properties: Dynamically loads configuration from `DATA/smtp.properties`.
 * - Idempotency (Ledger): Saves sent keys (`empId_month`) to `DATA/sent_ledger.csv`
 *   to ensure we do not send duplicate emails if the dispatch process is re-run.
 *   `isSent(empId, month)` is checked prior to triggering a send action.
 * - Jakarta Mail: Forms multi-part messages with greeting text and PDF attachments.
 * - Retries: Securely attempts up to 2 dispatch retries with wait times on network issues.
 * - Logging: Generates structured audit trails (`Logs/run_report_<month>.log`) and
 *   uses standard SLF4J logs controlled by CsvReaderService.isLoggingEnabled.
 * ============================================================================
 */
public class MailUtil {

    private static final Logger logger = LoggerFactory.getLogger(MailUtil.class);

    private static void logInfo(String format, Object... arguments) {
        if (Services.CsvReaderService.isLoggingEnabled) {
            logger.info(format, arguments);
        }
    }

    private static void logWarn(String format, Object... arguments) {
        if (Services.CsvReaderService.isLoggingEnabled) {
            logger.warn(format, arguments);
        }
    }

    private static void logError(String format, Object... arguments) {
        if (Services.CsvReaderService.isLoggingEnabled) {
            logger.error(format, arguments);
        }
    }

    private static final String CONFIG_FILE = "DATA/smtp.properties";
    private static final String LEDGER_FILE = "DATA/sent_ledger.csv";

    private static Properties smtpProps;
    private static Set<String> sentKeys = new HashSet<>();

    static {
        // Load configurations
        smtpProps = new Properties();
        try (FileInputStream in = new FileInputStream(CONFIG_FILE)) {
            smtpProps.load(in);
        } catch (IOException e) {
            logWarn("Could not load config file {}: {}", CONFIG_FILE, e.getMessage());
        }

        // Load ledger
        if (Files.exists(Paths.get(LEDGER_FILE))) {
            try (BufferedReader br = new BufferedReader(new FileReader(LEDGER_FILE))) {
                String line;
                while ((line = br.readLine()) != null) {
                    if (!line.trim().isEmpty()) {
                        sentKeys.add(line.trim());
                    }
                }
            } catch (IOException e) {
                logError("Error reading sent ledger: {}", e.getMessage());
            }
        }
    }

    /* --- CONFIGURATION --- */
    /**
     * Retrieves the SMTP host from properties or defaults to Office365.
     * @return The SMTP host (e.g., smtp.gmail.com)
     */
    public static String getSmtpHost() {
        return smtpProps.getProperty("smtp.host", "smtp.office365.com");
    }

    /**
     * Retrieves the SMTP port from properties.
     * @return The SMTP port (defaults to 587)
     */
    public static String getSmtpPort() {
        return smtpProps.getProperty("smtp.port", "587");
    }

    /**
     * Retrieves the SMTP user account for authentication.
     * @return The sender's email account username
     */
    public static String getSmtpUser() {
        return smtpProps.getProperty("smtp.user", "");
    }

    /**
     * Retrieves the email address the slip appears to be sent from.
     * @return The "From" email address (defaults to the smtp user)
     */
    public static String getSmtpFrom() {
        return smtpProps.getProperty("smtp.from", getSmtpUser());
    }

    /**
     * Retrieves the SMTP password from properties.
     * @return The SMTP password, or empty string if not set
     */
    public static String getSmtpPass() {
        return smtpProps.getProperty("smtp.pass", "");
    }

    /**
     * Returns true if smtp.secure=true is set (implicit SSL, port 465).
     * @return true for SSL mode, false for STARTTLS mode
     */
    public static boolean isSmtpSecure() {
        return "true".equalsIgnoreCase(smtpProps.getProperty("smtp.secure", "false"));
    }

    /* --- IDEMPOTENCY / LEDGER --- */
    /**
     * Checks if a salary slip has already been successfully sent to this employee for the given month.
     * This prevents duplicate emails when a batch is re-run.
     * 
     * @param empId Employee ID
     * @param month Month string (e.g. "Jun-26")
     * @return true if already sent, false otherwise
     */
    public static boolean isSent(String empId, String month) {
        return sentKeys.contains(empId + "_" + month);
    }

    /**
     * Marks an employee's salary slip as successfully sent by updating the in-memory tracking set
     * and appending the record to the persistent ledger file (sent_ledger.csv).
     * 
     * @param empId Employee ID
     * @param month Month string (e.g. "Jun-26")
     */
    private static void markAsSent(String empId, String month) {
        String key = empId + "_" + month;
        if (sentKeys.add(key)) {
            try (FileWriter fw = new FileWriter(LEDGER_FILE, true);
                    BufferedWriter bw = new BufferedWriter(fw);
                    PrintWriter out = new PrintWriter(bw)) {
                out.println(key);
            } catch (IOException e) {
                logError("Error writing to sent ledger: {}", e.getMessage());
            }
        }
    }

    /* --- LOGGING --- */
    /**
     * Securely logs the result of an email send attempt to a monthly log file.
     * Sensitive information like salary amounts or full PII are strictly excluded.
     * 
     * @param month  The month being processed (determines the log file name)
     * @param empId  The Employee ID to log against
     * @param status Status of the operation (e.g. "Sent" or "Failed")
     * @param error  Detailed error message if it failed, or null if successful
     */
    private static void logRun(String month, String empId, String status, String error) {
        String logDir = "Logs";
        File dir = new File(logDir);
        if (!dir.exists())
            dir.mkdirs();

        String logFileName = logDir + "/run_report_" + month + ".log";
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        try (FileWriter fw = new FileWriter(logFileName, true);
                BufferedWriter bw = new BufferedWriter(fw);
                PrintWriter out = new PrintWriter(bw)) {

            String logEntry = String.format("[%s] E.Code: %s | Status: %s", timestamp, empId, status);
            if (error != null && !error.isEmpty()) {
                logEntry += " | Error: " + error;
            }
            out.println(logEntry);

        } catch (IOException e) {
            logError("Failed to write to run log: {}", e.getMessage());
        }
    }

    /* --- MAILER --- */
    /**
     * The core mailing engine. 
     * Generates a dynamic email body explaining the PDF password, attaches the generated PDF, 
     * sends it securely via SMTP (with retry logic), and automatically updates tracking and logging.
     * 
     * @param empId        Employee ID
     * @param month        Month of the slip
     * @param email        Recipient's email address
     * @param name         Employee's name for greeting
     * @param doj          Date of Joining (used to instruct them on the password logic)
     * @param pdfPath      Absolute path to the generated PDF slip
     * @param smtpPassword The SMTP password (provided securely at runtime by the user)
     * @return true if sent successfully, false if validation or dispatch failed
     */
    public static boolean sendAndTrackSlip(String empId, String month, String email, String name, String doj,
            String pdfPath, String smtpPassword) {
        logInfo("Starting email dispatch process for Employee: {}, Month: {}, Email: {}", empId, month, email);

        if (email == null || email.isEmpty() || !email.contains("@")) {
            logWarn("Failed to send email to Employee {}: Invalid email address ({})", empId, email);
            logRun(month, empId, "Failed", "Invalid email address");
            return false;
        }

        File f = new File(pdfPath);
        if (!f.exists()) {
            logError("Failed to send email to Employee {}: PDF file not found at {}", empId, pdfPath);
            logRun(month, empId, "Failed", "PDF file not found");
            return false;
        }

        String formattedDoj = doj;
        try {
            java.time.format.DateTimeFormatter inFormat = java.time.format.DateTimeFormatter.ofPattern("dd-MMM-yy",
                    java.util.Locale.ENGLISH);
            java.time.format.DateTimeFormatter outFormat = java.time.format.DateTimeFormatter.ofPattern("ddMMyyyy");
            java.time.LocalDate date = java.time.LocalDate.parse(doj, inFormat);
            formattedDoj = date.format(outFormat);
        } catch (Exception ex) {
            logWarn("Could not parse DOJ for email password check: {} for employee ID: {}", doj, empId);
        }

        String subject = "Salary Slip for " + month;
        String bodyText = "Dear " + name + ",\n\n"
                + "Please find attached your salary slip for " + month + ".\n\n"
                + "Note: This document is password protected. The password is your Employee ID followed by your Date of Joining (DDMMYYYY).\n"
                + "Example: If your ID is VT0001 and DOJ is 01-Apr-2019, the password is VT000101042019.\n\n"
                + "Regards,\nHR Department";

        String host = getSmtpHost();
        String port = getSmtpPort();
        String from = getSmtpFrom();
        String user = getSmtpUser();

        Properties props = new Properties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.host", host);
        props.put("mail.smtp.port", port);
        if (isSmtpSecure()) {
            // Port 465: implicit SSL
            props.put("mail.smtp.ssl.enable", "true");
            props.put("mail.smtp.socketFactory.port", port);
            props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
        } else {
            // Port 587: STARTTLS
            props.put("mail.smtp.starttls.enable", "true");
        }

        Session session = Session.getInstance(props, new Authenticator() {
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(user, smtpPassword);
            }
        });

        int attempts = 0;
        int maxAttempts = 2;
        boolean success = false;

        while (attempts < maxAttempts) {
            try {
                Message message = new MimeMessage(session);
                message.setFrom(new InternetAddress(from));
                message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(email));
                message.setSubject(subject);

                Multipart multipart = new MimeMultipart();

                MimeBodyPart messageBodyPart = new MimeBodyPart();
                messageBodyPart.setText(bodyText);
                multipart.addBodyPart(messageBodyPart);

                MimeBodyPart attachmentPart = new MimeBodyPart();
                attachmentPart.attachFile(f);
                multipart.addBodyPart(attachmentPart);

                message.setContent(multipart);
                Transport.send(message);

                success = true;
                break;

            } catch (AuthenticationFailedException e) {
                logError("Failed to send email to Employee {} due to SMTP Authentication failure", empId, e);
                logRun(month, empId, "Failed", "SMTP Authentication failed");
                return false;
            } catch (Exception e) {
                attempts++;
                logWarn("Attempt {} failed to send email to Employee {}: {}", attempts, empId, e.getMessage());
                if (attempts >= maxAttempts) {
                    logError("Max retry attempts reached. Failed to send email to Employee {}: {}", empId, e.getMessage(), e);
                    logRun(month, empId, "Failed", "SMTP Error: " + e.getMessage());
                    return false;
                }
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ie) {
                }
            }
        }

        if (success) {
            markAsSent(empId, month);
            logInfo("Successfully sent email with attachment to: {}", email);
            logRun(month, empId, "Sent", null);
        }

        return success;
    }
}
