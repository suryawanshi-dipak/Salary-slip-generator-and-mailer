package Services;

import java.io.File;
import java.io.FileInputStream;
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
 * Service to interact with the HRMS Loan Payroll Export API.
 * It fetches active scheduled loan installments for a target month and writes them to a CSV file.
 */
public class LoanService {

    private static final String CONFIG_FILE = "DATA/smtp.properties";
    private static final String DEFAULT_URL = "http://localhost:5000/api/loans/payroll-export";
    private static final String DEFAULT_KEY = "sk_live_test_payroll_key_9999";

    // --- JSON Mapping Classes for Gson ---
    public static class LoanResponse {
        public boolean success;
        public String month;
        public int count;
        public String message;
        public List<Installment> installments;
    }

    public static class Installment {
        public int installment_id;
        public int loan_id;
        public String employee_id;
        public String employee_name;
        public String employee_email;
        public int installment_no;
        public String due_month;
        public String emi_amount;
        public String status;
        public String loan_reason;
    }

    // --- Properties configuration ---

    /**
     * Reads the Loan API URL from properties or returns the default.
     */
    public static String getApiUrl() {
        Properties props = loadProperties();
        return props.getProperty("loan.api.url", DEFAULT_URL);
    }

    /**
     * Reads the Loan API Key from properties or returns the default.
     */
    public static String getApiKey() {
        Properties props = loadProperties();
        return props.getProperty("loan.api.key", DEFAULT_KEY);
    }

    /**
     * Saves updated Loan API URL and Key to smtp.properties, preserving existing values.
     */
    public static void saveSettings(String url, String key) throws IOException {
        Properties props = loadProperties();
        props.setProperty("loan.api.url", url != null ? url.trim() : DEFAULT_URL);
        props.setProperty("loan.api.key", key != null ? key.trim() : DEFAULT_KEY);
        
        File file = new File(CONFIG_FILE);
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        
        try (FileOutputStream out = new FileOutputStream(file)) {
            props.store(out, "Updated Loan API Settings");
            Utils.LogUtils.info("Saved loan API settings to {}", CONFIG_FILE);
        }
    }

    private static Properties loadProperties() {
        Properties props = new Properties();
        File file = new File(CONFIG_FILE);
        if (file.exists()) {
            try (FileInputStream in = new FileInputStream(file)) {
                props.load(in);
            } catch (IOException e) {
                Utils.LogUtils.warn("Could not load properties from {}: {}", CONFIG_FILE, e.getMessage());
            }
        }
        return props;
    }

    // --- Core API Logic ---

    /**
     * Fetches loan installments from the HRMS API for a given month and API key.
     * 
     * @param apiUrl The base URL of the API endpoint.
     * @param apiKey The API authentication key.
     * @param month The target due month in YYYY-MM format.
     * @return LoanResponse mapped from JSON.
     * @throws Exception If fetching or parsing fails.
     */
    public static LoanResponse fetchLoans(String apiUrl, String apiKey, String month) throws Exception {
        if (month == null || !month.matches("^\\d{4}-\\d{2}$")) {
            throw new IllegalArgumentException("Invalid month format. Expected YYYY-MM.");
        }

        // Build target URI with month parameter
        String fullUrl = apiUrl + (apiUrl.contains("?") ? "&" : "?") + "month=" + month;
        Utils.LogUtils.info("Fetching loan installments from: {}", fullUrl);

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
            LoanResponse res = gson.fromJson(json, LoanResponse.class);
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
                LoanResponse res = gson.fromJson(json, LoanResponse.class);
                if (res != null && res.message != null) {
                    errMsg = res.message;
                }
            } catch (Exception ignored) {}
            throw new Exception(errMsg);
        }
    }

    /**
     * Converts loan installments to CSV and saves it to the target file path.
     * 
     * @param installments The list of installments to export.
     * @param targetFile The file path where the CSV will be saved.
     * @throws IOException If writing fails.
     */
    public static void exportLoansToCsv(List<Installment> installments, File targetFile) throws IOException {
        if (installments == null) {
            installments = new ArrayList<>();
        }

        Utils.LogUtils.info("Exporting {} loan installments to CSV file: {}", installments.size(), targetFile.getAbsolutePath());

        File parent = targetFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        try (PrintWriter pw = new PrintWriter(targetFile)) {
            // Write CSV headers
            pw.println("Installment ID,Loan ID,Employee ID,Employee Name,Email,Installment No,Due Month,EMI Amount,Status,Reason");

            // Write CSV data rows
            for (Installment inst : installments) {
                StringBuilder row = new StringBuilder();
                row.append(inst.installment_id).append(",");
                row.append(inst.loan_id).append(",");
                row.append(escapeCsvField(inst.employee_id)).append(",");
                row.append(escapeCsvField(inst.employee_name)).append(",");
                row.append(escapeCsvField(inst.employee_email)).append(",");
                row.append(inst.installment_no).append(",");
                row.append(escapeCsvField(inst.due_month)).append(",");
                row.append(escapeCsvField(inst.emi_amount)).append(",");
                row.append(escapeCsvField(inst.status)).append(",");
                row.append(escapeCsvField(inst.loan_reason));
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
