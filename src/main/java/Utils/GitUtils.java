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
 * Utility class handling GitHub-release based auto-updates for the desktop app:
 * - Checks the latest GitHub release for a newer version
 * - Downloads the release jar and hot-swaps the running application
 * - Persists a pending update so the next startup can auto-apply it silently
 */
public class GitUtils {

    private static final String CURRENT_VERSION = "1.0.0"; // replaced at build time by CI
    private static final String GITHUB_API =
        "https://api.github.com/repos/suryawanshi-dipak/Salary-slip-generator-and-mailer/releases/latest";

    /** Fully-qualified name of the class containing the application's main() entry point. */
    private static final String MAIN_CLASS = "UI.SalarySlipGenerator";

    // Persistent folder — survives across launches so the next startup can auto-apply
    private static final Path UPDATE_DIR = Path.of(
        System.getProperty("user.home"), "AppData", "Local", "SalarySlipGenerator", "updates");

    /**
     * Kicks off a background thread that first applies any previously-downloaded
     * update silently, then checks GitHub for a brand-new release.
     *
     * @param owner The main application frame (used for dialogs and disposal on update).
     */
    public static void startUpdateCheck(JFrame owner) {
        LogUtils.info("Starting application. Current Build Version: " + CURRENT_VERSION);
        Thread t = new Thread(() -> {
            // 1. Apply a previously-downloaded update silently (no dialog)
            if (applyPendingUpdate(owner)) return;
            // 2. Check GitHub for a brand-new update
            try { checkForUpdate(owner); } catch (Exception ignored) {}
        });
        t.setDaemon(true);
        t.start();
    }

    /**
     * If a newer JAR was downloaded in a previous session, hot-swap it silently.
     * Returns true if a swap was initiated (caller should stop further checks).
     */
    private static boolean applyPendingUpdate(JFrame owner) {
        try {
            Path jar = UPDATE_DIR.resolve("SalarySlipGenerator-update.jar");
            Path verFile = UPDATE_DIR.resolve("version.txt");
            if (!Files.exists(jar) || !Files.exists(verFile)) return false;

            String pendingVersion = Files.readString(verFile).trim();
            if (!isNewer(pendingVersion, CURRENT_VERSION)) return false;

            LogUtils.info("Applying pending build update to version: " + pendingVersion);
            hotSwap(owner, jar); // silent — no confirmation dialog
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static void checkForUpdate(JFrame owner) throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(GITHUB_API))
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "SalarySlipGenerator-App/" + CURRENT_VERSION)
            .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) return;

        String body = resp.body();
        String tagName = extractJsonValue(body, "tag_name");
        if (tagName == null) return;

        String latest = tagName.startsWith("v") ? tagName.substring(1) : tagName;
        if (!isNewer(latest, CURRENT_VERSION)) {
            LogUtils.info("Build is up-to-date. No new updates found.");
            return;
        }

        String downloadUrl = findJarDownloadUrl(body);
        if (downloadUrl == null) return;

        LogUtils.info("New build update found: v" + latest + ". Prompting user.");
        SwingUtilities.invokeLater(() -> promptUpdate(owner, latest, downloadUrl));
    }

    private static void promptUpdate(JFrame owner, String version, String downloadUrl) {
        int choice = JOptionPane.showConfirmDialog(owner,
            "<html>A new version <b>v" + version + "</b> is available!<br>"
                + "You are running: v" + CURRENT_VERSION + "<br><br>"
                + "Update now? The app will refresh automatically.</html>",
            "Update Available",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.INFORMATION_MESSAGE);

        if (choice == JOptionPane.YES_OPTION) {
            downloadAndHotSwap(owner, version, downloadUrl);
        }
    }

    private static void downloadAndHotSwap(JFrame owner, String version, String downloadUrl) {
        owner.setTitle("Salary Slip Generator  —  Updating to v" + version + "...");

        new Thread(() -> {
            try {
                Files.createDirectories(UPDATE_DIR);
                Path jar = UPDATE_DIR.resolve("SalarySlipGenerator-update.jar");

                // Download JAR
                URL url = URI.create(downloadUrl).toURL();
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setInstanceFollowRedirects(true);
                conn.setRequestProperty("User-Agent", "SalarySlipGenerator-App/" + CURRENT_VERSION);
                conn.connect();

                try (var in = conn.getInputStream();
                     var out = Files.newOutputStream(jar)) {
                    byte[] buf = new byte[8192];
                    int read;
                    while ((read = in.read(buf)) != -1) out.write(buf, 0, read);
                }

                // Persist version so the next startup auto-applies without a dialog
                Files.writeString(UPDATE_DIR.resolve("version.txt"), version);
                LogUtils.info("Build update v" + version + " downloaded successfully. Initiating hot-swap.");
                hotSwap(owner, jar);

            } catch (IOException | ReflectiveOperationException ex) {
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
        URLClassLoader loader = new URLClassLoader(
            new URL[]{jar.toUri().toURL()},
            ClassLoader.getPlatformClassLoader()
        ) {
            @Override
            public Class<?> loadClass(String name) throws ClassNotFoundException {
                if (name.startsWith("UI.") || name.startsWith("Utils.") || name.startsWith("Services.")) {
                    try { return findClass(name); }
                    catch (ClassNotFoundException ignored) {}
                }
                return super.loadClass(name);
            }
        };
        // Loader must stay open while the new class runs (lambdas load lazily).
        // Close it only when the JVM exits.
        Runtime.getRuntime().addShutdownHook(
            new Thread(() -> { try { loader.close(); } catch (IOException ignored) {} })
        );

        Class<?> newClass = loader.loadClass(MAIN_CLASS);
        Method mainMethod = newClass.getMethod("main", String[].class);

        SwingUtilities.invokeLater(() -> {
            try {
                if (owner != null) owner.dispose();
                mainMethod.invoke(null, (Object) new String[]{});
            } catch (ReflectiveOperationException e) {
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
            if (lv > cv) return true;
            if (lv < cv) return false;
        }
        return false;
    }

    private static int[] parseVersion(String v) {
        String[] parts = v.split("\\.");
        int[] nums = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try { nums[i] = Integer.parseInt(parts[i].replaceAll("[^0-9]", "")); }
            catch (NumberFormatException ignored) { nums[i] = 0; }
        }
        return nums;
    }
}
