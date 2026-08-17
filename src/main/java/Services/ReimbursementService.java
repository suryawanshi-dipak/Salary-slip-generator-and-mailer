package Services;

import java.io.File;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import com.google.gson.Gson;

/**
 * Service to interact with the HRMS Reimbursement Export API.
 * It fetches approved reimbursement data for a target month and writes it to a CSV file.
 */
public class ReimbursementService {

    private static final String CONFIG_FILE = new File("Salary-slip-generator-and-mailer/DATA/smtp.properties").exists() ? "Salary-slip-generator-and-mailer/DATA/smtp.properties" : "DATA/smtp.properties";
    private static final String DEFAULT_URL = "http://localhost:5000/api/reimbursements/payroll-export";
    private static final String DEFAULT_KEY = "sk_live_test_payroll_key_9999";

    // --- JSON Mapping Classes for Gson ---
    public static class ReimbursementResponse {
        public boolean success;
        public String month;
        public int count;
        public String message;
        public List<Claim> claims;
    }

    public static class Claim {
        public int claim_id;
        public String employee_id;
        public String employee_name;
        public String employee_email;
        public String amount;
        public String currency;
        public String reason;
        public String expense_date;
        public String payout_month;
        public String status;
    }

    // --- Properties configuration ---

    /**
     * Reads the Reimbursement API URL from properties or returns the default.
     */
    public static String getApiUrl() {
        Properties props = loadProperties();
        return props.getProperty("reimbursement.api.url", DEFAULT_URL);
    }

    /**
     * Reads the Reimbursement API Key from properties or returns the default.
     */
    public static String getApiKey() {
        Properties props = loadProperties();
        return props.getProperty("reimbursement.api.key", DEFAULT_KEY);
    }

    /**
     * Saves updated Reimbursement API URL and Key to smtp.properties, preserving existing values.
     */
    public static void saveSettings(String url, String key) throws IOException {
        Properties props = loadProperties();
        props.setProperty("reimbursement.api.url", url != null ? url.trim() : DEFAULT_URL);
        props.setProperty("reimbursement.api.key", key != null ? key.trim() : DEFAULT_KEY);
        
        File file = new File(CONFIG_FILE);
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        
        try (FileOutputStream out = new FileOutputStream(file)) {
            props.store(out, "Updated Reimbursement API Settings");
            Utils.LogUtils.info("Saved reimbursement API settings to {}", CONFIG_FILE);
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

    // --- Core API Logic ---

    /**
     * Fetches claims from the HRMS API for a given month and API key.
     * 
     * @param apiUrl The base URL of the API endpoint.
     * @param apiKey The API authentication key.
     * @param month The target payout month in YYYY-MM format.
     * @return ReimbursementResponse mapped from JSON.
     * @throws Exception If fetching or parsing fails.
     */
    public static ReimbursementResponse fetchClaims(String apiUrl, String apiKey, String month) throws Exception {
        if (month == null || !month.matches("^\\d{4}-\\d{2}$")) {
            throw new IllegalArgumentException("Invalid month format. Expected YYYY-MM.");
        }

        // Build target URI with month parameter
        String fullUrl = apiUrl + (apiUrl.contains("?") ? "&" : "?") + "month=" + month;
        Utils.LogUtils.info("Fetching reimbursement claims from: {}", fullUrl);

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

        Utils.LogUtils.info("API Response received. Status code: {}", statusCode);

        Gson gson = new Gson();
        if (statusCode == 200) {
            ReimbursementResponse res = gson.fromJson(json, ReimbursementResponse.class);
            if (res != null && res.success) {
                return res;
            } else {
                String errMsg = (res != null && res.message != null) ? res.message : "API reported failure.";
                throw new Exception(errMsg);
            }
        } else {
            // Attempt to parse message from body
            String errMsg = "HTTP error " + statusCode;
            try {
                ReimbursementResponse res = gson.fromJson(json, ReimbursementResponse.class);
                if (res != null && res.message != null) {
                    errMsg = res.message;
                }
            } catch (Exception ignored) {}
            throw new Exception(errMsg);
        }
    }

    /**
     * Converts reimbursement claims to CSV and saves it to the target file path.
     * 
     * @param claims The list of claims to export.
     * @param targetFile The file path where the CSV will be saved.
     * @throws IOException If writing fails.
     */
    public static void exportClaimsToCsv(List<Claim> claims, File targetFile) throws IOException {
        if (claims == null) {
            claims = new ArrayList<>();
        }

        Utils.LogUtils.info("Exporting {} claims to CSV file: {}", claims.size(), targetFile.getAbsolutePath());

        File parent = targetFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        try (PrintWriter pw = new PrintWriter(targetFile)) {
            // Write CSV headers
            pw.println("Claim ID,Employee ID,Employee Name,Email,Amount,Currency,Reason,Expense Date,Payout Month");

            // Write CSV data rows
            for (Claim claim : claims) {
                StringBuilder row = new StringBuilder();
                row.append(claim.claim_id).append(",");
                row.append(escapeCsvField(claim.employee_id)).append(",");
                row.append(escapeCsvField(claim.employee_name)).append(",");
                row.append(escapeCsvField(claim.employee_email)).append(",");
                row.append(escapeCsvField(claim.amount)).append(",");
                row.append(escapeCsvField(claim.currency)).append(",");
                row.append(escapeCsvField(claim.reason)).append(",");
                row.append(escapeCsvField(claim.expense_date)).append(",");
                row.append(escapeCsvField(claim.payout_month));
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
