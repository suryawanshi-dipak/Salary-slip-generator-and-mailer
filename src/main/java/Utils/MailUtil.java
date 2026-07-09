package Utils;

import jakarta.mail.*;
import jakarta.mail.internet.*;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

/**
 * Utility class handling all email-related functionality:
 * - SMTP configuration loading
 * - Ledger tracking for idempotency (preventing duplicate sends)
 * - Sending emails with PDF attachments using Jakarta Mail
 * - Run logging for success/failures
 */
public class MailUtil {

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
            System.err.println("Warning: Could not load " + CONFIG_FILE + ". " + e.getMessage());
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
                System.err.println("Error reading sent ledger: " + e.getMessage());
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
                System.err.println("Error writing to sent ledger: " + e.getMessage());
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
            System.err.println("Failed to write to run log: " + e.getMessage());
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
        if (email == null || email.isEmpty() || !email.contains("@")) {
            logRun(month, empId, "Failed", "Invalid email address");
            return false;
        }

        File f = new File(pdfPath);
        if (!f.exists()) {
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
                logRun(month, empId, "Failed", "SMTP Authentication failed");
                return false;
            } catch (Exception e) {
                attempts++;
                if (attempts >= maxAttempts) {
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
            logRun(month, empId, "Sent", null);
        }

        return success;
    }
}
