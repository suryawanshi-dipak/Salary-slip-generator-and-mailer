package Services;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

/**
 * Stores and validates the path of the fixed <b>Master CTC</b> CSV file.
 *
 * <p>The Master CTC file holds each employee's salary structure (Basic, HRA,
 * Special Allowance, KRA, PT, TDS, bonuses, bank details). The business maintains
 * it out-of-band; the user points the application at it <b>once</b> from the
 * Configuration screen and it is then reused for every "Generate Slips" run.</p>
 *
 * <p>The path is persisted in {@code DATA/smtp.properties} under
 * {@code masterctc.file.path}, using the same file the HRMS API URL/key settings
 * already live in (see {@link LeaveService}). There is no database.</p>
 */
public class ConfigService {

    private static final String CONFIG_FILE =
            new File("Salary-slip-generator-and-mailer/DATA/smtp.properties").exists()
                    ? "Salary-slip-generator-and-mailer/DATA/smtp.properties"
                    : "DATA/smtp.properties";

    private static final String KEY_MASTER_CTC_PATH = "masterctc.file.path";
    private static final String KEY_BANK_TEMPLATE_PATH = "bankfile.template.path";

    private ConfigService() {
    }

    /**
     * @return the configured Master CTC file path, or an empty string if never set.
     */
    public static String getMasterCtcPath() {
        return loadProperties().getProperty(KEY_MASTER_CTC_PATH, "").trim();
    }

    /**
     * @return the configured HDFC Enet bank-template .xls path, or an empty string
     *         if never set (in which case {@link BankFileWriter} falls back to the
     *         bundled {@code DATA/} template).
     */
    public static String getBankTemplatePath() {
        return loadProperties().getProperty(KEY_BANK_TEMPLATE_PATH, "").trim();
    }

    /**
     * @return {@code true} once a non-blank Master CTC path has been saved.
     */
    public static boolean isConfigured() {
        return !getMasterCtcPath().isEmpty();
    }

    /**
     * Persists the Master CTC file path to {@code smtp.properties}, preserving the
     * other properties already in the file. The CSV file itself is never touched.
     *
     * @param path absolute path to the Master CTC CSV file
     * @throws IOException if the properties file cannot be written
     */
    public static void saveMasterCtcPath(String path) throws IOException {
        save(KEY_MASTER_CTC_PATH, path, "Updated Master CTC file path");
    }

    /**
     * Persists the bank-template .xls path (may be blank to clear it and use the
     * bundled template). The template file itself is never touched.
     */
    public static void saveBankTemplatePath(String path) throws IOException {
        save(KEY_BANK_TEMPLATE_PATH, path, "Updated bank template file path");
    }

    private static void save(String key, String value, String comment) throws IOException {
        Properties props = loadProperties();
        props.setProperty(key, value != null ? value.trim() : "");

        File file = new File(CONFIG_FILE);
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        try (FileOutputStream out = new FileOutputStream(file)) {
            props.store(out, comment);
            Utils.LogUtils.info("Saved {} to {}", key, CONFIG_FILE);
        }
    }

    /**
     * Validates a candidate bank-template path. Blank is allowed (falls back to the
     * bundled template); a non-blank value must point at a readable {@code .xls}.
     *
     * @return {@code null} when acceptable, otherwise a user-facing message.
     */
    public static String validateBankTemplatePath(String path) {
        if (path == null || path.trim().isEmpty()) {
            return null; // optional - bundled template is used
        }
        String trimmed = path.trim();
        File file = new File(trimmed);
        if (!file.isFile()) {
            return "The bank template file cannot be found:\n" + trimmed;
        }
        if (!file.canRead()) {
            return "The bank template file cannot be read (check permissions):\n" + trimmed;
        }
        if (!trimmed.toLowerCase().endsWith(".xls")) {
            return "The bank template must be a .xls file:\n" + trimmed;
        }
        return null;
    }

    /**
     * Validates the currently configured Master CTC path for use by "Generate Slips".
     *
     * @return {@code null} when the configuration is usable, otherwise a
     *         user-facing message describing the problem.
     */
    public static String validate() {
        return validatePath(getMasterCtcPath());
    }

    /**
     * Validates an arbitrary candidate path (used by the Configuration dialog
     * before the value is saved).
     *
     * @param path the path to check
     * @return {@code null} when the path points at a readable Master CTC CSV,
     *         otherwise a user-facing message.
     */
    public static String validatePath(String path) {
        if (path == null || path.trim().isEmpty()) {
            return "Master CTC file path is not configured. Please configure the "
                    + "Master_CTC.CSV file path before generating salary slips.";
        }

        String trimmed = path.trim();
        File file = new File(trimmed);

        if (!file.isFile()) {
            return "The configured Master CTC file cannot be found:\n" + trimmed;
        }
        if (!file.canRead()) {
            return "The configured Master CTC file cannot be read (check permissions):\n" + trimmed;
        }
        if (!trimmed.toLowerCase().endsWith(".csv")) {
            return "The configured Master CTC file must be a .csv file:\n" + trimmed;
        }

        String header = readFirstNonEmptyLine(file);
        if (header == null) {
            return "The configured Master CTC file is empty:\n" + trimmed;
        }
        String h = header.toLowerCase();
        java.util.List<String> missing = new java.util.ArrayList<>();
        for (String required : new String[] { "e.code", "total basic", "net salary", "email" }) {
            if (!h.contains(required)) {
                missing.add(required);
            }
        }
        if (!missing.isEmpty()) {
            return "This file does not look like a Master CTC CSV (missing column(s): "
                    + String.join(", ", missing) + "):\n" + trimmed;
        }

        return null;
    }

    private static String readFirstNonEmptyLine(File file) {
        try (java.io.BufferedReader br = new java.io.BufferedReader(new java.io.FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (!line.trim().isEmpty()) {
                    return line;
                }
            }
        } catch (Exception e) {
            Utils.LogUtils.warn("Could not read Master CTC header from {}: {}", file, e.getMessage());
        }
        return null;
    }

    private static Properties loadProperties() {
        Properties props = new Properties();
        try {
            File propFile = new File("DATA/smtp.properties");
            if (!propFile.exists()) {
                propFile = new File("Salary-slip-generator-and-mailer/DATA/smtp.properties");
            }
            try (java.io.InputStream in = new java.io.FileInputStream(propFile)) {
                props.load(in);
            }
        } catch (Exception e) {
            System.err.println("Could not load smtp.properties: " + e.getMessage());
        }
        return props;
    }
}
