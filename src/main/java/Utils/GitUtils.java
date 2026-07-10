package Utils;

import javax.swing.*;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ============================================================================
 * PROJECT UNDERSTANDING - GitUtils
 * ============================================================================
 * ROLE:
 * This utility handles checking, downloading, and hot-swapping application
 * releases.
 *
 * HOW IT WORKS:
 * - On startup, `startUpdateCheck(owner)` spins up a background updates thread.
 * - Checking: Queries the public GitHub Releases API for the latest published
 * tag.
 * - Comparing: Compares latest tag with `CURRENT_VERSION` (using semantic
 * versioning).
 * - Downloading: Retrieves the release JAR and saves it under the user's Local
 * AppData path:
 * `%USERPROFILE%\AppData\Local\SalarySlipGeneratorupdates\SalarySlipGenerator-update.jar`.
 * - Hot-Swapping: Spawns a custom URLClassLoader to load classes dynamically
 * from the update
 * JAR, disposes the old frame, and invokes the new main method reflecting
 * changes on the fly.
 * - Bypass: CURRENT_VERSION is set to "9.9.9" locally to bypass auto-update
 * overriding in dev.
 * ============================================================================
 */
public class GitUtils {

    private static final String CURRENT_VERSION = "9.9.9"; // set high to bypass updates in dev
    private static final String GITHUB_API = "https://api.github.com/repos/suryawanshi-dipak/Salary-slip-generator-and-mailer/releases/latest";

    /**
     * Fully-qualified name of the class containing the application's main() entry
     * point.
     */
    private static final String MAIN_CLASS = "UI.SalarySlipGenerator";

    // Persistent folder — survives across launches so the next startup can
    // auto-apply
    private static final Path UPDATE_DIR = Path.of(
            System.getProperty("user.home"), "AppData", "Local", "SalarySlipGenerator", "updates");

    /**
     * Kicks off a background thread that first applies any previously-downloaded
     * update silently, then checks GitHub for a brand-new release.
     *
     * @param owner The main application frame (used for dialogs and disposal on
     *              update).
     */
    public static void startUpdateCheck(JFrame owner) {
<<<<<<< Updated upstream
        LogUtils.info("Starting application. Current Build Version: " + CURRENT_VERSION);
=======
        LogUtils.info("Initializing update check sequence. Current runtime version: " + CURRENT_VERSION);
>>>>>>> Stashed changes
        Thread t = new Thread(() -> {
            LogUtils.info("Update engine background thread spawned successfully.");
            // 1. Apply a previously-downloaded update silently (no dialog)
            if (applyPendingUpdate(owner)) {
                LogUtils.info("Pending local update successfully applied. Exiting current updater workflow context.");
                return;
            }
            // 2. Check GitHub for a brand-new update
            try {
                checkForUpdate(owner);
            } catch (Exception e) {
                LogUtils.error("Failed to complete remote application update verification process", e);
            }
        });
        t.setDaemon(true);
        t.start();
    }

    /**
     * If a newer JAR was downloaded in a previous session, hot-swap it silently.
     * Returns true if a swap was initiated (caller should stop further checks).
     */
    private static boolean applyPendingUpdate(JFrame owner) {
        LogUtils.info("Scanning local update cache directory: " + UPDATE_DIR.toAbsolutePath());
        try {
            Path jar = UPDATE_DIR.resolve("SalarySlipGenerator-update.jar");
            Path verFile = UPDATE_DIR.resolve("version.txt");
            if (!Files.exists(jar) || !Files.exists(verFile)) {
                LogUtils.info("No complete pending local update files found in cache.");
                return false;
            }

            String pendingVersion = Files.readString(verFile).trim();
            LogUtils.info("Found cached update package file metadata version: " + pendingVersion);
            if (!isNewer(pendingVersion, CURRENT_VERSION)) {
                LogUtils.info("Cached version [" + pendingVersion + "] is not newer than currently active version ["
                        + CURRENT_VERSION + "]. Skipping hot-swap apply.");
                return false;
            }

<<<<<<< Updated upstream
            LogUtils.info("Applying pending build update to version: " + pendingVersion);
=======
            LogUtils.info("Executing silent cold-apply hot-swap for locally staged update package version ["
                    + pendingVersion + "].");
>>>>>>> Stashed changes
            hotSwap(owner, jar); // silent — no confirmation dialog
            return true;
        } catch (Exception e) {
            LogUtils.error("Error encountered while evaluation/applying local pending update file structures", e);
            return false;
        }
    }

