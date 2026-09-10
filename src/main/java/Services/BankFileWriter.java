package Services;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.apache.poi.hssf.usermodel.HSSFFormulaEvaluator;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellReference;

import Services.CsvReaderService.EmployeeSalary;

/**
 * Fills the HDFC Enet bulk-salary upload workbook (.xls) from the merged payroll
 * rows.
 *
 * <p>The template has three sheets: <b>Specifications</b> and <b>Sheet1</b> are
 * left exactly as they are; <b>Input Sheet</b> keeps its three header rows and
 * gets one payment row per employee from row 4 down. Column AC ("COPY … FROM …
 * HERE") is the template's own formula that comma-joins A→AB — it is rebuilt for
 * every data row and re-evaluated so the copy-paste text is correct.</p>
 */
public class BankFileWriter {

    private static final String TEMPLATE_NAME = "Enet Bank salary DD.MM.YY-1 Template.xls";
    private static final String INPUT_SHEET = "Input Sheet";

    // 0-based column indices on the Input Sheet
    private static final int COL_TXN_TYPE = 0;   // A
    private static final int COL_BENE_CODE = 1;  // B
    private static final int COL_ACCOUNT = 2;    // C
    private static final int COL_AMOUNT = 3;     // D
    private static final int COL_BENE_NAME = 4;  // E
    private static final int COL_NARRATION = 13; // N  (Customer Reference / Debit narration)
    private static final int COL_VALUE_DATE = 22;// W
    private static final int COL_IFSC = 24;      // Y
    private static final int COL_EMAIL = 27;     // AB
    private static final int COL_COPY = 28;      // AC (formula)
    private static final int FIRST_DATA_ROW = 3; // row 4 (0-based)
    private static final int MAX_TEMPLATE_ROW = 29; // template pre-fills the formula through row 30

    private BankFileWriter() {
    }

    /**
     * @param employees   payroll rows already merged with HRMS data
     * @param outputDir    the month's SalarySlips folder
     * @param monthMmmYy   the payroll month, e.g. {@code Aug-26}
     * @return the written .xls file
     * @throws IOException if the template is missing or the file cannot be written
     */
    public static File write(List<EmployeeSalary> employees, File outputDir, String monthMmmYy) throws IOException {
        File template = resolveTemplate();
        if (template == null) {
            throw new IOException("Bank template not found: DATA/" + TEMPLATE_NAME);
        }
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }

        String narration = "SALARY FOR " + (monthMmmYy == null ? "" : monthMmmYy.replace("-", " "));
        String valueDate = LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));

        try (FileInputStream in = new FileInputStream(template);
             HSSFWorkbook wb = new HSSFWorkbook(in)) {

            Sheet sheet = wb.getSheet(INPUT_SHEET);
            if (sheet == null) {
                throw new IOException("Template has no '" + INPUT_SHEET + "' sheet");
            }

            int rowIdx = FIRST_DATA_ROW;
            int written = 0;
            for (EmployeeSalary e : employees) {
                if (isBlank(e.eCode)) {
                    continue;
                }
                if (isBlank(e.bankAccountNo)) {
                    Utils.LogUtils.warn("Bank file: skipping {} - no bank account number", e.eCode.trim());
                    continue;
                }
                long netPay = parseAmount(e.netPay);
                if (netPay <= 0) {
                    Utils.LogUtils.warn("Bank file: skipping {} - net pay is {}", e.eCode.trim(), e.netPay);
                    continue;
                }

                Row row = sheet.getRow(rowIdx);
                if (row == null) {
                    row = sheet.createRow(rowIdx);
                }
                setString(row, COL_TXN_TYPE, "I");
                setNumber(row, COL_BENE_CODE, written + 1);
                setString(row, COL_ACCOUNT, e.bankAccountNo.trim());
                setNumber(row, COL_AMOUNT, netPay);
                setString(row, COL_BENE_NAME, trim(e.name, 40));
                setString(row, COL_NARRATION, narration);
                setString(row, COL_VALUE_DATE, valueDate);
                setString(row, COL_IFSC, safe(e.ifscCode));
                setString(row, COL_EMAIL, safe(e.email));
                setCopyFormula(row, rowIdx + 1); // 1-based row number for the formula

                rowIdx++;
                written++;
            }

            // Drop the template's leftover pre-filled formula rows below the data.
            for (int r = rowIdx; r <= MAX_TEMPLATE_ROW; r++) {
                Row row = sheet.getRow(r);
                if (row != null) {
                    sheet.removeRow(row);
                }
            }

            // Refresh the AC copy-paste text and flag a full recalc on open.
            HSSFFormulaEvaluator.evaluateAllFormulaCells(wb);
            wb.setForceFormulaRecalculation(true);

            File out = new File(outputDir, "Enet Bank salary "
                    + LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yy")) + ".xls");
            try (FileOutputStream fos = new FileOutputStream(out)) {
                wb.write(fos);
            }
            Utils.LogUtils.info("Bank upload file written: {} ({} payment rows)", out.getAbsolutePath(), written);
            return out;
        }
    }

    private static void setCopyFormula(Row row, int rowNum1Based) {
        StringBuilder f = new StringBuilder();
        for (int c = 0; c < COL_COPY; c++) { // A .. AB
            if (c > 0) {
                f.append("&\",\"&");
            }
            f.append(CellReference.convertNumToColString(c)).append(rowNum1Based);
        }
        cell(row, COL_COPY).setCellFormula(f.toString());
    }

    private static File resolveTemplate() {
        String configured = ConfigService.getBankTemplatePath();
        if (configured != null && !configured.isEmpty()) {
            File f = new File(configured);
            if (f.isFile()) {
                return f;
            }
            Utils.LogUtils.warn("Configured bank template not found, falling back to bundled: {}", configured);
        }
        String[] candidates = {
                "DATA/" + TEMPLATE_NAME,
                TEMPLATE_NAME,
                "Salary-slip-generator-and-mailer/DATA/" + TEMPLATE_NAME
        };
        for (String p : candidates) {
            File f = new File(p);
            if (f.isFile()) {
                return f;
            }
        }
        return null;
    }

    private static Cell cell(Row row, int col) {
        Cell c = row.getCell(col);
        return c != null ? c : row.createCell(col);
    }

    private static void setString(Row row, int col, String value) {
        cell(row, col).setCellValue(value == null ? "" : value);
    }

    private static void setNumber(Row row, int col, double value) {
        cell(row, col).setCellValue(value);
    }

    private static long parseAmount(String s) {
        try {
            return Math.round(Double.parseDouble(s.trim()));
        } catch (Exception e) {
            return 0;
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static String safe(String s) {
        return s == null ? "" : s.trim();
    }

    private static String trim(String s, int max) {
        String v = safe(s);
        return v.length() <= max ? v : v.substring(0, max);
    }
}
