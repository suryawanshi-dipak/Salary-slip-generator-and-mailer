package Services;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;
import java.util.Properties;

/**
 * Resolves where {@code DATA/smtp.properties} is read from and, more importantly,
 * where it can safely be <b>written</b>.
 *
 * <p>When run from a source checkout, {@code DATA/smtp.properties} is writable and
 * everything works as it always has. But the packaged Windows installer
 * ({@code jpackage --app-content package-data/DATA}) ships that same file inside
 * the install directory, which by default is {@code C:\Program Files\...} -
 * writable only by an administrator. Saving Configuration there fails with
 * "Access is denied". To fix that without requiring the app to run elevated,
 * writes are redirected to a per-user location the account always owns:
 * {@code %LOCALAPPDATA%\SalarySlipGenerator\smtp.properties} (or
 * {@code ~/.salaryslipgenerator/smtp.properties} if {@code LOCALAPPDATA} isn't
 * set). The bundled file is only ever read, never touched.</p>
 *
 * <p>{@link #load()} merges the bundled file (defaults shipped with the install)
 * with the per-user file layered on top, so a user edit always wins even though
 * the bundled copy under Program Files can't be updated to match.</p>
 */
public final class AppConfigStore {

    private static final String RELATIVE_PATH = "DATA/smtp.properties";
    private static final String APP_DATA_DIR_NAME = "SalarySlipGenerator";

    private AppConfigStore() {
    }

    /** Loads the bundled defaults overlaid with any saved per-user overrides. */
    public static Properties load() {
        Properties props = new Properties();
        File bundled = bundledFile();
        if (bundled != null) {
            readInto(props, bundled);
        }
        File writable = userConfigFile();
        if (writable.isFile()) {
            readInto(props, writable); // overrides the bundled values
        }
        return props;
    }

    /**
     * Merges {@code updates} into the resolved writable config file and saves it.
     * If the bundled {@code DATA/smtp.properties} is itself writable (a normal
     * source checkout), it is updated directly, exactly like before. Otherwise the
     * per-user copy is created/updated instead - seeded from the bundled file the
     * first time, so existing settings are not lost.
     *
     * @param updates key/value pairs to set (existing keys not listed are kept)
     * @param comment header comment written above the properties, like
     *                {@link Properties#store}
     * @throws IOException if the target file cannot be written
     */
    public static void save(Map<String, String> updates, String comment) throws IOException {
        File target = resolveWriteTarget();

        Properties props = new Properties();
        if (target.isFile()) {
            readInto(props, target);
        } else {
            File bundled = bundledFile();
            if (bundled != null) {
                readInto(props, bundled); // seed the new per-user copy with the shipped defaults
            }
        }
        updates.forEach((k, v) -> props.setProperty(k, v == null ? "" : v));

        File parent = target.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        try (OutputStream out = new FileOutputStream(target)) {
            props.store(out, comment);
        }
        Utils.LogUtils.info("Saved configuration to {}", target.getAbsolutePath());
    }

    /**
     * @return the file {@link #save} would write to right now - useful for
     *         diagnostics/log messages, e.g. telling the user where their settings
     *         actually live when the bundled location isn't writable.
     */
    public static File resolveWriteTarget() {
        File bundled = bundledFile();
        if (bundled != null && bundled.canWrite()) {
            return bundled; // source checkout / user-writable install dir - unchanged behaviour
        }
        return userConfigFile();
    }

    /**
     * Resolves a writable path for another file that normally lives in
     * {@code DATA/} alongside {@code smtp.properties} - currently
     * {@code sent_ledger.csv} (see {@link Utils.MailUtil}). Same rule as the
     * config file: keep using the bundled {@code DATA/<fileName>} copy if it's
     * writable (dev checkout / user-writable install dir), otherwise redirect to
     * the shared per-user app-data folder.
     *
     * @param fileName simple file name, e.g. {@code "sent_ledger.csv"}
     */
    public static File resolveDataFile(String fileName) {
        File bundled = new File("DATA", fileName);
        if (!bundled.isFile()) {
            File alt = new File("Salary-slip-generator-and-mailer/DATA", fileName);
            if (alt.isFile()) {
                bundled = alt;
            }
        }
        boolean writable = bundled.isFile()
                ? bundled.canWrite()
                : (bundled.getParentFile() != null && bundled.getParentFile().isDirectory()
                        && bundled.getParentFile().canWrite());
        if (writable) {
            return bundled;
        }
        return new File(appDataDir(), fileName);
    }

    /** The read-only copy shipped with the app (or present in a dev checkout). */
    private static File bundledFile() {
        File f = new File(RELATIVE_PATH);
        if (f.isFile()) {
            return f;
        }
        f = new File("Salary-slip-generator-and-mailer/" + RELATIVE_PATH);
        return f.isFile() ? f : null;
    }

    /** Per-user writable fallback location, always owned by the running account. */
    static File userConfigFile() {
        return new File(appDataDir(), "smtp.properties");
    }

    /** Shared per-user app-data folder, also used by {@link Utils.MailUtil} for the sent ledger. */
    static File appDataDir() {
        String localAppData = System.getenv("LOCALAPPDATA");
        if (localAppData != null && !localAppData.isBlank()) {
            return new File(localAppData, APP_DATA_DIR_NAME);
        }
        return new File(System.getProperty("user.home"), "." + APP_DATA_DIR_NAME.toLowerCase());
    }

    private static void readInto(Properties props, File file) {
        try (InputStream in = new FileInputStream(file)) {
            props.load(in);
        } catch (IOException e) {
            Utils.LogUtils.warn("Could not load config {}: {}", file, e.getMessage());
        }
    }
}
