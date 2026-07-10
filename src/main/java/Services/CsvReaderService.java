package Services;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


/**
 * ============================================================================
 * PROJECT UNDERSTANDING - CsvReaderService
 * ============================================================================
 * ROLE:
 * This service acts as the data ingestion layer for the Salary Slip Generator.
 * It is responsible for parsing payroll CSV files, cleaning and validating the raw data,
 * formatting metrics for display in the JTable, and flagging validation errors.
 *
 * HOW IT FITS IN:
 * - GUI (SalarySlipGenerator) calls this service when the user uploads a CSV file.
 * - The parsed rows are loaded into the GUI's table model.
 * - The raw rows are saved in-memory and subsequently passed to PdfUtil for PDF generation.
 * - It contains a global `isLoggingEnabled` flag connected to the UI's Log Toggle.
 * ============================================================================
 */
public class CsvReaderService {

    // Global toggle for enabling/disabling CSV parsing logs from the UI
    public static boolean isLoggingEnabled = true;

    /**
     * Wrapper class to hold both the parsed table data and any validation errors
     * found.
     */
    public static class CsvParseResult {
        public Object[][] rows;
        public List<String[]> rawRows;
        public List<String> errors;

        public CsvParseResult(Object[][] rows, List<String[]> rawRows, List<String> errors) {
            this.rows = rows;
            this.rawRows = rawRows;
            this.errors = errors;
        }
    }

    /**
     * Parses the given CSV file into a format suitable for the SalarySlipGenerator
     * table model,
     * and performs basic data validation.
     * 
     * @param filePath The absolute path to the CSV file
     * @return A CsvParseResult containing the 2D Object array and a list of
     *         validation errors.
     * @throws IOException If there is an issue reading the file
     */
    public static CsvParseResult parsePayrollCsv(String filePath) throws IOException {
        // INFO level: Routine operational tracking
        Utils.LogUtils.info("Starting CSV parsing for file: {}", filePath);

        List<Object[]> rows = new ArrayList<>();
        List<String[]> rawRows = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line;
            boolean isFirstLine = true;
            int rowNum = 0;

            while ((line = br.readLine()) != null) {
                rowNum++;

                if (line.trim().isEmpty()) {
                    continue;
                }

                if (isFirstLine) {
                    isFirstLine = false;
                    continue;
                }

                String[] cols = line.split(",");
                rawRows.add(cols);

                String empId = cols.length > 1 ? cols[1].trim() : "";
                String name = cols.length > 2 ? cols[2].trim() : "";
                String rawBasic = cols.length > 4 ? cols[4].trim() : "";
                String rawNet = cols.length > 19 ? cols[19].trim() : "";
                String email = cols.length > 21 ? cols[21].trim() : "";

                // WARN level: Bad user input or soft data issues that don't crash the app
                if (empId.isEmpty()) {
                    String err = "Row " + rowNum + ": Missing or blank Employee ID.";
                    errors.add(err);
                    Utils.LogUtils.warn("CSV Parse Warning - {}", err);
                    empId = "Unknown";
                }
                if (name.isEmpty()) {
                    String err = "Row " + rowNum + ": Missing or blank Employee Name.";
                    errors.add(err);
                    Utils.LogUtils.warn("CSV Parse Warning - {}", err);
                    name = "Unknown";
                }
                if (rawBasic.isEmpty()) {
                    String err = "Row " + rowNum + ": Missing Basic Salary.";
                    errors.add(err);
                    Utils.LogUtils.warn("CSV Parse Warning - {}", err);
                    rawBasic = "0";
                }
                if (rawNet.isEmpty()) {
                    String err = "Row " + rowNum + ": Missing Net Salary.";
                    errors.add(err);
                    Utils.LogUtils.warn("CSV Parse Warning - {}", err);
                    rawNet = "0";
                }
                if (email.isEmpty() || !email.contains("@")) {
                    String err = "Row " + rowNum + ": Missing or invalid Email for " + name
                            + " (Will be flagged as unsendable).";
                    errors.add(err);
                    Utils.LogUtils.warn("CSV Parse Warning - {}", err);
                }

                String basicSalary = "\u20B9" + rawBasic;
                String netSalary = "\u20B9" + rawNet;

                String department = "General";
                String month = "June 2026";
                String slipStatus = "Pending";
                String mailStatus = "Pending";
                String action = "";

                Object[] rowData = new Object[] {
                        empId,
                        name,
                        department,
                        basicSalary,
                        netSalary,
                        month,
                        slipStatus,
                        mailStatus,
                        action
                };

                rows.add(rowData);
            }
            Utils.LogUtils.info("Finished parsing CSV file: {}. Loaded {} rows successfully. Validation errors found: {}",
                    filePath, rows.size(), errors.size());

        } catch (IOException e) {
            // ERROR level: Fatal system failures or file loading blockages
            Utils.LogUtils.error("Error reading CSV file {}: {}", filePath, e.getMessage(), e);
            throw e;
        }

        return new CsvParseResult(rows.toArray(new Object[0][]), rawRows, errors);
    }
}
