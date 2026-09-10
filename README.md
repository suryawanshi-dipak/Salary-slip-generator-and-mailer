# Salary Slip Generator

Salary Slip Generator is a Java-based desktop application designed for automated payroll processing and distribution. As a companion tool for modern HR Management Systems, it fetches approved reimbursement and loan data directly from the HRMS backend, calculates dynamic salary slips, and distributes password-protected PDFs to employees via SMTP.

## Key Features
- **Master CTC Configuration**: A permanent, month-agnostic `Master_CTC.CSV` file (salary structure, PT/TDS, bank details) is maintained by the business and pointed to **once** via the **Configuration** screen. It is read from that location every run and never modified by the app.
- **Payroll Month Selection**: The user picks the payroll month from a header dropdown; it drives the HRMS import and slip generation.
- **Backend API Integration**: The **Import Data from HRMS** action (and, if skipped, "Generate Slips" automatically) fetches approved reimbursements, loan EMI installments and Loss-of-Pay leave days from the HRMS backend via secure API endpoints.
- **Dynamic Salary Calculation**: Combines the Master CTC earnings and PT/TDS with the imported HRMS deductions (loan EMI, LOP leave) and reimbursements to compute Total Deduction and Net Pay. Leaves Availed / Paid Days are derived from the HRMS LOP figure.
- **PDF Generation**: Creates professional, password-protected PDF salary slips using Employee ID and Date of Joining as credentials.
- **Automated Emailing**: Distributes the generated PDFs directly to employees via an integrated SMTP client.
- **Desktop UI**: A simple Graphical User Interface built with Java Swing for generating slips and tracking delivery status.
- **In-App Updater & Logging**: Includes Git-based utilities for in-app hot-swapping and centralized console logging for robust error reporting.

## Tech Stack
- **Frontend**: Java Swing (Desktop UI)
- **Backend**: Java 25
- **Database**: None (Uses CSV for data storage/input)
- **Third-Party Libraries**:
  - iText 7 Core (v7.2.5) for PDF generation
  - Apache PDFBox (v3.0.1) for PDF previewing/manipulation
  - Jakarta Mail (v2.0.1) for SMTP emailing
  - Maven Shade Plugin for building a runnable fat JAR

## Project Structure
```text
Salary-slip-generator-and-mailer/
├── pom.xml                 # Maven project configuration and dependencies
├── src/
│   └── main/
│       └── java/
│           ├── Services/   # Core business logic
│           │   ├── CsvReaderService.java    # Master CTC CSV parsing & validation
│           │   ├── ConfigService.java       # Master CTC file-path config (smtp.properties)
│           │   ├── LoanService.java         # HRMS loan payroll-export client
│           │   ├── ReimbursementService.java# HRMS reimbursement payroll-export client
│           │   └── LeaveService.java        # HRMS LOP-leave payroll-export client
│           ├── UI/         # User Interface components
│           │   └── SalarySlipGenerator.java # Main application entry point & Swing UI
│           └── Utils/      # Helper utilities
│               ├── GitUtils.java            # Utilities for auto-updating via Git
│               ├── LogUtils.java            # Utility for standardized console logging
│               ├── MailUtil.java            # SMTP email dispatch logic
│               └── PdfUtil.java             # PDF creation and formatting logic
└── DATA/                   # Directory for storing input CSVs and generated PDFs
```

## Prerequisites & Installation
Ensure you have **Java 25** and **Maven** installed on your system.

```bash
# Ensure Java 25 and Maven are installed on your system
java -version
mvn -version

# Clone the repository
git clone https://github.com/suryawanshi-dipak/Salary-slip-generator-and-mailer.git
cd Salary-slip-generator-and-mailer

# Build the project to create a runnable fat JAR
mvn clean package
```

## Configuration
This project has **no database**. All configuration lives in `DATA/smtp.properties`.

### Master CTC file
Set the path to the permanent `Master_CTC.CSV` from the **Configuration** button in the
app (Browse → pick the file → Save). It is validated (exists, readable, `.csv`, has the
expected columns) and persisted as `masterctc.file.path`. The workflow is then:

1. **Configuration** — set the `Master_CTC.CSV` path once (the file is edited ~once a year).
2. **Select the payroll month** in the header dropdown (past months only).
3. **Import Data from HRMS** — pull loans / reimbursements / LOP leaves for the selected month.
4. **Generate Slips** — re-reads `Master_CTC.CSV` fresh, stamps the selected month on every
   row, merges the HRMS data, then writes, into `~/SalarySlips/<Mon-yy>/`, in order:
   the **PDF slips**, the consolidated **`Salary_<mon>_<yyyy>.csv`**, and the HDFC Enet
   **`Enet Bank salary <dd.MM.yy>.xls`** bulk-payment upload file.
   (Auto-imports HRMS first if step 3 was skipped.)

`Master_CTC.CSV` is month-agnostic — it has **no Month column**. Columns: `Sr.No., E.Code,
Name, DOJ, Total Basic, Total HRA, Total Spl. Allowance, Total KRA, Gross Salary, Basic, HRA,
Spl. Allowance, KRA, Net Salary, PT, TDS, Total Deduction, Net Pay, Email, Designation,
Bank Name, Bank A/c No., IFSC Code, Performance Bonus, Office Expense, Leave Payment`. See
`DATA/Master_CTC.csv` for an example. Employees are matched to HRMS records by `E.Code`.
The bank .xls is filled from `DATA/Enet Bank salary DD.MM.YY-1 Template.xls` (keep that file).

Headless equivalent: `java -cp target/salary-slip-generator.jar CLI.PayrollRunner DATA/Master_CTC.csv 2026-08`.

### SMTP & API Settings
The `smtp.properties` file also holds SMTP email delivery settings and the HRMS API URLs/keys.

Example `DATA/smtp.properties`:
```properties
smtp.host=sg2plzcpnl505617.prod.sin2.secureserver.net
smtp.port=465
smtp.user=your_email@example.com
smtp.pass=your_password
smtp.from=sender@example.com
smtp.secure=true

# Reimbursement API Integration Settings
reimbursement.api.url=http://161.118.171.230/api/reimbursements/payroll-export
reimbursement.api.key=your_service_api_key_here

# Loan API Integration Settings
loan.api.url=http://161.118.171.230/api/loans/payroll-export
loan.api.key=your_service_api_key_here

# Leave API Integration Settings
leave.api.url=http://161.118.171.230/api/payroll-export/leaves
leave.api.key=your_service_api_key_here

# Master CTC file - set via the Configuration button
masterctc.file.path=C:\\path\\to\\Master_CTC.csv
```

## Usage/Running the Application
To run the compiled application:
```bash
java -jar target/salary-slip-generator.jar
```


$env:JAVA_HOME = "C:\Program Files\Java\jdk-21"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
java -version
mvn clean package
