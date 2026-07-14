package Utils;

import com.itextpdf.io.image.ImageData;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.pdf.EncryptionConstants;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.WriterProperties;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Image;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.itextpdf.layout.properties.VerticalAlignment;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;

/**
 * ============================================================================
 * PROJECT UNDERSTANDING - PdfUtil
 * ============================================================================
 * ROLE:
 * This utility handles PDF compilation and formatting for employee salary
 * slips.
 * It reads EmployeeSalary row structures, parses values into fields, constructs
 * tables
 * (dynamically hiding rows based on the v2 conditional row suppression
 * requirements),
 * paints headers/totals, and outputs encrypted PDF documents to disk.
 *
 * HOW IT WORKS:
 * - Uses iText 7 to dynamically draw cells, custom borders, alignment, and
 * styling.
 * - Password Protection: Derives owner/user document passwords using the
 * employee ID
 * concatenated with their parsed Date of Joining (DOJ) formatted as "ddMMyyyy"
 * (e.g., VT0001 + 01042019 = VT000101042019).
 * - Implements SLF4J logging which is controlled by
 * CsvReaderService.isLoggingEnabled.
 * ============================================================================
 */
public class PdfUtil {

    private static final DeviceRgb BLUE_TEXT = new DeviceRgb(0, 51, 153);
    private static final DeviceRgb GREY_BG = new DeviceRgb(200, 200, 200);

    private static class EarningRow {
        String label;
        String fixedAmt;
        String earnedAmt;

        EarningRow(String label, String fixedAmt, String earnedAmt) {
            this.label = label;
            this.fixedAmt = fixedAmt;
            this.earnedAmt = earnedAmt;
        }
    }

    private static class DeductionRow {
        String label;
        String amt;

        DeductionRow(String label, String amt) {
            this.label = label;
            this.amt = amt;
        }
    }

