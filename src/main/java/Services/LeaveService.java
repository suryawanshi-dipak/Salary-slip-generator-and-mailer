package Services;

import java.io.File;

import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Properties;
import com.google.gson.Gson;

/**
 * Service to interact with the HRMS Leave Payroll Export API.
 */
public class LeaveService {

    private static final String CONFIG_FILE = new File("Salary-slip-generator-and-mailer/DATA/smtp.properties").exists() ? "Salary-slip-generator-and-mailer/DATA/smtp.properties" : "DATA/smtp.properties";
    private static final String DEFAULT_URL = "http://localhost:5000/api/payroll-export/leaves";
    private static final String DEFAULT_KEY = "sk_live_test_payroll_key_9999";

    // --- JSON Mapping Classes for Gson ---
    public static class LeaveResponse {
        public boolean success;
        public String month;
        public String message;
        public Map<String, Double> data; // Maps Employee ID to LOP days
    }

    /**
     * Reads the Leave API URL from properties or returns the default.
     */
    public static String getApiUrl() {
        Properties props = loadProperties();
        return props.getProperty("leave.api.url", DEFAULT_URL);
    }

    /**
     * Reads the Leave API Key from properties or returns the default.
     */
    public static String getApiKey() {
        Properties props = loadProperties();
        return props.getProperty("leave.api.key", DEFAULT_KEY);
    }

    /**
     * Saves updated Leave API URL and Key to smtp.properties, preserving existing values.
     */
    public static void saveSettings(String url, String key) throws IOException {
        Properties props = loadProperties();
        props.setProperty("leave.api.url", url != null ? url.trim() : DEFAULT_URL);
        props.setProperty("leave.api.key", key != null ? key.trim() : DEFAULT_KEY);
        
        File file = new File(CONFIG_FILE);
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        
        try (FileOutputStream out = new FileOutputStream(file)) {
            props.store(out, "Updated Leave API Settings");
            Utils.LogUtils.info("Saved leave API settings to {}", CONFIG_FILE);
        }
    }

    private static Properties loadProperties() {
        Properties props = new Properties();
        try {
            java.io.File propFile = new java.io.File("DATA/smtp.properties");
            if (!propFile.exists()) {
                propFile = new java.io.File("Salary-slip-generator-and-mailer/DATA/smtp.properties");
            }
            try (java.io.InputStream in = new java.io.FileInputStream(propFile)) {
                props.load(in);
            }
        } catch (Exception e) {
            System.err.println("Could not load smtp.properties: " + e.getMessage());
        }
        return props;
    }

    /**
     * Fetches LOP leaves from the HRMS API for a given month and API key.
     * 
     * @param apiUrl The base URL of the API endpoint.
     * @param apiKey The API authentication key.
     * @param month The target due month in YYYY-MM format.
     * @return LeaveResponse mapped from JSON.
     * @throws Exception If fetching or parsing fails.
     */
    /**
     * Queries the HRMS backend to fetch approved Loss of Pay (LOP) leaves for the specified month.
     * 
     * @param apiUrl The base URL of the HRMS backend.
     * @param apiKey The secret API key.
     * @param month The target payout month in YYYY-MM format.
     * @return LeaveResponse containing matched employee LOP days.
     */
    public static LeaveResponse fetchLeaves(String apiUrl, String apiKey, String month) throws Exception {
        if (month == null || !month.matches("^\\d{4}-\\d{2}$")) {
            throw new IllegalArgumentException("Invalid month format. Expected YYYY-MM.");
        }

        // Build target URI with month parameter
        String fullUrl = apiUrl + (apiUrl.contains("?") ? "&" : "?") + "month=" + month;
        Utils.LogUtils.info("Fetching leaves for payroll from: {}", fullUrl);

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(fullUrl))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .header("x-api-key", apiKey != null ? apiKey.trim() : "")
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        int statusCode = response.statusCode();
        String json = response.body();

        Utils.LogUtils.info("Leave API Response received. Status code: {}", statusCode);

        Gson gson = new Gson();
        if (statusCode == 200) {
            LeaveResponse res = gson.fromJson(json, LeaveResponse.class);
            if (res != null && res.success) {
                return res;
            } else {
                String errMsg = (res != null && res.message != null) ? res.message : "API reported failure.";
                throw new Exception(errMsg);
            }
        } else {
            String errMsg = "HTTP error " + statusCode;
            try {
                LeaveResponse res = gson.fromJson(json, LeaveResponse.class);
                if (res != null && res.message != null) {
                    errMsg = res.message;
                }
            } catch (Exception ignored) {}
            throw new Exception(errMsg);
        }
    }

    /**
     * Converts LOP leaves map to CSV and saves it to the target file path.
     * 
     * @param leaveData The map of Employee ID to LOP Days to export.
     * @param targetFile The file path where the CSV will be saved.
     * @throws IOException If writing fails.
     */
    public static void exportLeavesToCsv(Map<String, Double> leaveData, File targetFile) throws IOException {
        if (leaveData == null) {
            leaveData = new java.util.HashMap<>();
        }

        Utils.LogUtils.info("Exporting {} leave records to CSV file: {}", leaveData.size(), targetFile.getAbsolutePath());

        File parent = targetFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        try (java.io.PrintWriter pw = new java.io.PrintWriter(targetFile)) {
            // Write CSV headers
            pw.println("Employee ID,LOP Days");

            // Write CSV data rows
            for (Map.Entry<String, Double> entry : leaveData.entrySet()) {
                StringBuilder row = new StringBuilder();
                row.append(escapeCsvField(entry.getKey())).append(",");
                row.append(entry.getValue());
                pw.println(row.toString());
            }
        }
    }

    /**
     * Safely escapes fields for standard CSV representation.
     */
    private static String escapeCsvField(String value) {
        if (value == null) {
            return "";
        }
        String val = value.trim();
        if (val.contains(",") || val.contains("\"") || val.contains("\n") || val.contains("\r")) {
            return "\"" + val.replace("\"", "\"\"") + "\"";
        }
        return val;
    }
}
