package Services;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
 * - The parsed rows are saved in-memory as EmployeeSalary objects and subsequently 
 *   passed to PdfUtil for PDF generation.
 * - It contains a global `isLoggingEnabled` flag connected to the UI's Log Toggle.
 * ============================================================================
 */
public class CsvReaderService {

    public static boolean isLoggingEnabled = true;

    public static class EmployeeSalary {
        public String month;
        public String srNo;
        public String eCode;
        public String name;
        public String doj;
        public String totalBasic;
        public String totalHra;
        public String totalSplAllowance;
        public String totalKra;
        public String grossSalary;
        public String leavesAvailed;
        public String monthDays;
        public String daysWorked;
        public String basic;
        public String hra;
        public String splAllowance;
        public String kra;
        public String netSalary;
        public String pt;
        public String loanDeducted;
        public String tds;
        public String totalDeduction;
        public String netPay;
        public String email;
        public String designation;
        public String bankName;
        public String bankAccountNo;
        public String performanceBonus;
        public String officeExpense;
        public String leavePayment;

        public String maskedBankAccountNo() {
            if (bankAccountNo == null || bankAccountNo.trim().isEmpty()) {
                return "";
            }
            String raw = bankAccountNo.trim();
            for (char c : raw.toCharArray()) {
                if (!Character.isDigit(c)) {
                    return raw; // Return unchanged if contains non-digit
                }
            }
            if (raw.length() <= 4) {
                return raw;
            }
            StringBuilder masked = new StringBuilder();
            for (int i = 0; i < raw.length() - 4; i++) {
                masked.append('X');
            }
            masked.append(raw.substring(raw.length() - 4));
            return masked.toString();
        }
    }

    /**
     * Wrapper class to hold both the parsed table data and any validation errors
     * found.
     */
    public static class CsvParseResult {
        public Object[][] rows;
        public List<EmployeeSalary> employees;
        public List<String> errors;

        public CsvParseResult(Object[][] rows, List<EmployeeSalary> employees, List<String> errors) {
            this.rows = rows;
            this.employees = employees;
            this.errors = errors;
        }
    }

