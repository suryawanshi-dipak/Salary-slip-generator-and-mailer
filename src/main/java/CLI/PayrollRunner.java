package CLI;

import java.io.File;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import Services.CsvReaderService;
import Services.CsvReaderService.CsvParseResult;
import Services.CsvReaderService.EmployeeSalary;
import Services.LeaveService;
import Services.LoanService;
import Services.ReimbursementService;
import Utils.PdfUtil;

/**
 * Headless payroll runner: parses a payroll CSV, calls the HRMS Leave / Loan /
 * Reimbursement payroll-export APIs, merges the results into each employee row,
 * recalculates net pay, and writes the salary-slip PDFs to disk.
 *
 * <p>This mirrors {@code SalarySlipGenerator.loadCsvData(...)} + the "Generate
 * Slips" button, without the Swing UI, so payroll can be produced/scripted from
 * the command line.</p>
 *
 * <pre>
 *   java -cp target/salary-slip-generator.jar CLI.PayrollRunner DATA/Master_CTC.csv 2026-08
 * </pre>
 */
public class PayrollRunner {

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: java -cp <jar> CLI.PayrollRunner <master_ctc.csv> <YYYY-MM>");
            System.exit(2);
        }
        String csvPath = args[0];
        String apiMonth = args[1];
        if (!apiMonth.matches("^\\d{4}-\\d{2}$")) {
            System.err.println("Month must be YYYY-MM, e.g. 2026-08");
            System.exit(2);
        }
        YearMonth runMonth = YearMonth.parse(apiMonth);
        DateTimeFormatter mmmYY = DateTimeFormatter.ofPattern("MMM-yy", Locale.ENGLISH);
        String fileMonth = runMonth.format(mmmYY); // e.g. "Aug-26"

        CsvParseResult result = CsvReaderService.parsePayrollCsv(csvPath, fileMonth);
        if (result.employees.isEmpty()) {
            System.err.println("No employee rows parsed from " + csvPath);
            System.exit(1);
        }

        System.out.println("Payroll month        : " + fileMonth);
        System.out.println("API month           : " + apiMonth);
        System.out.println("Employees parsed     : " + result.employees.size());
        System.out.println("CSV validation errors: " + result.errors.size());
        for (CsvReaderService.CsvError e : result.errors) {
            System.out.println("   ! " + e.eCode + " " + e.name + " -> " + e.reason);
        }

        // ---- 1. Call the three HRMS payroll-export APIs -------------------------
        LoanService.LoanResponse loanRes = null;
        try {
            loanRes = LoanService.fetchLoans(LoanService.getApiUrl(), LoanService.getApiKey(), apiMonth);
            System.out.println("[HRMS] loans           : " + (loanRes.installments == null ? 0 : loanRes.installments.size()) + " installment(s)");
        } catch (Exception e) {
            System.out.println("[HRMS] loans           : FAILED - " + e.getMessage());
        }

        ReimbursementService.ReimbursementResponse reimbRes = null;
        try {
            reimbRes = ReimbursementService.fetchClaims(ReimbursementService.getApiUrl(), ReimbursementService.getApiKey(), apiMonth);
            System.out.println("[HRMS] reimbursements  : " + (reimbRes.claims == null ? 0 : reimbRes.claims.size()) + " claim(s)");
        } catch (Exception e) {
            System.out.println("[HRMS] reimbursements  : FAILED - " + e.getMessage());
        }

        LeaveService.LeaveResponse leaveRes = null;
        try {
            leaveRes = LeaveService.fetchLeaves(LeaveService.getApiUrl(), LeaveService.getApiKey(), apiMonth);
            System.out.println("[HRMS] LOP leaves      : " + (leaveRes.data == null ? 0 : leaveRes.data.size()) + " employee(s)");
        } catch (Exception e) {
            System.out.println("[HRMS] LOP leaves      : FAILED - " + e.getMessage());
        }

        // ---- 2. Merge + recalculate (same maths as the Swing app) -------------
        String outputDir = System.getProperty("user.home") + File.separator + "SalarySlips" + File.separator + fileMonth;

        System.out.printf("%n%-8s %-22s %10s %8s %8s %8s %12s %12s%n",
                "E.Code", "Name", "Gross", "EMI", "Reimb", "LOP-ded", "TotalDed", "NetPay");
        System.out.println("-".repeat(96));

        int ok = 0, fail = 0;
        for (EmployeeSalary emp : result.employees) {
            double emi = 0, reimb = 0, leaveDaysTotal = 0, lopDays = 0;

            if (loanRes != null && loanRes.installments != null) {
                for (LoanService.Installment inst : loanRes.installments) {
                    if (inst.employee_id.equals(emp.eCode)) {
                        try {
                            emi += Double.parseDouble(inst.emi_amount);
                            if (inst.loan_amount != null) emp.loanAmount = inst.loan_amount;
                            if (inst.outstanding_amount != null) emp.outstandingAmount = inst.outstanding_amount;
                        } catch (Exception ignored) {}
                    }
                }
            }
            if (reimbRes != null && reimbRes.claims != null) {
                for (ReimbursementService.Claim c : reimbRes.claims) {
                    if (c.employee_id.equals(emp.eCode)) {
                        try { reimb += Double.parseDouble(c.amount); } catch (Exception ignored) {}
                    }
                }
            }
            if (leaveRes != null && leaveRes.data != null && leaveRes.data.containsKey(emp.eCode)) {
                leaveDaysTotal = leaveRes.data.get(emp.eCode);
            }
            if (leaveRes != null && leaveRes.lop != null && leaveRes.lop.get(emp.eCode) != null) {
                lopDays = leaveRes.lop.get(emp.eCode);
            }

            emp.loanDeducted = String.valueOf((int) Math.round(emi));
            emp.reimbursementAmount = String.valueOf((int) Math.round(reimb));
            emp.lopDays = String.valueOf(lopDays);

            // Leaves Availed = all approved leave days; Paid Days = 30 minus those.
            int leaveRounded = (int) Math.round(leaveDaysTotal);
            emp.leavesAvailed = String.valueOf(leaveRounded);
            emp.monthDays = "30";
            emp.daysWorked = String.valueOf(Math.max(0, 30 - leaveRounded));

            double basic = parseD(emp.totalBasic);
            double allowances = parseD(emp.totalHra) + parseD(emp.totalSplAllowance) + parseD(emp.totalKra);
            double taxes = parseD(emp.pt) + parseD(emp.tds);

            // Leave deduction = unpaid (LOP) days * per-day basic wage; HRMS already
            // applied the balance + Probation rule when deriving lopDays.
            double perDay = basic / 30.0;
            double leaveDeduction = lopDays * perDay;
            emp.leaveDeduction = String.valueOf((int) Math.round(leaveDeduction));

            double totalDeduction = taxes + leaveDeduction + emi;
            emp.totalDeduction = String.valueOf((int) Math.round(totalDeduction));

            double others = parseD(emp.performanceBonus) + parseD(emp.officeExpense) + parseD(emp.leavePayment);
            double totalEarnings = basic + allowances + others + reimb;
            emp.netSalary = String.valueOf((int) Math.round(totalEarnings));

            double netPay = Math.max(0, totalEarnings - totalDeduction);
            emp.netPay = String.valueOf((int) Math.round(netPay));

            System.out.printf("%-8s %-22s %10.0f %8.0f %8.0f %8.0f %12s %12s%n",
                    emp.eCode, trunc(emp.name, 22), totalEarnings, emi, reimb, leaveDeduction,
                    emp.totalDeduction, emp.netPay);

            String filename = emp.eCode.trim() + "_" + fileMonth + ".pdf";
            String path = PdfUtil.generateSalarySlip(emp, outputDir, fileMonth, filename);
            if (path != null) ok++; else { fail++; System.out.println("   ! PDF generation failed for " + emp.eCode); }
        }

        System.out.println("-".repeat(96));
        System.out.println("Slips generated: " + ok + "  failed: " + fail);

        // Consolidated payroll sheet (Master CTC + HRMS data), same folder as the slips.
        try {
            java.io.File sheet = Services.PayrollSheetWriter.write(
                    result.employees, new File(outputDir), fileMonth);
            System.out.println("Payroll sheet  : " + sheet.getAbsolutePath());
        } catch (Exception e) {
            System.out.println("Payroll sheet  : FAILED - " + e.getMessage());
        }
        // HDFC Enet bank upload file (.xls).
        try {
            java.io.File bank = Services.BankFileWriter.write(
                    result.employees, new File(outputDir), fileMonth);
            System.out.println("Bank file      : " + bank.getAbsolutePath());
        } catch (Exception e) {
            System.out.println("Bank file      : FAILED - " + e.getMessage());
        }
        System.out.println("Output folder  : " + outputDir);
    }

    private static double parseD(String s) {
        try { return Double.parseDouble(s.trim()); } catch (Exception e) { return 0; }
    }

    private static String trunc(String s, int n) {
        if (s == null) return "";
        return s.length() <= n ? s : s.substring(0, n);
    }
}