    /**
     * Generates a comprehensive, formatted PDF salary slip for an individual
     * employee.
     * 
     * <p>
     * This method takes an EmployeeSalary object and constructs
     * a professional salary slip using the iText PDF library. The generated
     * document includes
     * a customized header with company branding, employee details, and a detailed
     * breakdown
     * of salary components (earnings and deductions) with dynamic row suppression.
     * </p>
     * 
     * <h3>Document Structure:</h3>
     * <ul>
     * <li><b>Header:</b> Contains the company logo and corporate address.</li>
     * <li><b>Employee Details:</b> Displays Name, Employee ID, Date of Joining,
     * Designation, Leaves, Paid Days, and Masked Bank Account.</li>
     * <li><b>Salary Details:</b> A tabular breakdown separating Fixed vs Earned
     * components, Gross Salary, Deductions (PT, TDS), and final Net Pay. Omit rows
     * with 0 amounts for conditional components.</li>
     * <li><b>Footer:</b> System generation disclaimer.</li>
     * </ul>
     * 
     * <h3>Security:</h3>
     * <p>
     * The generated PDF is encrypted with 128-bit AES encryption. It requires a
     * password to open.
     * The password is a concatenation of the Employee ID and the Date of Joining
     * formatted as {@code ddMMyyyy}
     * (e.g., if EmpID is VT0001 and DOJ is 01-Jan-20, the password is
     * {@code VT000101012020}).
     * </p>
     * 
     * <h3>Error Handling:</h3>
     * <p>
     * Fields are extracted safely with fallback to "0" if missing or empty.
     * Any failure in parsing the Date of Joining defaults to the original unparsed
     * string for password generation,
     * while logging the error. Exceptions during file I/O operations are caught,
     * logged, and gracefully handled by returning {@code null}.
     * </p>
     * 
     * @param empData     An EmployeeSalary object representing a single row from
     *                    the parsed CSV.
     * @param outputDir   The absolute or relative directory path where the
     *                    generated PDF should be saved.
     *                    If the directory does not exist, it will be created.
     * @param monthString The formatted month/year string (e.g., "Jul-26") to
     *                    display on the salary slip.
     * @param filename    The desired filename for the generated output (e.g.,
     *                    "VT0001_Jul-26.pdf").
     * @return The absolute path of the generated PDF file if successful;
     *         {@code null} if an exception occurs during generation.
     */
    public static String generateSalarySlip(Services.CsvReaderService.EmployeeSalary empData, String outputDir,
            String monthString, String filename) {
        LogUtils.info("Starting PDF generation for filename: " + filename);
        File dir = new File(outputDir);
        if (!dir.exists())
            dir.mkdirs();
        Utils.LogUtils.debug("PDF output directory: {}", dir.getAbsolutePath());

        String empId = safeStr(empData.eCode);
        String name = safeStr(empData.name);
        String doj = safeStr(empData.doj);

        Utils.LogUtils.info("Generating PDF salary slip for employee ID: {}, month: {}", empId, monthString);

        String month = monthString != null ? monthString : "Unknown";
        String designation = safeStr(empData.designation);
        String bankName = safeStr(empData.bankName);
        String maskedBankAccount = safeStr(empData.maskedBankAccountNo());

        String leaves = safe(empData.leavesAvailed);
        String daysInMonth = safe(empData.monthDays);
        String paidDays = safe(empData.daysWorked);

        String grossSalary = safe(empData.grossSalary);
        String totalDed = safe(empData.totalDeduction);
        String netPay = safe(empData.netPay);
        Utils.LogUtils.debug("Loaded salary data for Employee ID: {}, Name: {}", empId, name);

        // --- Earnings Table Preparation ---
        // We dynamically build the list of earnings to allow for row suppression.
        Utils.LogUtils.debug("Preparing earnings components for Employee ID: {}", empId);
        List<EarningRow> earnings = new ArrayList<>();

        // Unconditional components: Always printed even if the value is zero.
        earnings.add(new EarningRow("Basic Salary", safe(empData.totalBasic), safe(empData.basic)));
        earnings.add(new EarningRow("House Rent Allowance", safe(empData.totalHra), safe(empData.hra)));
        earnings.add(new EarningRow("Special Allowances", safe(empData.totalSplAllowance), safe(empData.splAllowance)));
        earnings.add(new EarningRow("KRA", safe(empData.totalKra), safe(empData.kra))); // KRA always shown

        // Conditional components: Only added if their value is greater than 0.
        // We pass an empty string ("") for the Fixed/Contractual column since these are
        // payable-only variables.
        if (parseInteger(empData.performanceBonus) != 0) {
            earnings.add(new EarningRow("Performance Bonus", "", safe(empData.performanceBonus)));
        }
        if (parseInteger(empData.officeExpense) != 0) {
            earnings.add(new EarningRow("Office Expense", "", safe(empData.officeExpense)));
        }
        if (parseInteger(empData.leavePayment) != 0) {
            earnings.add(new EarningRow("Leave Payment", "", safe(empData.leavePayment)));
        }

        // --- Deductions Table Preparation ---
        // Dynamically build deductions side.

        List<DeductionRow> deductions = new ArrayList<>();
        deductions.add(new DeductionRow("Professional tax", safe(empData.pt)));
        deductions.add(new DeductionRow("TDS Deducted", safe(empData.tds)));

        if (parseInteger(empData.loanDeducted) != 0) {
            deductions.add(new DeductionRow("Loan Deducted", safe(empData.loanDeducted)));
        }

        String destPath = new File(dir, filename).getAbsolutePath();

        // --- Password Generation (FR-06) ---
        // The password is Emp ID + DOJ (in ddMMyyyy format).
        // The source CSV now uses d-MMM-yy (e.g. 1-Apr-19), so we must parse it back
        // into the strict format.
        String formattedDoj = doj;
        try {
            java.time.format.DateTimeFormatter inFormat = java.time.format.DateTimeFormatter.ofPattern("d-MMM-yy",
                    java.util.Locale.ENGLISH);
            java.time.format.DateTimeFormatter outFormat = java.time.format.DateTimeFormatter.ofPattern("ddMMyyyy");
            java.time.LocalDate date = java.time.LocalDate.parse(doj, inFormat);
            formattedDoj = date.format(outFormat);
        } catch (Exception e) {
            Utils.LogUtils.warn("Could not parse DOJ: {} for employee ID: {}. Falling back to unformatted string.", doj,
                    empId);
        }

        // Final Document Password
        String pwd = empId + formattedDoj;
        Utils.LogUtils.debug("PDF password generated successfully for Employee ID: {}", empId);
        try {
            Utils.LogUtils.info("Creating encrypted PDF document for Employee ID: {}", empId);
            WriterProperties props = new WriterProperties()
                    .setStandardEncryption(pwd.getBytes(), pwd.getBytes(), EncryptionConstants.ALLOW_PRINTING,
                            EncryptionConstants.ENCRYPTION_AES_128);
            PdfWriter writer = new PdfWriter(destPath, props);
            PdfDocument pdf = new PdfDocument(writer);
            Document document = new Document(pdf);

            document.setMargins(36, 36, 36, 36);

            Table outerTable = new Table(UnitValue.createPercentArray(new float[] { 100 })).useAllAvailableWidth();
            outerTable.setBorder(new SolidBorder(1));

            // Header Section
            Cell headerCell = new Cell().setBorder(Border.NO_BORDER);
            Table headerGrid = new Table(UnitValue.createPercentArray(new float[] { 20, 60, 20 }))
                    .useAllAvailableWidth();

            Cell logoCell = new Cell().setBorder(Border.NO_BORDER).setVerticalAlignment(VerticalAlignment.MIDDLE);
            try {
                Utils.LogUtils.debug("Loading company logo into PDF");
                ImageData data = ImageDataFactory.create("DATA/logo.jpg");
                Image img = new Image(data);
                img.setWidth(80);
                logoCell.add(img);
            } catch (Exception e) {
                Utils.LogUtils.warn("Company logo not found. Using placeholder logo.");
                logoCell.add(new Paragraph("[LOGO]").setFontSize(10).setItalic());
            }
            headerGrid.addCell(logoCell);

            Cell companyInfo = new Cell().setBorder(Border.NO_BORDER)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setVerticalAlignment(VerticalAlignment.MIDDLE);

            companyInfo.add(new Paragraph("Vivekanand Technologies").setFontColor(BLUE_TEXT).setBold().setFontSize(16)
                    .setMarginBottom(0));
            companyInfo.add(new Paragraph("A Blissful Development").setFontColor(BLUE_TEXT).setBold().setFontSize(12)
                    .setMarginBottom(0));
            companyInfo.add(new Paragraph(
                    "Office No.208, Building No.05,Millenium Business Park,Sector 3,Mahape Ghansoli,Navi Mumbai-400710")
                    .setFontColor(BLUE_TEXT).setFontSize(8));
            headerGrid.addCell(companyInfo);

            headerGrid.addCell(new Cell().setBorder(Border.NO_BORDER));
            headerCell.add(headerGrid);
            outerTable.addCell(headerCell);

            Cell titleCell = new Cell().setBackgroundColor(GREY_BG).setTextAlignment(TextAlignment.CENTER).setBold()
                    .setFontSize(10);
            titleCell.add(new Paragraph("SALARY SLIP").setMargin(2));
            outerTable.addCell(titleCell);

            // Employee Details Section
            Table empDetails = new Table(UnitValue.createPercentArray(new float[] { 25, 25, 30, 20 }))
                    .useAllAvailableWidth();
            empDetails.setFontSize(9);

            empDetails.addCell(noBorder("Name of the Employee", true));
            empDetails.addCell(noBorder(name, false));
            empDetails.addCell(noBorder("LEAVES AVAILED", true).setUnderline());
            empDetails.addCell(noBorder(leaves, false));

            empDetails.addCell(noBorder("Date of Joining", true));
            empDetails.addCell(noBorder(doj, false));
            empDetails.addCell(noBorder("No of Days in the Month", true));
            empDetails.addCell(noBorder(daysInMonth, false));

            empDetails.addCell(noBorder("Designation", true));
            empDetails.addCell(noBorder(designation, false));
            empDetails.addCell(noBorder("Paid Days", true));
            empDetails.addCell(noBorder(paidDays, false));

            empDetails.addCell(noBorder("Month", true));
            empDetails.addCell(noBorder(month, true));
            empDetails.addCell(noBorder("", false));
            empDetails.addCell(noBorder("", false));

            empDetails.addCell(noBorder("Bank Name", true));
            empDetails.addCell(noBorder(bankName, false));
            empDetails.addCell(noBorder("", false));
            empDetails.addCell(noBorder("", false));

            empDetails.addCell(noBorder("Bank Account Number", true));
            empDetails.addCell(noBorder(maskedBankAccount, false));
            empDetails.addCell(noBorder("", false));
            empDetails.addCell(noBorder("", false));

            empDetails.addCell(noBorder("Employee ID:", false));
            empDetails.addCell(noBorder(empId, false));
            empDetails.addCell(noBorder("", false));
            empDetails.addCell(noBorder("", false));

            outerTable.addCell(new Cell().add(empDetails).setPadding(5));

            // Salary Details Section
            Table salaryTable = new Table(UnitValue.createPercentArray(new float[] { 30, 15, 15, 25, 15 }))
                    .useAllAvailableWidth();
            salaryTable.setFontSize(9);

            salaryTable.addCell(headerCell("SALARY DETAILS"));
            salaryTable.addCell(headerCell("Amount (Rs)"));
            salaryTable.addCell(headerCell("Amt. Payable"));
            salaryTable.addCell(headerCell("Deductions"));
            salaryTable.addCell(headerCell("Amount (Rs)"));

            // --- Dynamic Table Rendering (FR-17) ---
            // Calculate maximum number of rows needed (either earnings or deductions
            // dictates height).
            Utils.LogUtils.debug("Rendering salary table with {} earning rows and {} deduction rows", earnings.size(),
                    deductions.size());
            int maxRows = Math.max(earnings.size(), deductions.size());

            // Iterate down the rows, injecting earnings on the left and deductions on the
            // right
            for (int i = 0; i < maxRows; i++) {

                // Inject Earning cells (or blank cells if we ran out of earnings)
                if (i < earnings.size()) {
                    EarningRow eRow = earnings.get(i);
                    salaryTable.addCell(detailCell(eRow.label, false));
                    salaryTable.addCell(detailCell(eRow.fixedAmt, true));
                    salaryTable.addCell(detailCell(eRow.earnedAmt, true));
                } else {
                    salaryTable.addCell(detailCell("", false));
                    salaryTable.addCell(detailCell("", false));
                    salaryTable.addCell(detailCell("", false));
                }

                // Inject Deduction cells (or blank cells if we ran out of deductions)
                if (i < deductions.size()) {
                    DeductionRow dRow = deductions.get(i);
                    salaryTable.addCell(detailCell(dRow.label, false));
                    salaryTable.addCell(detailCell(dRow.amt, true));
                } else {
                    salaryTable.addCell(detailCell("", false));
                    salaryTable.addCell(detailCell("", false));
                }
            }

            // --- Totals Row ---
            salaryTable.addCell(headerCell("").setTextAlignment(TextAlignment.LEFT));
            salaryTable.addCell(headerCell(grossSalary).setTextAlignment(TextAlignment.RIGHT));
            salaryTable.addCell(headerCell(safe(empData.netSalary)).setTextAlignment(TextAlignment.RIGHT));
            salaryTable.addCell(headerCell("Total Deductions").setTextAlignment(TextAlignment.CENTER));
            salaryTable.addCell(headerCell(totalDed).setTextAlignment(TextAlignment.RIGHT));

            Cell netPayLabel = new Cell(1, 2).add(new Paragraph("NET PAY"))
                    .setBackgroundColor(ColorConstants.WHITE).setBold().setTextAlignment(TextAlignment.CENTER);
            salaryTable.addCell(netPayLabel);

            Cell netPayValue = new Cell().add(new Paragraph(netPay))
                    .setBackgroundColor(ColorConstants.WHITE).setBold().setTextAlignment(TextAlignment.RIGHT);
            salaryTable.addCell(netPayValue);

            salaryTable.addCell(new Cell(1, 2).setBorder(Border.NO_BORDER));

            outerTable.addCell(new Cell().add(salaryTable).setPadding(0));
            document.add(outerTable);

            Paragraph footer = new Paragraph("This is a Computer Generated slip and does not require authentication")
                    .setFontSize(8)
                    .setBold()
                    .setMarginTop(10);
            document.add(footer);
            Utils.LogUtils.debug("Finalizing PDF document for Employee ID: {}", empId);
            document.close();
            Utils.LogUtils.info("Successfully generated PDF salary slip for employee ID: {} at {}", empId, destPath);
            return destPath;
        } catch (FileNotFoundException e) {
            Utils.LogUtils.error("Failed to generate PDF salary slip for employee ID: {} - File not found: {}", empId,
                    e.getMessage(), e);
            return null;
        }
    }

    private static Cell noBorder(String text, boolean bold) {
        Cell c = new Cell().add(new Paragraph(text != null ? text : "")).setBorder(Border.NO_BORDER);
        if (bold)
            c.setBold();
        return c;
    }

    private static Cell headerCell(String text) {
        return new Cell().add(new Paragraph(text != null ? text : ""))
                .setBackgroundColor(GREY_BG)
                .setBold()
                .setTextAlignment(TextAlignment.CENTER)
                .setVerticalAlignment(VerticalAlignment.MIDDLE)
                .setPadding(3);
    }

    private static Cell detailCell(String text, boolean rightAlign) {
        Cell c = new Cell().add(new Paragraph(text != null ? text : "")).setPadding(3);
        if (rightAlign)
            c.setTextAlignment(TextAlignment.RIGHT);
        return c;
    }

    private static String safe(String val) {
        if (val == null || val.trim().isEmpty())
            return "0";
        return val.trim();
    }

    private static String safeStr(String val) {
        if (val == null || val.trim().isEmpty())
            return "";
        return val.trim();
    }

    private static int parseInteger(String val) {
        if (val == null || val.trim().isEmpty())
            return 0;
        try {
            return Integer.parseInt(val.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
