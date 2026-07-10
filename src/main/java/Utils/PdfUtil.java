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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileNotFoundException;

/**
<<<<<<< Updated upstream
 * Utility class for generating Salary Slip PDFs.
 * This class uses the iText library to create a structured, password-protected PDF document.
 * The password is derived from the employee's ID and Date of Joining (DOJ).
=======
 * ============================================================================
 * PROJECT UNDERSTANDING - PdfUtil
 * ============================================================================
 * ROLE:
 * This utility handles PDF compilation and formatting for employee salary slips.
 * It reads raw CSV row structures, parses values into fields, constructs tables,
 * paints headers/totals, and outputs encrypted PDF documents to disk.
 *
 * HOW IT WORKS:
 * - Uses iText 7 to dynamically draw cells, custom borders, alignment, and styling.
 * - Password Protection: Derives owner/user document passwords using the employee ID
 *   concatenated with their parsed Date of Joining (DOJ) formatted as "ddMMyyyy"
 *   (e.g., VT0001 + 01042019 = VT000101042019).
 * - Implements SLF4J logging which is controlled by CsvReaderService.isLoggingEnabled.
 * ============================================================================
>>>>>>> Stashed changes
 */
public class PdfUtil {

    private static final Logger logger = LoggerFactory.getLogger(PdfUtil.class);

    private static void logInfo(String format, Object... arguments) {
        if (Services.CsvReaderService.isLoggingEnabled) {
            logger.info(format, arguments);
        }
    }

    private static void logWarn(String format, Object... arguments) {
        if (Services.CsvReaderService.isLoggingEnabled) {
            logger.warn(format, arguments);
        }
    }

    private static void logError(String format, Object... arguments) {
        if (Services.CsvReaderService.isLoggingEnabled) {
            logger.error(format, arguments);
        }
    }

    // Define colors used in the layout
    private static final DeviceRgb BLUE_TEXT = new DeviceRgb(0, 51, 153);
    private static final DeviceRgb GREY_BG = new DeviceRgb(200, 200, 200);