    private static void checkForUpdate(JFrame owner) throws Exception {
        LogUtils.info("Querying GitHub Release Distribution API Endpoint: " + GITHUB_API);
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(GITHUB_API))
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "SalarySlipGenerator-App/" + CURRENT_VERSION)
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            LogUtils.warn("GitHub application API query rejected. Server responded with unexpected status code: "
                    + resp.statusCode());
            return;
        }

        String body = resp.body();
        String tagName = extractJsonValue(body, "tag_name");
        if (tagName == null) {
            LogUtils.error(
                    "Failed to parse valid release reference payload tag data structural keys from GitHub payload.");
            return;
        }

        String latest = tagName.startsWith("v") ? tagName.substring(1) : tagName;
<<<<<<< Updated upstream
        if (!isNewer(latest, CURRENT_VERSION)) {
            LogUtils.info("Build is up-to-date. No new updates found.");
=======
        LogUtils.info("Remote server indicates latest published production artifact tag distribution is: v" + latest);
        if (!isNewer(latest, CURRENT_VERSION)) {
            LogUtils.info("Current system build version [" + CURRENT_VERSION
                    + "] is up-to-date with remote distribution release version [" + latest + "].");
>>>>>>> Stashed changes
            return;
        }

        String downloadUrl = findJarDownloadUrl(body);
        if (downloadUrl == null) {
            LogUtils.error(
                    "Matched update metadata targets but failed to extract executable artifact asset URL path allocations from repository stream.");
            return;
        }

<<<<<<< Updated upstream
        LogUtils.info("New build update found: v" + latest + ". Prompting user.");
=======
        LogUtils.info("Dispatched prompt notification payload elements request to UI thread. Target update link: "
                + downloadUrl);
>>>>>>> Stashed changes
        SwingUtilities.invokeLater(() -> promptUpdate(owner, latest, downloadUrl));
    }

    private static void promptUpdate(JFrame owner, String version, String downloadUrl) {
        LogUtils.info(
                "Displaying modal confirmation dialogue notification matrix to operator for update option v" + version);
        int choice = JOptionPane.showConfirmDialog(owner,
                "<html>A new version <b>v" + version + "</b> is available!<br>"
                        + "You are running: v" + CURRENT_VERSION + "<br><br>"
                        + "Update now? The app will refresh automatically.</html>",
                "Update Available",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.INFORMATION_MESSAGE);

        if (choice == JOptionPane.YES_OPTION) {
            LogUtils.info("User choice: ACCEPTED. Starting live download runtime extraction sequence operations.");
            downloadAndHotSwap(owner, version, downloadUrl);
        } else {
            LogUtils.info("User choice: DECLINED. Postponing upgrade operations for version context: v" + version);
        }
    }

    private static void downloadAndHotSwap(JFrame owner, String version, String downloadUrl) {
        owner.setTitle("Salary Slip Generator  —  Updating to v" + version + "...");

        new Thread(() -> {
            try {
                LogUtils.info("Creating target update staging file structures paths on filesystem at: "
                        + UPDATE_DIR.toAbsolutePath());
                Files.createDirectories(UPDATE_DIR);
                Path jar = UPDATE_DIR.resolve("SalarySlipGenerator-update.jar");

                // Download JAR
                LogUtils.info("Initiating binary streams extraction downloads from target network resource location: "
                        + downloadUrl);
                URL url = URI.create(downloadUrl).toURL();
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setInstanceFollowRedirects(true);
                conn.setRequestProperty("User-Agent", "SalarySlipGenerator-App/" + CURRENT_VERSION);
                conn.connect();

                try (var in = conn.getInputStream();
                        var out = Files.newOutputStream(jar)) {
                    byte[] buf = new byte[8192];
                    int read;
                    long totalBytes = 0;
                    while ((read = in.read(buf)) != -1) {
                        out.write(buf, 0, read);
                        totalBytes += read;
                    }
                    LogUtils.info("Binary package transfer finished. Received payload byte volume footprint: "
                            + totalBytes + " bytes.");
                }

                // Persist version so the next startup auto-applies without a dialog
                Files.writeString(UPDATE_DIR.resolve("version.txt"), version);
<<<<<<< Updated upstream
                LogUtils.info("Build update v" + version + " downloaded successfully. Initiating hot-swap.");
=======
                LogUtils.info(
                        "Saved update target descriptor metadata successfully. Proceeding to live frame dynamic class loader hot-swaps execution.");

>>>>>>> Stashed changes
                hotSwap(owner, jar);

            } catch (IOException | ReflectiveOperationException ex) {
                LogUtils.error("Critical pipeline disruption while retrieving or processing remote deployment objects",
                        ex);
                SwingUtilities.invokeLater(() -> {
                    owner.setTitle("Salary Slip Generator");
                    JOptionPane.showMessageDialog(owner,
                            "Update failed:\n" + ex.getMessage(),
                            "Update Error", JOptionPane.ERROR_MESSAGE);
                });
            }
        }, "ssg-update-thread").start();
    }

    private static void hotSwap(JFrame owner, Path jar)
            throws IOException, ReflectiveOperationException {
        LogUtils.info(
                "Assembling isolated class namespace boundaries configurations using dynamic URLClassLoader paths targets: "
                        + jar.toUri().toURL());
        URLClassLoader loader = new URLClassLoader(
                new URL[] { jar.toUri().toURL() },
                ClassLoader.getPlatformClassLoader()) {
            @Override
            public Class<?> loadClass(String name) throws ClassNotFoundException {
                if (name.startsWith("UI.") || name.startsWith("Utils.") || name.startsWith("Services.")) {
                    try {
                        return findClass(name);
                    } catch (ClassNotFoundException ignored) {
                        // Fallback implementation behavior inside internal nested resolution loops
                    }
                }
                return super.loadClass(name);
            }
        };
        // Loader must stay open while the new class runs (lambdas load lazily).
        // Close it only when the JVM exits.
        Runtime.getRuntime().addShutdownHook(
                new Thread(() -> {
                    try {
                        LogUtils.info(
                                "Shutting down live container environment context hooks. Clearing old loader streams layers.");
                        loader.close();
                    } catch (IOException e) {
                        LogUtils.error(
                                "Failed to gracefully close system update URLClassLoader hooks maps during JVM shutdown",
                                e);
                    }
                }));

        LogUtils.info(
                "Instantiating remote target application main container frame context points structural path targets: "
                        + MAIN_CLASS);
        Class<?> newClass = loader.loadClass(MAIN_CLASS);
        Method mainMethod = newClass.getMethod("main", String[].class);

        SwingUtilities.invokeLater(() -> {
            try {
                if (owner != null) {
                    LogUtils.info("Disposing obsolete reference interface windows dependencies.");
                    owner.dispose();
                }
                LogUtils.info("Invoking main entry points inside the update scope layers.");
                mainMethod.invoke(null, (Object) new String[] {});
            } catch (ReflectiveOperationException e) {
                LogUtils.error("Runtime compilation failure encountered while executing reflection components mappings",
                        e);
                JOptionPane.showMessageDialog(null,
                        "Hot-reload failed:\n" + e.getMessage(),
                        "Update Error", JOptionPane.ERROR_MESSAGE);
            }
        });
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static String extractJsonValue(String json, String key) {
        Matcher m = Pattern.compile("\"" + key + "\"\\s*:\\s*\"([^\"]+)\"").matcher(json);
        return m.find() ? m.group(1) : null;
    }

    private static String findJarDownloadUrl(String json) {
        Matcher m = Pattern.compile(
                "\"browser_download_url\"\\s*:\\s*\"([^\"]+\\.jar)\"").matcher(json);
        return m.find() ? m.group(1) : null;
    }

    private static boolean isNewer(String latest, String current) {
        int[] l = parseVersion(latest);
        int[] c = parseVersion(current);
        for (int i = 0; i < Math.max(l.length, c.length); i++) {
            int lv = i < l.length ? l[i] : 0;
            int cv = i < c.length ? c[i] : 0;
            if (lv > cv)
                return true;
            if (lv < cv)
                return false;
        }
        return false;
    }

    private static int[] parseVersion(String v) {
        String[] parts = v.split("\\.");
        int[] nums = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try {
                nums[i] = Integer.parseInt(parts[i].replaceAll("[^0-9]", ""));
            } catch (NumberFormatException ignored) {
                nums[i] = 0;
            }
        }
        return nums;
    }
}