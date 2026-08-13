# Salary Slip Generator

Salary Slip Generator is a Java-based desktop application designed for automated payroll processing and distribution. As a companion tool for modern HR Management Systems, it fetches approved reimbursement and loan data directly from the HRMS backend, calculates dynamic salary slips, and distributes password-protected PDFs to employees via SMTP.

## Key Features
- **Backend API Integration**: Directly fetches approved employee reimbursements and loan EMI data from the HRMS backend via secure API endpoints.
- **CSV Data Ingestion**: Parses core employee salary data and personal details from standard CSV files.
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
│           │   └── CsvReaderService.java    # CSV parsing logic
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
This project relies on a `DATA/smtp.properties` file to securely store configuration.

### SMTP & API Settings
```properties
smtp.host=sg2plzcpnl505617.prod.sin2.secureserver.net
smtp.port=465
smtp.user=your_email@example.com
smtp.pass=your_password
smtp.from=sender@example.com
smtp.secure=true

# Reimbursement API Integration Settings
reimbursement.api.url=http://localhost:5000/api/reimbursements/payroll-export
reimbursement.api.key=your_service_api_key_here

# Loan API Integration Settings
loan.api.url=http://localhost:5000/api/loans/payroll-export
loan.api.key=your_service_api_key_here
```

## Usage/Running the Application
To run the compiled application:
```bash
java -jar target/salary-slip-generator.jar
```
