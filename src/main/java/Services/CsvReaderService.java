package Services;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Service to parse uploaded CSV payroll files and format them for the UI Table.
 */
public class CsvReaderService {

    /**
     * Wrapper class to hold both the parsed table data and any validation errors found.
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
     * Parses the given CSV file into a format suitable for the SalarySlipGenerator table model,
     * and performs basic data validation.
     * 
     * @param filePath The absolute path to the CSV file
     * @return A CsvParseResult containing the 2D Object array and a list of validation errors.
     * @throws IOException If there is an issue reading the file
     */
    public static CsvParseResult parsePayrollCsv(String filePath) throws IOException {
        // List to hold the dynamically parsed rows before converting them to a 2D array
        List<Object[]> rows = new ArrayList<>();
        List<String[]> rawRows = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        
        // Open the file using a BufferedReader for efficient line-by-line reading
        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line;
            boolean isFirstLine = true; // Flag to track and skip the CSV header row
            int rowNum = 0;
            
            // Loop through the file until there are no more lines
            while ((line = br.readLine()) != null) {
                rowNum++;
                // ----------------------------------------------------
                // 1. Data Cleaning & Validation
                // ----------------------------------------------------
                // Skip empty lines to prevent IndexOutOfBounds exceptions
                if (line.trim().isEmpty()) {
                    continue;
                }
                
                // Skip the very first row (header row containing column titles)
                if (isFirstLine) {
                    isFirstLine = false;
                    continue;
                }
                
                // ----------------------------------------------------
                // 2. Data Extraction & Validation
                // ----------------------------------------------------
                // Split the comma-separated line into an array of string columns
                String[] cols = line.split(",");
                rawRows.add(cols); // Store the full raw data for PDF generation later
                
                String empId = cols.length > 1 ? cols[1].trim() : "";
                String name = cols.length > 2 ? cols[2].trim() : "";
                String rawBasic = cols.length > 4 ? cols[4].trim() : "";
                String rawNet = cols.length > 19 ? cols[19].trim() : "";
                String email = cols.length > 21 ? cols[21].trim() : "";
                
                // Validation checks
                if (empId.isEmpty()) {
                    errors.add("Row " + rowNum + ": Missing or blank Employee ID.");
                    empId = "Unknown";
                }
                if (name.isEmpty()) {
                    errors.add("Row " + rowNum + ": Missing or blank Employee Name.");
                    name = "Unknown";
                }
                if (rawBasic.isEmpty()) {
                    errors.add("Row " + rowNum + ": Missing Basic Salary.");
                    rawBasic = "0";
                }
                if (rawNet.isEmpty()) {
                    errors.add("Row " + rowNum + ": Missing Net Salary.");
                    rawNet = "0";
                }
                if (email.isEmpty() || !email.contains("@")) {
                    errors.add("Row " + rowNum + ": Missing or invalid Email for " + name + " (Will be flagged as unsendable).");
                }
                
                // Formatting salary fields to include the Rupee (₹) symbol using Unicode escape to prevent encoding issues
                String basicSalary = "\u20B9" + rawBasic;
                String netSalary = "\u20B9" + rawNet;
                
                // ----------------------------------------------------
                // 3. Default Values & Mappings
                // ----------------------------------------------------
                String department = "General";   // Default department
                String month = "June 2026";      // Default placeholder month
                String slipStatus = "Pending";   // Default slip generation status
                String mailStatus = "Pending";   // Default email delivery status
                String action = "";              // Placeholder for the Action Buttons column
                
                // ----------------------------------------------------
                // 4. Row Assembly
                // ----------------------------------------------------
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
        }
        
        return new CsvParseResult(rows.toArray(new Object[0][]), rawRows, errors);
    }
}