    /**
     * Parses a single CSV line into a list of string tokens, respecting fields
     * enclosed in double quotes. This prevents embedded commas (e.g., inside an address
     * or formatted currency) from incorrectly splitting the data into separate columns.
     * 
     * @param line The raw line read from the CSV file
     * @return A list of extracted column values
     */
    private static List<String> parseCsvLine(String line) {
        List<String> result = new ArrayList<>();
        if (line == null || line.isEmpty()) return result;
        
        StringBuilder currentToken = new StringBuilder();
        boolean inQuotes = false;
        
        // Loop through each character to handle quotes properly
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                // If we encounter a double quote, check for escaped quotes ("")
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    currentToken.append('"');
                    i++; // Skip the escaped quote
                } else {
                    inQuotes = !inQuotes; // Toggle quote state
                }
            } else if (c == ',' && !inQuotes) {
                // If we see a comma and we are NOT inside quotes, it's a column separator
                result.add(currentToken.toString());
                currentToken.setLength(0);
            } else {
                // Otherwise, it's just a normal character inside the column
                currentToken.append(c);
            }
        }
        // Add the final column parsed after the last comma
        result.add(currentToken.toString());
        return result;
    }

    /**
     * Safely converts a String value to an integer, defaulting to 0 if the string
     * is null, empty, or unparseable. This prevents formatting errors from crashing
     * the ingestion process.
     */
    private static int parseInteger(String val) {
        if (val == null || val.trim().isEmpty()) return 0;
        try {
            return Integer.parseInt(val.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Parses the given CSV file into a format suitable for the SalarySlipGenerator
     * table model, performs data validation, maps columns by header name, and 
     * applies 4-identity reconciliation checks.
     * 
     * @param filePath The absolute path to the CSV file
     * @return A CsvParseResult containing the 2D Object array, a list of parsed 
     *         EmployeeSalary objects, and a list of validation errors.
     * @throws IOException If there is an issue reading the file
     */
    public static CsvParseResult parsePayrollCsv(String filePath) throws IOException {
        List<Object[]> rows = new ArrayList<>();
        List<EmployeeSalary> employees = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        String[] expectedHeaders = {
            "month", "sr.no.", "e.code", "name", "doj", "total basic", "total hra", "total spl. allowance", 
            "total kra", "gross salary", "leaves availed", "month days", "days worked", "basic", "hra", 
            "spl. allowance", "kra", "net salary", "pt", "loan deducted", "tds", "total deduction", "net pay", 
            "email", "designation", "bank name", "bank a/c no.", "performance bonus", "office expense", "leave payment"
        };

        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line;
            int rowNum = 0;
            
            Map<String, Integer> columnMap = new HashMap<>();
            String fileMonth = null;

            while ((line = br.readLine()) != null) {
                rowNum++;
                if (line.trim().isEmpty()) {
                    continue;
                }

                List<String> cols = parseCsvLine(line);

                // If columnMap is empty, this is the very first row (the header row)
                if (columnMap.isEmpty()) { 
                    // Map every column header strictly by its lower-cased name
                    for (int i = 0; i < cols.size(); i++) {
                        String header = cols.get(i).trim().toLowerCase();
                        columnMap.put(header, i);
                    }
                    
                    // Verify that our map contains all 30 expected headers
                    List<String> missingHeaders = new ArrayList<>();
                    for (String expected : expectedHeaders) {
                        if (!columnMap.containsKey(expected)) {
                            missingHeaders.add(expected);
                        }
                    }
                    
                    // If any expected column is missing, fail immediately to prevent data corruption
                    if (!missingHeaders.isEmpty()) {
                        throw new IllegalArgumentException("Missing or unexpected columns: " + String.join(", ", missingHeaders));
                    }
                    continue; // Skip processing this row further, as it's just headers
                }
                
                // At this point, we are processing a data row.
                // We use safeGet to pull the data dynamically by header name rather than hardcoded array index.
                String eCodeVal = safeGet(cols, columnMap, "e.code");
                if (eCodeVal == null || eCodeVal.isEmpty()) {
                    continue; // Skip non-employee rows as an insurance policy
                }

                // Populate the object properties by extracting columns dynamically via the columnMap
                EmployeeSalary emp = new EmployeeSalary();
                emp.month = safeGet(cols, columnMap, "month");
                emp.srNo = safeGet(cols, columnMap, "sr.no.");
                emp.eCode = eCodeVal;
                emp.name = safeGet(cols, columnMap, "name");
                emp.doj = safeGet(cols, columnMap, "doj");
                emp.totalBasic = safeGet(cols, columnMap, "total basic");
                emp.totalHra = safeGet(cols, columnMap, "total hra");
                emp.totalSplAllowance = safeGet(cols, columnMap, "total spl. allowance");
                emp.totalKra = safeGet(cols, columnMap, "total kra");
                emp.grossSalary = safeGet(cols, columnMap, "gross salary");
                emp.leavesAvailed = safeGet(cols, columnMap, "leaves availed");
                emp.monthDays = safeGet(cols, columnMap, "month days");
                emp.daysWorked = safeGet(cols, columnMap, "days worked");
                emp.basic = safeGet(cols, columnMap, "basic");
                emp.hra = safeGet(cols, columnMap, "hra");
                emp.splAllowance = safeGet(cols, columnMap, "spl. allowance");
                emp.kra = safeGet(cols, columnMap, "kra");
                emp.netSalary = safeGet(cols, columnMap, "net salary");
                emp.pt = safeGet(cols, columnMap, "pt");
                emp.loanDeducted = safeGet(cols, columnMap, "loan deducted");
                emp.tds = safeGet(cols, columnMap, "tds");
                emp.totalDeduction = safeGet(cols, columnMap, "total deduction");
                emp.netPay = safeGet(cols, columnMap, "net pay");
                emp.email = safeGet(cols, columnMap, "email");
                emp.designation = safeGet(cols, columnMap, "designation");
                emp.bankName = safeGet(cols, columnMap, "bank name");
                emp.bankAccountNo = safeGet(cols, columnMap, "bank a/c no.");
                emp.performanceBonus = safeGet(cols, columnMap, "performance bonus");
                emp.officeExpense = safeGet(cols, columnMap, "office expense");
                emp.leavePayment = safeGet(cols, columnMap, "leave payment");

                // --- Month Consistency Check (FR-19) ---
                // Validates that every row in the file belongs to the same pay period.
                // Mixed months indicate human error during CSV compilation.
                if (fileMonth == null) {
                    fileMonth = emp.month;
                } else if (!fileMonth.equals(emp.month)) {
                    throw new IllegalArgumentException("Conflicting run month on row " + rowNum + ": expected " + fileMonth + " but found " + emp.month);
                }
                
                // Validate presence of essential fields
                if (emp.eCode.isEmpty()) {
                    String err = "Row " + rowNum + ": Missing Employee ID.";
                    errors.add(err);
                    Utils.LogUtils.warn("CSV Parse Warning - {}", err);
                }
                if (emp.email.isEmpty() || !emp.email.contains("@")) {
                    String err = "Row " + rowNum + ": Missing or invalid Email for E.Code " + emp.eCode;
                    errors.add(err);
                    Utils.LogUtils.warn("CSV Parse Warning - {}", err);
                }
                if (emp.designation.isEmpty()) {
                    String err = "Row " + rowNum + ": Missing Designation for E.Code " + emp.eCode;
                    errors.add(err);
                    Utils.LogUtils.warn("CSV Parse Warning - {}", err);
                }

                // Reconciliation check
                int totalBasic = parseInteger(emp.totalBasic);
                int totalHra = parseInteger(emp.totalHra);
                int totalSplAllowance = parseInteger(emp.totalSplAllowance);
                int totalKra = parseInteger(emp.totalKra);
                int grossSalary = parseInteger(emp.grossSalary);
                
                int basic = parseInteger(emp.basic);
                int hra = parseInteger(emp.hra);
                int splAllowance = parseInteger(emp.splAllowance);
                int kra = parseInteger(emp.kra);
                int performanceBonus = parseInteger(emp.performanceBonus);
                int officeExpense = parseInteger(emp.officeExpense);
                int leavePayment = parseInteger(emp.leavePayment);
                int netSalary = parseInteger(emp.netSalary);
                
                int pt = parseInteger(emp.pt);
                int loanDeducted = parseInteger(emp.loanDeducted);
                int tds = parseInteger(emp.tds);
                int totalDeduction = parseInteger(emp.totalDeduction);
                
                int netPay = parseInteger(emp.netPay);
                
                // --- 4 Identities Arithmetic Reconciliation (FR-03 & FR-22) ---
                // 1. Gross Salary must equal the sum of all Fixed/Contractual components
                if (grossSalary != (totalBasic + totalHra + totalSplAllowance + totalKra)) {
                    throw new IllegalArgumentException("Gross Salary reconciliation failed on row " + rowNum + " for E.Code " + emp.eCode);
                }
                
                // 2. Net Salary (Payable Earnings) must equal the sum of earned base components plus any variable bonuses.
                // Note: Net Salary > Gross Salary is NOT flagged as an error to permit performance bonuses.
                if (netSalary != (basic + hra + splAllowance + kra + performanceBonus + officeExpense + leavePayment)) {
                    throw new IllegalArgumentException("Net Salary reconciliation failed on row " + rowNum + " for E.Code " + emp.eCode);
                }
                
                // 3. Total Deduction must be the exact sum of all isolated deduction elements.
                if (totalDeduction != (pt + loanDeducted + tds)) {
                    throw new IllegalArgumentException("Total Deduction reconciliation failed on row " + rowNum + " for E.Code " + emp.eCode);
                }
                
                // 4. Final Net Pay must exactly match Payable Earnings minus Deductions.
                if (netPay != (netSalary - totalDeduction)) {
                    throw new IllegalArgumentException("Net Pay reconciliation failed on row " + rowNum + " for E.Code " + emp.eCode);
                }

                // --- UI Display Data Preparation ---
                String basicSalaryUI = "\u20B9" + parseInteger(emp.totalBasic);
                String netSalaryUI = "\u20B9" + netSalary;
                String slipStatus = "Pending";
                String mailStatus = "Pending";
                String action = "";

                Object[] rowData = new Object[] {
                        emp.eCode,
                        emp.name,
                        emp.designation, 
                        basicSalaryUI,
                        netSalaryUI,
                        emp.month,
                        slipStatus,
                        mailStatus,
                        action
                };
                
                rows.add(rowData);
                employees.add(emp);
                
                // Logging only non-PII values
                Utils.LogUtils.info("Successfully processed row {} for E.Code: {}", rowNum, emp.eCode);
            }

        } catch (IOException e) {
            Utils.LogUtils.error("Error reading CSV file {}: {}", filePath, e.getMessage(), e);
            throw e;
        }
        
        return new CsvParseResult(rows.toArray(new Object[0][]), employees, errors);
    }
    
    /**
     * Safely retrieves a column value based on the column name mapped in columnMap.
     * Prevents IndexOutOfBoundsExceptions if a field is entirely missing from the line string.
     */
    private static String safeGet(List<String> cols, Map<String, Integer> columnMap, String header) {
        Integer idx = columnMap.get(header);
        if (idx != null && idx < cols.size()) {
            return cols.get(idx).trim();
        }
        return "";
    }
}
