package Services;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import Services.CsvReaderService.EmployeeSalary;

/**
 * Writes the consolidated monthly payroll sheet: the Master CTC columns merged
 * with the HRMS-derived figures (Leaves Availed, Loan Deducted) and the
 * recomputed Net Salary / Total Deduction / Net Pay.
 *
 * <p>Produced by "Generate Slips" <b>after</b> the PDF slips, into the same
 * {@code ~/SalarySlips/<Mon-yy>/} folder, as {@code Salary_<mon>_<yyyy>.csv}.</p>
 */
public class PayrollSheetWriter {

    private static final String[] HEADERS = {
            "Month", "Sr.No.", "E.Code", "Name", "DOJ",
            "Total Basic", "Total HRA", "Total Spl. Allowance", "Total KRA", "Gross Salary",
            "Leaves Availed", "Month Days", "Days Worked",
            "Basic", "HRA", "Spl. Allowance", "KRA",
            "Performance Bonus", "Office Expense", "Leave Payment", "Net Salary",
            "PT", "Loan Deducted", "TDS", "Total Deduction", "Net Pay",
            "Email", "Designation", "Bank Name", "Bank A/c No."
    };

    private PayrollSheetWriter() {
    }

    /**
     * @param employees   rows already merged with HRMS data (post applyHrmsData / CLI merge)
     * @param outputDir   the month's SalarySlips folder
     * @param monthMmmYy  the payroll month, e.g. {@code Aug-26}
     * @return the written CSV file
     * @throws IOException if the file cannot be written
     */
    public static File write(List<EmployeeSalary> employees, File outputDir, String monthMmmYy) throws IOException {
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }

        File file = new File(outputDir, buildFileName(monthMmmYy));
        try (PrintWriter pw = new PrintWriter(file, StandardCharsets.UTF_8)) {
            pw.println(String.join(",", HEADERS));

            int sr = 1;
            for (EmployeeSalary e : employees) {
                if (e.eCode == null || e.eCode.trim().isEmpty()) {
                    continue;
                }
                String[] row = {
                        monthMmmYy,
                        (e.srNo == null || e.srNo.trim().isEmpty()) ? String.valueOf(sr) : e.srNo,
                        e.eCode, e.name, e.doj,
                        e.totalBasic, e.totalHra, e.totalSplAllowance, e.totalKra, e.grossSalary,
                        e.leavesAvailed, e.monthDays, e.daysWorked,
                        e.basic, e.hra, e.splAllowance, e.kra,
                        e.performanceBonus, e.officeExpense, e.leavePayment, e.netSalary,
                        e.pt, e.loanDeducted, e.tds, e.totalDeduction, e.netPay,
                        e.email, e.designation, e.bankName, e.bankAccountNo
                };
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < row.length; i++) {
                    if (i > 0) {
                        sb.append(',');
                    }
                    sb.append(escape(row[i]));
                }
                pw.println(sb.toString());
                sr++;
            }
        }
        Utils.LogUtils.info("Payroll sheet written: {}", file.getAbsolutePath());
        return file;
    }

    private static String buildFileName(String monthMmmYy) {
        try {
            YearMonth ym = YearMonth.parse(monthMmmYy,
                    DateTimeFormatter.ofPattern("MMM-yy", Locale.ENGLISH));
            String mon = ym.format(DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH))
                    .toLowerCase(Locale.ENGLISH);
            return "Salary_" + mon + "_" + ym.getYear() + ".csv";
        } catch (Exception ex) {
            return "Salary_" + (monthMmmYy == null ? "unknown" : monthMmmYy.replace("-", "_")) + ".csv";
        }
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        String s = value.trim();
        if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }
}
