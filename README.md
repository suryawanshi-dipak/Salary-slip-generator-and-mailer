# Salary Slip Generator

Salary Slip Generator is a Java-based desktop application designed for automated payroll processing. It reads employee data from CSV files, generates password-protected PDF salary slips, and automatically emails them to employees via SMTP.

## Key Features
- **CSV Data Ingestion**: Parses employee salary and details from standard CSV files.
- **Dynamic Salary Calculation**: Processes earnings and deductions based on the provided input data.
- **PDF Generation**: Creates professional, password-protected PDF salary slips. The PDF password logic utilizes the Employee ID and Date of Joining.
- **Automated Emailing**: Distributes the generated PDFs directly to employees via an integrated SMTP client.
- **Desktop UI**: Features a Graphical User Interface built with Java Swing for ease of use.
- **In-App Updater**: Includes Git-based utilities for in-app hot-swapping and updates.
- **Integrated Logging**: Features centralized, formatted console logging for process tracking and robust error reporting.

## Tech Stack
- **Frontend**: Java Swing (Desktop UI)
- **Backend**: Java 21
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
```bash
# Ensure Java 21 and Maven are installed on your system
java -version
mvn -version

# Clone the repository
git clone https://github.com/suryawanshi-dipak/Salary-slip-generator-and-mailer.git
cd Salary-slip-generator-and-mailer

# Build the project and create a runnable fat JAR
mvn clean package
```

## Environment Variables
This project does not require a `.env` file. SMTP credentials (email address and application-specific password) are provided securely at runtime through the application's graphical user interface.

## Usage/Running the Application
```bash
# Run the compiled application JAR file
java -jar target/salary-slip-generator.jar
```