    /**
     * Generates a comprehensive, formatted PDF salary slip for an individual employee.
     * 
     * <p>This method takes a single row of employee data parsed from a CSV file and constructs
     * a professional salary slip using the iText PDF library. The generated document includes
     * a customized header with company branding, employee details, and a detailed breakdown
     * of salary components (earnings and deductions).</p>
     * 
     * <h3>Document Structure:</h3>
     * <ul>
     *   <li><b>Header:</b> Contains the company logo and corporate address.</li>
     *   <li><b>Employee Details:</b> Displays Name, Employee ID, Date of Joining, Designation, Department, Location, Leaves, and Paid Days.</li>
     *   <li><b>Salary Details:</b> A tabular breakdown separating Fixed vs Earned components, Gross Salary, Deductions (PT, TDS), and final Net Pay.</li>
     *   <li><b>Footer:</b> System generation disclaimer.</li>
     * </ul>
     * 
     * <h3>Security:</h3>
     * <p>The generated PDF is encrypted with 128-bit AES encryption. It requires a password to open.
     * The password is a concatenation of the Employee ID and the Date of Joining formatted as {@code ddMMyyyy}
     * (e.g., if EmpID is VT0001 and DOJ is 01-Jan-20, the password is {@code VT000101012020}).</p>
     * 
     * <h3>Error Handling:</h3>
     * <p>Indices from the {@code rawData} array are extracted safely with fallback to "0" if missing or empty.
     * Any failure in parsing the Date of Joining defaults to the original unparsed string for password generation,
     * while logging the error. Exceptions during file I/O operations are caught, logged, and gracefully handled by returning {@code null}.</p>
     * 
     * @param rawData     An array of strings representing a single row from the parsed CSV. 
     *                    Expected indices: [1] EmpID, [2] Name, [3] DOJ, [4-7] Fixed Salary parts, 
     *                    [8] Leaves, [9-10] Days info, [11-14] Earned Salary parts, [15-18] Deductions, [19] Net Pay.
     * @param outputDir   The absolute or relative directory path where the generated PDF should be saved. 
     *                    If the directory does not exist, it will be created.
     * @param monthString The formatted month/year string (e.g., "Jul-26") to display on the salary slip.
     * @param filename    The desired filename for the generated output (e.g., "VT0001_Jul-26.pdf").
     * @return The absolute path of the generated PDF file if successful; {@code null} if an exception occurs during generation.
     */
    public static String generateSalarySlip(String[] rawData, String outputDir, String monthString, String filename) {
        // CHANGE MADE HERE: Added logline to indicate the start of PDF generation.
        LogUtils.info("Starting PDF generation for filename: " + filename);
        File dir = new File(outputDir);
        if (!dir.exists()) dir.mkdirs();

        // Safely extract CSV indices with fallbacks
        String empId = safe(rawData, 1);
        String name = safe(rawData, 2);
        String doj = safe(rawData, 3);
        
        logInfo("Generating PDF salary slip for employee ID: {}, month: {}", empId, monthString);
        
        // Month and Static details (since not in CSV explicitly formatted)
        String month = monthString != null ? monthString : "Unknown"; 
        String designation = "Jr. Software Developer";
        String department = "Application Development";
        String location = "Maharashtra";
        
        // Leaves & Days
        String leaves = safe(rawData, 8);
        String daysInMonth = safe(rawData, 9);
        String paidDays = safe(rawData, 10);
        
        // Fixed Salary vs Earned Salary
        String basicFixed = safe(rawData, 4);
        String hraFixed = safe(rawData, 5);
        String splFixed = safe(rawData, 6);
        String grossFixed = safe(rawData, 7);
        
        String basicEarned = safe(rawData, 11);
        String hraEarned = safe(rawData, 12);
        String splEarned = safe(rawData, 13);
        String grossEarned = safe(rawData, 14);
        
        // Deductions & Net
        String pt = safe(rawData, 15);
        String tds = safe(rawData, 17);
        String totalDed = safe(rawData, 18);
        String netPay = safe(rawData, 19);

        // Filename
        String destPath = new File(dir, filename).getAbsolutePath();
        
        // Password Derivation (E.Code + DOJ in ddMMyyyy)
        String formattedDoj = doj;
        try {
            java.time.format.DateTimeFormatter inFormat = java.time.format.DateTimeFormatter.ofPattern("dd-MMM-yy", java.util.Locale.ENGLISH);
            java.time.format.DateTimeFormatter outFormat = java.time.format.DateTimeFormatter.ofPattern("ddMMyyyy");
            java.time.LocalDate date = java.time.LocalDate.parse(doj, inFormat);
            formattedDoj = date.format(outFormat);
        } catch (Exception e) {
            // fallback to original if parsing fails
<<<<<<< Updated upstream
            // CHANGE MADE HERE: Replaced System.err.println with LogUtils.error
            LogUtils.error("Could not parse DOJ: " + doj, e);
=======
            logWarn("Could not parse DOJ: {} for employee ID: {}", doj, empId);
>>>>>>> Stashed changes
        }
        String pwd = empId + formattedDoj;

        try {
            WriterProperties props = new WriterProperties()
                .setStandardEncryption(pwd.getBytes(), pwd.getBytes(), EncryptionConstants.ALLOW_PRINTING, EncryptionConstants.ENCRYPTION_AES_128);
            PdfWriter writer = new PdfWriter(destPath, props);
            PdfDocument pdf = new PdfDocument(writer);
            Document document = new Document(pdf);
            
            // Set margins
            document.setMargins(36, 36, 36, 36);

            // Create main outer table to act as a border for everything
            Table outerTable = new Table(UnitValue.createPercentArray(new float[]{100})).useAllAvailableWidth();
            outerTable.setBorder(new SolidBorder(1));

            // --- HEADER SECTION ---
            Cell headerCell = new Cell().setBorder(Border.NO_BORDER);
            Table headerGrid = new Table(UnitValue.createPercentArray(new float[]{20, 60, 20})).useAllAvailableWidth();
            
            // Logo
            Cell logoCell = new Cell().setBorder(Border.NO_BORDER).setVerticalAlignment(VerticalAlignment.MIDDLE);
            try {
                ImageData data = ImageDataFactory.create("DATA/logo.jpg");
                Image img = new Image(data);
                img.setWidth(80); // Increased scale to fit better
                logoCell.add(img);
            } catch (Exception e) {
                logoCell.add(new Paragraph("[LOGO]").setFontSize(10).setItalic());
            }
            headerGrid.addCell(logoCell);
            
            // Company Info (Centered on the page)
            Cell companyInfo = new Cell().setBorder(Border.NO_BORDER)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setVerticalAlignment(VerticalAlignment.MIDDLE);
            
            companyInfo.add(new Paragraph("Vivekanand Technologies").setFontColor(BLUE_TEXT).setBold().setFontSize(16).setMarginBottom(0));
            companyInfo.add(new Paragraph("A Blissful Development").setFontColor(BLUE_TEXT).setBold().setFontSize(12).setMarginBottom(0));
            companyInfo.add(new Paragraph("Office No.208, Building No.05,Millenium Business Park,Sector 3,Mahape Ghansoli,Navi Mumbai-400710").setFontColor(BLUE_TEXT).setFontSize(8));
            headerGrid.addCell(companyInfo);
            
            // Empty right cell to balance the 20% logo cell and keep text dead-center
            headerGrid.addCell(new Cell().setBorder(Border.NO_BORDER));
            
            headerCell.add(headerGrid);
            outerTable.addCell(headerCell);

            // --- SALARY SLIP TITLE ---
            Cell titleCell = new Cell().setBackgroundColor(GREY_BG).setTextAlignment(TextAlignment.CENTER).setBold().setFontSize(10);
            titleCell.add(new Paragraph("SALARY SLIP").setMargin(2));
            outerTable.addCell(titleCell);

            // --- EMPLOYEE DETAILS SECTION ---
            Table empDetails = new Table(UnitValue.createPercentArray(new float[]{25, 25, 30, 20})).useAllAvailableWidth();
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
            
            empDetails.addCell(noBorder("Department", true));
            empDetails.addCell(noBorder(department, false));
            empDetails.addCell(noBorder("", false));
            empDetails.addCell(noBorder("", false));
            
            empDetails.addCell(noBorder("Location", true));
            empDetails.addCell(noBorder(location, false));
            empDetails.addCell(noBorder("", false));
            empDetails.addCell(noBorder("", false));
            
            empDetails.addCell(noBorder("Month", true));
            empDetails.addCell(noBorder(month, true));
            empDetails.addCell(noBorder("", false));
            empDetails.addCell(noBorder("", false));
            
            empDetails.addCell(noBorder("Employee ID:", false));
            empDetails.addCell(noBorder(empId, false));
            empDetails.addCell(noBorder("", false));
            empDetails.addCell(noBorder("", false));

            outerTable.addCell(new Cell().add(empDetails).setPadding(5));

            // --- SALARY DETAILS TABLE ---
            Table salaryTable = new Table(UnitValue.createPercentArray(new float[]{30, 15, 15, 25, 15})).useAllAvailableWidth();
            salaryTable.setFontSize(9);
            
            // Header Row
            salaryTable.addCell(headerCell("SALARY DETAILS"));
            salaryTable.addCell(headerCell("Amount (Rs)"));
            salaryTable.addCell(headerCell("Amt. Payable"));
            salaryTable.addCell(headerCell("Deductions"));
            salaryTable.addCell(headerCell("Amount (Rs)"));
            
            // Row 1
            salaryTable.addCell(detailCell("Basic Salary", false));
            salaryTable.addCell(detailCell(basicFixed, true));
            salaryTable.addCell(detailCell(basicEarned, true));
            salaryTable.addCell(detailCell("Professional tax", false));
            salaryTable.addCell(detailCell(pt, true));
            
            // Row 2
            salaryTable.addCell(detailCell("House Rent Allowance", false));
            salaryTable.addCell(detailCell(hraFixed, true));
            salaryTable.addCell(detailCell(hraEarned, true));
            salaryTable.addCell(detailCell("TDS Deducted", false));
            salaryTable.addCell(detailCell(tds, true));
            
            // Row 3
            salaryTable.addCell(detailCell("Special Allowances", false));
            salaryTable.addCell(detailCell(splFixed, true));
            salaryTable.addCell(detailCell(splEarned, true));
            salaryTable.addCell(detailCell("", false));
            salaryTable.addCell(detailCell("", false));
            
            // Gross / Total Deductions
            salaryTable.addCell(headerCell("Gross Salary"));
            salaryTable.addCell(headerCell(grossFixed).setTextAlignment(TextAlignment.RIGHT));
            salaryTable.addCell(headerCell(grossEarned).setTextAlignment(TextAlignment.RIGHT));
            salaryTable.addCell(headerCell("Total Deductions"));
            salaryTable.addCell(headerCell(totalDed).setTextAlignment(TextAlignment.RIGHT));
            
            // Net Pay
            salaryTable.addCell(new Cell(1, 3).setBorder(Border.NO_BORDER)); // Empty span
            salaryTable.addCell(headerCell("NET PAY").setBackgroundColor(ColorConstants.WHITE));
            salaryTable.addCell(headerCell(netPay).setBackgroundColor(ColorConstants.WHITE).setTextAlignment(TextAlignment.RIGHT));
            
            outerTable.addCell(new Cell().add(salaryTable).setPadding(0));
            
            // Add the outer table to document
            document.add(outerTable);
            
            // Footer
            Paragraph footer = new Paragraph("This is a Computer Generated slip and does not require authentication")
                .setFontSize(8)
                .setBold()
                .setMarginTop(10);
            document.add(footer);

            document.close();
<<<<<<< Updated upstream
            // CHANGE MADE HERE: Added success logline before returning the destination path
            LogUtils.info("PDF generated successfully at: " + destPath);
            return destPath;
        } catch (FileNotFoundException e) {
            // CHANGE MADE HERE: Replaced e.printStackTrace() with LogUtils.error
            LogUtils.error("Failed to generate PDF for filename: " + filename, e);
=======
            logInfo("Successfully generated PDF salary slip for employee ID: {} at {}", empId, destPath);
            return destPath;
        } catch (FileNotFoundException e) {
            logError("Failed to generate PDF salary slip for employee ID: {} - File not found: {}", empId, e.getMessage(), e);
>>>>>>> Stashed changes
            return null;
        }
    }

    // --- Helper Methods for formatting cells ---
    
    private static Cell noBorder(String text, boolean bold) {
        Cell c = new Cell().add(new Paragraph(text != null ? text : "")).setBorder(Border.NO_BORDER);
        if (bold) c.setBold();
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
        if (rightAlign) c.setTextAlignment(TextAlignment.RIGHT);
        return c;
    }

    private static String safe(String[] arr, int index) {
        if (arr == null || index >= arr.length) return "0";
        String val = arr[index].trim();
        return val.isEmpty() ? "0" : val;
    }
}
