package UI;

import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;

/**
 * Main application class for the Salary Slip Generator.
 * This class builds the entire UI using Java Swing and custom drawn panels.
 * 
 * ============================================================================
 * COMPONENT & METHOD DOCUMENTATION
 * ============================================================================
 * 
 * 1. UI Components Overview:
 *    - GradientPanel: Custom JPanel used for the top header to draw a smooth gradient background.
 *    - ShadowPanel: Custom JPanel used for dashboard cards and the main table container. 
 *                   It paints a rounded rectangle background with a subtle drop shadow.
 *    - Header: The top navigation bar containing the application title and main action buttons.
 *    - Dashboard: The horizontal row of 4 statistics cards showing summary metrics.
 *    - Table Card: The main white container holding the search bar, month filter, and employee table.
 * 
 * 2. Core Methods:
 *    - SalarySlipGenerator(): Constructor. Sets up the main JFrame window, colors, and orchestrates the layout.
 *    - buildHeader(): Assembles the top gradient header (Title, Upload Button, Generate Button).
 *    - buildDashboard(): Assembles the KPI statistic cards (Total Employees, Generated, Pending, Mails Sent).
 *    - buildBody(): Combines the dashboard and the table card into the central layout region.
 *    - buildTableCard(): Constructs the data table, configures its custom renderers, and adds the search filters.
 *    - filterTable(): Applies row filtering to the table based on the search query input and month dropdown.
 *    - makeHeaderButton(): Helper method to create styled, interactive buttons for the header.
 *    - makeCard(): Helper method to create individual statistic cards with icons and labels.
 *    - createFontIcon(): Helper method that generates a perfectly centered Swing Icon from a font character.
 *    - createIconButton(): Helper method to draw custom shapes (like eye or paper plane) inside table action buttons.
 * ============================================================================
 */
public class SalarySlipGenerator extends JFrame {

    /* ===================== COLOR PALETTE ===================== */
    // Define primary colors used across the application for consistency
    private static final Color PRIMARY_BLUE = new Color(21, 101, 255);
    private static final Color PRIMARY_PURPLE = new Color(107, 76, 255);
    private static final Color BG = new Color(237, 244, 255); // Background color
    private static final Color WHITE = Color.WHITE;
    
    // Text colors
    private static final Color TEXT_HEADING = new Color(23, 52, 99);
    private static final Color TEXT_MUTED = new Color(119, 119, 119);
    
    // Status and action colors
    private static final Color GREEN = new Color(22, 163, 74);
    private static final Color GREEN_LIGHT = new Color(220, 252, 231);
    private static final Color ORANGE = new Color(217, 119, 6);
    private static final Color RED = new Color(220, 38, 38);
    private static final Color BLUE_MID = new Color(37, 99, 235);
    private static final Color UPLOAD_GREEN = new Color(24, 184, 111);
    
    // Table styling colors
    private static final Color TABLE_HEADER_BG = new Color(24, 59, 117);
    private static final Color ROW_HOVER = new Color(245, 249, 255);
    
    /* ===================== FONTS ===================== */
    private static final Font FONT = new Font("Segoe UI", Font.PLAIN, 14);
    private static final Font FONT_BOLD = new Font("Segoe UI", Font.BOLD, 14);
    private static final Font FONT_HEADING = new Font("Segoe UI", Font.BOLD, 22);
    private static final Font FONT_SUB = new Font("Segoe UI", Font.PLAIN, 14);
    private static final Font FONT_CARD_NUM = new Font("Segoe UI", Font.BOLD, 24);
    private static final Font FONT_TABLE_HEAD = new Font("Segoe UI", Font.BOLD, 14);
    private static final Font FONT_TABLE_CELL = new Font("Segoe UI", Font.PLAIN, 14);
    private static final Font FONT_FOOTER = new Font("Segoe UI", Font.PLAIN, 13);
    private static final Font FONT_TITLE = new Font("Segoe UI", Font.BOLD, 24);
    private static final Font FONT_LOGO = new Font("Segoe UI", Font.PLAIN, 13);
    
    // Constants for custom UI drawing
    private static final int ARC = 18; // Border radius for panels
    private static final int SHADOW_OFFSET = 8; // Drop shadow offset

    /* ===================== DATA ===================== */
    // Initial static mock data for the employees table
    private Object[][] data = {};
    
    // Column headers for the table
    private String[] cols = { "Emp ID", "Employee Name", "Department",
            "Basic Salary", "Net Salary", "Month",
            "Slip Status", "Mail Status", "Action" };

    // --------------------------------------------------------
    // Core UI Components
    // --------------------------------------------------------
    
    /** The underlying data model supporting the main employees JTable */
    private DefaultTableModel model;
    
    /** The main UI table displaying the employee salary records */
    private JTable table;
    
    /** Text field used to dynamically filter the table by employee name or ID */
    private JTextField searchField;
    
    /** Dropdown combo box to filter the table by payroll month */
    private JComboBox<String> monthCombo;
    
    /** In-memory storage for SMTP password, pre-loaded from smtp.properties */
    private String smtpPassword = Utils.MailUtil.getSmtpPass();
    
    // Dashboard stat labels
    private JLabel totalCountLbl;
    private JLabel generatedCountLbl;
    private JLabel pendingCountLbl;
    private JLabel mailsSentCountLbl;

    /* ===================== CONSTRUCTOR ===================== */
    /**
     * Initializes the main window and all its components.
     */
    public SalarySlipGenerator() {
        setTitle("Salary Slip Generator");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1350, 900);
        setLocationRelativeTo(null); // Center on screen
        setMinimumSize(new Dimension(960, 640)); // Prevent shrinking too small
        setBackground(BG);

        // Set application logo
        try {
            java.io.File logoFile = new java.io.File("DATA/logo.jpg");
            if (logoFile.exists()) {
                setIconImage(new ImageIcon(logoFile.getAbsolutePath()).getImage());
            }
        } catch (Exception e) {
            System.err.println("Could not load application logo: " + e.getMessage());
        }

        // Root panel holding everything
        JPanel root = new JPanel(new BorderLayout(0, 12));
        root.setBackground(BG);
        root.setBorder(BorderFactory.createEmptyBorder(12, 20, 12, 20));
        
        // Add sections to the root layout
        root.add(buildHeader(), BorderLayout.NORTH);
        root.add(buildBody(), BorderLayout.CENTER);
        root.add(buildFooter(), BorderLayout.SOUTH);
        setContentPane(root);
    }

    /* ===================== HEADER ===================== */
    private java.util.List<String[]> currentRawCsvData = new java.util.ArrayList<>();

    /**
     * Builds the top header panel including the logo, title, and action buttons.
     */
    private JPanel buildHeader() {
        // Use custom GradientPanel for a nice visual header
        GradientPanel header = new GradientPanel(
                new GradientPaint(0, 0, PRIMARY_BLUE, 400, 80, PRIMARY_PURPLE));
        header.setLayout(new BorderLayout());
        header.setPreferredSize(new Dimension(0, 80));
        header.setBorder(BorderFactory.createEmptyBorder(12, 24, 12, 24));

        // -- Left: Logo and Title --
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 14, 0));
        left.setOpaque(false);

        // Title texts
        JPanel textBox = new JPanel(new GridLayout(2, 1, 0, 2));
        textBox.setOpaque(false);
        JLabel title = new JLabel("Salary Slip Generator");
        title.setFont(FONT_TITLE);
        title.setForeground(WHITE);
        JLabel sub = new JLabel("Generate and manage employee salary slips easily.");
        sub.setFont(FONT_LOGO);
        sub.setForeground(new Color(255, 255, 255, 240));
        textBox.add(title);
        textBox.add(sub);
        left.add(textBox);

        // -- Right: Action buttons --
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 14, 0));
        right.setOpaque(false);
        
        // Upload Excel Button
        right.add(makeHeaderButton("\uE898", "Upload Excel", UPLOAD_GREEN, WHITE,
                e -> {
                    JFileChooser chooser = new JFileChooser();
                    chooser.setDialogTitle("Select Payroll CSV File");
                    int res = chooser.showOpenDialog(this);
                    if (res == JFileChooser.APPROVE_OPTION) {
                        try {
                            Services.CsvReaderService.CsvParseResult result = Services.CsvReaderService.parsePayrollCsv(chooser.getSelectedFile().getAbsolutePath());
                            
                            // Store the raw data for PDF Generation
                            currentRawCsvData = result.rawRows;
                            
                            // Clear existing table data and inject parsed CSV rows
                            model.setRowCount(0);
                            for (Object[] row : result.rows) {
                                model.addRow(row);
                            }
                            updateDashboardStats();
                            
                            // Check if validation found any errors (e.g. missing IDs, missing names)
                            if (!result.errors.isEmpty()) {
                                // Display error report dialog
                                JTextArea textArea = new JTextArea(12, 50);
                                textArea.setText(String.join("\n", result.errors));
                                textArea.setEditable(false);
                                textArea.setFont(new Font("Monospaced", Font.PLAIN, 13));
                                JScrollPane scrollPane = new JScrollPane(textArea);
                                
                                JOptionPane.showMessageDialog(this, scrollPane, "CSV Validation Report - Issues Found", JOptionPane.WARNING_MESSAGE);
                            } else {
                                JOptionPane.showMessageDialog(this, "Data loaded successfully from CSV! No errors found.", "Success", JOptionPane.INFORMATION_MESSAGE);
                            }
                        } catch (Exception ex) {
                            JOptionPane.showMessageDialog(this, "Error reading file: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                        }
                    }
                }));
                        
        // Generate Slips Button
        right.add(makeHeaderButton("\uE74C", "Generate Slips", PRIMARY_PURPLE, WHITE,
                e -> {
                    if (currentRawCsvData == null || currentRawCsvData.isEmpty()) {
                        JOptionPane.showMessageDialog(this, "No data uploaded! Please upload a CSV first.", "Warning", JOptionPane.WARNING_MESSAGE);
                        return;
                    }
                    
                    String formattedMonth = getFormattedMonth();
                    String outputDir = System.getProperty("user.home") + java.io.File.separator + "SalarySlips" + java.io.File.separator + formattedMonth;
                    int successCount = 0;
                    
                    for (int i = 0; i < currentRawCsvData.size(); i++) {
                        String[] rawRow = currentRawCsvData.get(i);
                        // Skip empty rows or rows without an ID
                        if (rawRow.length <= 1 || rawRow[1].trim().isEmpty()) continue;
                        
                        String empId = rawRow[1].trim();
                        String filename = empId + "_" + formattedMonth + ".pdf";
                        
                        String path = Utils.PdfUtil.generateSalarySlip(rawRow, outputDir, formattedMonth, filename);
                        if (path != null) {
                            successCount++;
                            if (i < model.getRowCount()) {
                                model.setValueAt("Generated", i, 6); // Update slip status
                            }
                        }
                    }
                    updateDashboardStats();
                    
                    JOptionPane.showMessageDialog(this, 
                        "Successfully generated " + successCount + " salary slips!\nSaved to: " + outputDir,
                        "Generation Complete", JOptionPane.INFORMATION_MESSAGE);
                }));

        header.add(left, BorderLayout.WEST);
        header.add(right, BorderLayout.EAST);
        return header;
    }

    /**
     * Helper method to create styled header buttons with an embedded icon.
     * 
     * @param iconCode The Unicode character for the font-based icon (e.g., "\uE898").
     * @param text     The text to display on the button.
     * @param bg       The background color of the button.
     * @param fg       The text and icon foreground color.
     * @param l        The ActionListener triggered on button click.
     * @return A fully configured, stylized JButton.
     */
    private JButton makeHeaderButton(String iconCode, String text, Color bg, Color fg, ActionListener l) {
        JButton btn = new JButton(text);
        btn.setIcon(createFontIcon(iconCode, 16, fg));
        btn.setIconTextGap(8);
        btn.setFont(FONT_BOLD);
        btn.setForeground(fg);
        btn.setBackground(bg);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.setContentAreaFilled(false);
        btn.setOpaque(true);
        btn.setPreferredSize(new Dimension(160, 42));
        btn.setBorder(BorderFactory.createEmptyBorder(0, 16, 0, 16));
        btn.addActionListener(l);
        
        // Hover effects
        btn.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) { btn.setBackground(bg.brighter()); }
            public void mouseExited(MouseEvent e) { btn.setBackground(bg); }
        });
        return btn;
    }

    /**
     * Creates an Icon instance using a font character, solving vertical alignment issues.
     * Rendering an icon from a font directly onto a graphics context avoids the "ghost text"
     * vertical layout bugs often caused by using embedded HTML inside JButtons.
     * 
     * @param text  The Unicode string representing the icon.
     * @param size  The size (width and height) of the icon square in pixels.
     * @param color The color to paint the icon.
     * @return An Icon implementation that draws the font character centered perfectly.
     */
    private Icon createFontIcon(String text, int size, Color color) {
        return new Icon() {
            private Font f = new Font("Segoe MDL2 Assets", Font.PLAIN, size);
            public int getIconWidth() { return size; }
            public int getIconHeight() { return size; }
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setFont(f);
                g2.setColor(color);
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(text, x, y + fm.getAscent() - (fm.getHeight() - size) / 2);
                g2.dispose();
            }
        };
    }

    /* ===================== DASHBOARD CARDS ===================== */
    /**
     * Builds the statistics dashboard cards.
     */
    private JPanel buildDashboard() {
        JPanel dash = new JPanel(new GridLayout(1, 4, 14, 0));
        dash.setOpaque(false);
        dash.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));

        totalCountLbl = new JLabel("0");
        generatedCountLbl = new JLabel("0");
        pendingCountLbl = new JLabel("0");
        mailsSentCountLbl = new JLabel("0");

        // Add 4 stat cards
        dash.add(makeCard("\uE716", "Total Employees", totalCountLbl, PRIMARY_BLUE));
        dash.add(makeCard("\uE73E", "Generated", generatedCountLbl, GREEN));
        dash.add(makeCard("\uE916", "Pending", pendingCountLbl, ORANGE));
        dash.add(makeCard("\uE715", "Mails Sent", mailsSentCountLbl, RED));
        return dash;
    }

    private JPanel makeCard(String iconCode, String label, JLabel valLbl, Color iconColor) {
        ShadowPanel card = new ShadowPanel();
        card.setLayout(new FlowLayout(FlowLayout.LEFT, 16, 10));
        card.setBackground(WHITE);

        // Icon
        JLabel iconLbl = new JLabel(iconCode);
        iconLbl.setFont(new Font("Segoe MDL2 Assets", Font.PLAIN, 28));
        iconLbl.setForeground(iconColor);

        // Texts
        JPanel info = new JPanel(new GridLayout(2, 1, 0, 0));
        info.setOpaque(false);
        JLabel lbl = new JLabel(label);
        lbl.setFont(FONT_SUB);
        lbl.setForeground(TEXT_MUTED);
        
        valLbl.setFont(FONT_CARD_NUM);
        valLbl.setForeground(iconColor);
        
        info.add(lbl);
        info.add(valLbl);

        card.add(iconLbl);
        card.add(info);
        return card;
    }

    private void updateDashboardStats() {
        if (model == null) return;
        
        int total = model.getRowCount();
        int generated = 0;
        int pending = 0;
        int sent = 0;
        
        for (int i = 0; i < total; i++) {
            String slipStatus = (String) model.getValueAt(i, 6);
            String mailStatus = (String) model.getValueAt(i, 7);
            
            if ("Generated".equals(slipStatus)) generated++;
            if ("Pending".equals(slipStatus)) pending++;
            if ("Sent".equals(mailStatus)) sent++;
        }
        
        if (totalCountLbl != null) totalCountLbl.setText(String.valueOf(total));
        if (generatedCountLbl != null) generatedCountLbl.setText(String.valueOf(generated));
        if (pendingCountLbl != null) pendingCountLbl.setText(String.valueOf(pending));
        if (mailsSentCountLbl != null) mailsSentCountLbl.setText(String.valueOf(sent));
    }

    private String getFormattedMonth() {
        String rawMonth = (String) monthCombo.getSelectedItem();
        if (rawMonth == null) return "Unknown";
        try {
            java.time.format.DateTimeFormatter out = java.time.format.DateTimeFormatter.ofPattern("MMM-yy", java.util.Locale.ENGLISH);
            java.time.LocalDate date = java.time.LocalDate.parse("01 " + rawMonth, java.time.format.DateTimeFormatter.ofPattern("dd MMMM yyyy", java.util.Locale.ENGLISH));
            return date.format(out);
        } catch (Exception e) {
            return rawMonth.replace(" ", "-");
        }
    }

    private void previewPdf(String pdfPath, String password) {
        try {
            org.apache.pdfbox.pdmodel.PDDocument document = org.apache.pdfbox.Loader.loadPDF(new java.io.File(pdfPath), password);
            org.apache.pdfbox.rendering.PDFRenderer pdfRenderer = new org.apache.pdfbox.rendering.PDFRenderer(document);
            // Render first page at 150 DPI for preview
            java.awt.image.BufferedImage bim = pdfRenderer.renderImageWithDPI(0, 150, org.apache.pdfbox.rendering.ImageType.RGB);
            document.close();
            
            JDialog previewDialog = new JDialog(this, "PDF Preview", true);
            previewDialog.setSize(800, 1000);
            previewDialog.setLocationRelativeTo(this);
            
            JLabel imageLabel = new JLabel(new ImageIcon(bim));
            JScrollPane scrollPane = new JScrollPane(imageLabel);
            previewDialog.add(scrollPane);
            previewDialog.setVisible(true);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Error rendering PDF preview: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /* ===================== TABLE SECTION ===================== */
    /**
     * Builds the main body section encompassing the dashboard and the table card.
     */
    private JPanel buildBody() {
        JPanel body = new JPanel(new BorderLayout(0, 12));
        body.setOpaque(false);
        body.add(buildDashboard(), BorderLayout.NORTH);
        body.add(buildTableCard(), BorderLayout.CENTER);
        return body;
    }

    /**
     * Builds the large white card panel containing the search filters and the data table.
     * This method is the largest UI constructor, setting up the custom table renderers,
     * the column layout, and attaching the real-time search logic.
     * 
     * @return The fully configured main table panel.
     */
    private JPanel buildTableCard() {
        ShadowPanel card = new ShadowPanel();
        card.setBackground(WHITE);
        card.setLayout(new BorderLayout(0, 16));
        card.setBorder(BorderFactory.createEmptyBorder(24, 24, 24, 24));

        // -- Title row --
        JPanel titleRow = new JPanel(new BorderLayout());
        titleRow.setOpaque(false);
        JLabel t = new JLabel("Employee Salary Records");
        t.setFont(FONT_HEADING);
        t.setForeground(TEXT_HEADING);
        JLabel s = new JLabel("July 2026 Payroll");
        s.setFont(FONT_SUB);
        s.setForeground(TEXT_MUTED);
        titleRow.add(t, BorderLayout.WEST);
        titleRow.add(s, BorderLayout.EAST);

        // -- Search row --
        JPanel searchRow = new JPanel(new BorderLayout(12, 0));
        searchRow.setOpaque(false);
        searchRow.setBorder(BorderFactory.createEmptyBorder(16, 0, 16, 0));

        // Custom Search Field that draws its own placeholder cleanly (avoids 'ghost text' bug)
        searchField = new JTextField() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                // Draw placeholder if text is empty and doesn't have focus
                if (getText().isEmpty() && !isFocusOwner()) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setFont(getFont());
                    g2.setColor(Color.GRAY);
                    int fm = g.getFontMetrics().getAscent();
                    g2.drawString("Search Employee Name or Employee ID", getInsets().left, (getHeight() + fm) / 2 - 2);
                    g2.dispose();
                }
            }
        };
        searchField.setFont(FONT);
        searchField.setForeground(Color.BLACK);
        searchField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(221, 221, 221), 1, true),
                BorderFactory.createEmptyBorder(12, 16, 12, 16)));
        
        // Trigger table filtering when user types
        searchField.addKeyListener(new KeyAdapter() {
            public void keyReleased(KeyEvent e) { filterTable(); }
        });
        // Repaint on focus change to show/hide placeholder correctly
        searchField.addFocusListener(new FocusAdapter() {
            public void focusGained(FocusEvent e) { searchField.repaint(); }
            public void focusLost(FocusEvent e) { searchField.repaint(); }
        });

        // Month Filter Dropdown
        monthCombo = new JComboBox<>(new String[] { "July 2026", "June 2026", "May 2026" });
        monthCombo.setFont(FONT);
        monthCombo.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(221, 221, 221), 1, true),
                BorderFactory.createEmptyBorder(10, 14, 10, 14)));
        monthCombo.setBackground(WHITE);
        monthCombo.setPreferredSize(new Dimension(200, 0));
        monthCombo.addActionListener(e -> filterTable());

        searchRow.add(searchField, BorderLayout.CENTER);
        searchRow.add(monthCombo, BorderLayout.EAST);
        
        // Group title and search into a Top Panel
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.setOpaque(false);
        topPanel.add(titleRow, BorderLayout.NORTH);
        topPanel.add(searchRow, BorderLayout.CENTER);
        card.add(topPanel, BorderLayout.NORTH);

        // -- Table Initialization --
        model = new DefaultTableModel(data, cols) {
            public boolean isCellEditable(int r, int c) {
                return c == 8; // Only Action column is editable
            }
            public Class<?> getColumnClass(int c) {
                return String.class;
            }
        };
        
        table = new JTable(model);
        table.setRowHeight(56); // Tall rows for better readability
        table.setShowGrid(false); // Clean borderless look
        table.setIntercellSpacing(new Dimension(0, 1));
        table.setSelectionBackground(ROW_HOVER);
        table.setSelectionForeground(Color.BLACK);
        table.setFont(FONT_TABLE_CELL);
        table.getTableHeader().setReorderingAllowed(false);
        table.getTableHeader().setResizingAllowed(true);

        // -- Header styling --
        JTableHeader th = table.getTableHeader();
        th.setPreferredSize(new Dimension(0, 52));
        th.setFont(FONT_TABLE_HEAD);
        th.setForeground(WHITE);
        th.setBackground(TABLE_HEADER_BG);
        
        // Custom Header Renderer
        DefaultTableCellRenderer headerRenderer = new DefaultTableCellRenderer() {
            public Component getTableCellRendererComponent(JTable t, Object val,
                    boolean sel, boolean foc, int row, int col) {
                JLabel lbl = (JLabel) super.getTableCellRendererComponent(t, val, sel, foc, row, col);
                lbl.setForeground(WHITE);
                lbl.setBackground(TABLE_HEADER_BG);
                lbl.setHorizontalAlignment(SwingConstants.CENTER);
                lbl.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
                return lbl;
            }
        };
        for (int i = 0; i < cols.length; i++) {
            th.getColumnModel().getColumn(i).setHeaderRenderer(headerRenderer);
        }

        // Column preferred widths
        int[] widths = { 70, 160, 170, 110, 110, 100, 110, 100, 120 };
        for (int i = 0; i < widths.length && i < cols.length; i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }

        // -- Cell renderers (Data Styling) --
        table.setDefaultRenderer(String.class, new DefaultTableCellRenderer() {
            public Component getTableCellRendererComponent(JTable t, Object val,
                    boolean sel, boolean foc, int row, int col) {
                JLabel lbl = (JLabel) super.getTableCellRendererComponent(t, val, sel, foc, row, col);
                lbl.setHorizontalAlignment(SwingConstants.CENTER);
                
                // Add bottom border line to each cell
                lbl.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(236, 236, 236)),
                        BorderFactory.createEmptyBorder(0, 8, 0, 8)));
                lbl.setFont(FONT_TABLE_CELL);
                lbl.setForeground(Color.BLACK);
                lbl.setBackground(sel ? ROW_HOVER : WHITE);

                // Specific styling for 'Net Salary' column
                if (col == 4) {
                    lbl.setForeground(GREEN);
                    lbl.setFont(FONT_BOLD);
                }
                // Specific styling for 'Slip Status' column
                if (col == 6) {
                    String v = val.toString();
                    if (v.equals("Generated")) {
                        lbl.setForeground(GREEN);
                        lbl.setFont(FONT_BOLD);
                    } else if (v.equals("Pending")) {
                        lbl.setForeground(ORANGE);
                        lbl.setFont(FONT_BOLD);
                    } else if (v.equals("Failed")) {
                        lbl.setForeground(RED);
                        lbl.setFont(FONT_BOLD);
                    }
                }
                // Specific styling for 'Mail Status' column
                if (col == 7) {
                    String v = val.toString();
                    if (v.equals("Sent")) {
                        lbl.setForeground(GREEN);
                        lbl.setFont(FONT_BOLD);
                    } else if (v.equals("Pending")) {
                        lbl.setForeground(BLUE_MID);
                        lbl.setFont(FONT_BOLD);
                    } else if (v.equals("Failed")) {
                        lbl.setForeground(RED);
                        lbl.setFont(FONT_BOLD);
                    }
                }
                return lbl;
            }
        });

        // Setup custom renderer and editor for the Action column (buttons)
        table.getColumnModel().getColumn(8).setCellRenderer(new ActionRenderer());
        table.getColumnModel().getColumn(8).setCellEditor(new ActionEditor());

        // -- Scroll Pane --
        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));
        scroll.getViewport().setBackground(WHITE);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);

        // Put scroll pane directly into BorderLayout.CENTER of the card so it can expand 
        // properly to fill space, guaranteeing scrollbars are fully functional and usable on resize.
        card.add(scroll, BorderLayout.CENTER);

        // -- Send All Button --
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 16));
        bottomPanel.setOpaque(false);
        JButton sendAllBtn = new JButton("Send Mail to All");
        sendAllBtn.setIcon(createFontIcon("\uE724", 14, WHITE));
        sendAllBtn.setIconTextGap(8);
        sendAllBtn.setFont(FONT_BOLD);
        sendAllBtn.setForeground(WHITE);
        sendAllBtn.setBackground(GREEN);
        sendAllBtn.setBorderPainted(false);
        sendAllBtn.setFocusPainted(false);
        sendAllBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        sendAllBtn.setPreferredSize(new Dimension(200, 42));
        
        // Button action listener
        sendAllBtn.addActionListener(e -> {
            // =========================================================
            // MAIL SENDING INTEGRATION (BATCH MODE)
            // =========================================================
            if (smtpPassword == null || smtpPassword.isEmpty()) {
                smtpPassword = promptForPassword();
                if (smtpPassword == null) return;
            }
            
            int totalSent = 0;
            int totalFailed = 0;
            int totalSkipped = 0;
            String month = getFormattedMonth();
            
            for (int i = 0; i < table.getRowCount(); i++) {
                int modelRow = table.convertRowIndexToModel(i);
                String empId = (String) model.getValueAt(modelRow, 0);
                
                if (Utils.MailUtil.isSent(empId, month)) {
                    totalSkipped++;
                    continue; // Skip already sent
                }
                
                boolean success = attemptSendSingleSlip(modelRow);
                if (success) {
                    model.setValueAt("Sent", modelRow, 7);
                    totalSent++;
                } else {
                    model.setValueAt("Failed", modelRow, 7);
                    totalFailed++;
                }
            }
            
            updateDashboardStats();
            JOptionPane.showMessageDialog(card, 
                "Batch send complete.\nSent: " + totalSent + "\nFailed: " + totalFailed + "\nSkipped (Already Sent): " + totalSkipped, 
                "Send Mail", JOptionPane.INFORMATION_MESSAGE);
        });
        
        // Button hover effect
        sendAllBtn.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) { sendAllBtn.setBackground(new Color(34, 197, 94)); }
            public void mouseExited(MouseEvent e) { sendAllBtn.setBackground(GREEN); }
        });
        
        bottomPanel.add(sendAllBtn);
        card.add(bottomPanel, BorderLayout.SOUTH);

        return card;
    }

    /* ===================== ACTION CELL ===================== */
    
    /**
     * Custom TableCellRenderer to display action buttons (View, Send) inside a table cell.
     * The renderer only paints the UI representation. It does not handle click events.
     */
    private class ActionRenderer implements TableCellRenderer {
        public Component getTableCellRendererComponent(JTable t, Object val,
                boolean sel, boolean foc, int row, int col) {
            return buildActionPanel(row, false);
        }
    }

    /**
     * Custom TableCellEditor to make the action buttons actually clickable.
     * When a user clicks the cell, the JTable switches from the Renderer to this Editor.
     * This Editor attaches actual ActionListeners to the buttons so they respond to clicks.
     */
    private class ActionEditor extends AbstractCellEditor implements TableCellEditor {
        private JPanel panel;

        public Component getTableCellEditorComponent(JTable t, Object val,
                boolean sel, int row, int col) {
            panel = buildActionPanel(row, true); // true = attach listeners
            return panel;
        }

        public Object getCellEditorValue() {
            return "";
        }
    }

    /**
     * Creates the panel containing the "View" and "Send" icon buttons.
     * @param row The row index
     * @param editable Whether to attach click listeners
     * @return The populated JPanel
     */
    private JPanel buildActionPanel(int row, boolean editable) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 7));
        p.setOpaque(true);
        p.setBackground(table.isRowSelected(row) ? ROW_HOVER : WHITE);
        p.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(236, 236, 236)));

        JButton viewBtn = createIconButton("view", new Color(238, 244, 255), PRIMARY_BLUE);
        viewBtn.setToolTipText("View Salary Slip");
        
        JButton sendBtn = createIconButton("send", GREEN_LIGHT, GREEN);
        sendBtn.setToolTipText("Send via Email");

        // Only attach click logic if we are rendering for the Editor (not just Display Renderer)
        if (editable) {
            int modelRow = table.convertRowIndexToModel(row);
            
            viewBtn.addActionListener(e -> {
                if (currentRawCsvData == null || modelRow >= currentRawCsvData.size()) {
                    JOptionPane.showMessageDialog(this, "No raw data found to generate PDF.", "Error", JOptionPane.ERROR_MESSAGE);
                } else {
                    try {
                        String[] rawData = currentRawCsvData.get(modelRow);
                        
                        String empId = rawData.length > 1 && rawData[1] != null ? rawData[1].trim() : "";
                        String name = rawData.length > 2 && rawData[2] != null ? rawData[2].trim() : "";
                        String doj = rawData.length > 3 && rawData[3] != null ? rawData[3].trim() : "";
                        
                        String formattedMonth = getFormattedMonth();
                        String outputDir = System.getProperty("user.home") + java.io.File.separator + "SalarySlips" + java.io.File.separator + formattedMonth;
                        String filename = empId + "_" + formattedMonth + ".pdf";
                        
                        java.io.File pdfFile = new java.io.File(outputDir, filename);
                        
                        if (pdfFile.exists()) {
                            String formattedDoj = doj;
                            try {
                                java.time.format.DateTimeFormatter inFormat = java.time.format.DateTimeFormatter.ofPattern("dd-MMM-yy", java.util.Locale.ENGLISH);
                                java.time.format.DateTimeFormatter outFormat = java.time.format.DateTimeFormatter.ofPattern("ddMMyyyy");
                                java.time.LocalDate date = java.time.LocalDate.parse(doj, inFormat);
                                formattedDoj = date.format(outFormat);
                            } catch (Exception ex) {}
                            String pwd = empId + formattedDoj;
                            
                            previewPdf(pdfFile.getAbsolutePath(), pwd);
                        } else {
                            JOptionPane.showMessageDialog(this, "PDF not found for " + name + "!\nExpected path: " + pdfFile.getAbsolutePath(), "Error", JOptionPane.ERROR_MESSAGE);
                        }
                    } catch (Exception ex) {
                        JOptionPane.showMessageDialog(this, "Error opening PDF: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                    }
                }
                
                if (table.isEditing()) {
                    table.getCellEditor().stopCellEditing(); // Commit edit mode
                }
            });

            sendBtn.addActionListener(e -> {
                // =========================================================
                // MAIL SENDING INTEGRATION (INDIVIDUAL MODE)
                // =========================================================
                String name = (String) model.getValueAt(modelRow, 1);
                String empId = (String) model.getValueAt(modelRow, 0);
                String month = getFormattedMonth();
                
                // Check if already sent
                if (Utils.MailUtil.isSent(empId, month)) {
                    int res = JOptionPane.showConfirmDialog(this,
                            name + " has already been sent a slip for this month. Send again?",
                            "Already Sent", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                    if (res != JOptionPane.YES_OPTION) {
                        if (table.isEditing()) table.getCellEditor().stopCellEditing();
                        return;
                    }
                }

                if (smtpPassword == null || smtpPassword.isEmpty()) {
                    smtpPassword = promptForPassword();
                    if (smtpPassword == null) {
                        if (table.isEditing()) table.getCellEditor().stopCellEditing();
                        return; // User cancelled
                    }
                }

                int confirm = JOptionPane.showConfirmDialog(this,
                        "Send salary slip to " + name + " via email?",
                        "Confirm Send", JOptionPane.YES_NO_OPTION,
                        JOptionPane.QUESTION_MESSAGE);
                        
                if (confirm == JOptionPane.YES_OPTION) {
                    boolean success = attemptSendSingleSlip(modelRow);
                    if (success) {
                        model.setValueAt("Sent", modelRow, 7); // Update state to sent
                        updateDashboardStats();
                        JOptionPane.showMessageDialog(this,
                                "Salary slip sent to " + name + " successfully.",
                                "Sent", JOptionPane.INFORMATION_MESSAGE);
                    } else {
                        model.setValueAt("Failed", modelRow, 7);
                        JOptionPane.showMessageDialog(this,
                                "Failed to send email to " + name + ". Check console for details.",
                                "Error", JOptionPane.ERROR_MESSAGE);
                    }
                }
                if (table.isEditing()) {
                    table.getCellEditor().stopCellEditing(); // Commit edit mode
                }
            });
        }

        p.add(viewBtn);
        p.add(sendBtn);
        return p;
    }

    /**
     * Helper to create custom drawn icon buttons (Eye or Paper plane shapes).
     */
    private JButton createIconButton(String type, Color bg, Color fg) {
        JButton btn = new JButton() {
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                
                // Draw rounded background
                g2.setColor(getBackground());
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                
                // Draw icon lines/shapes
                g2.setColor(getForeground());
                int cx = getWidth() / 2;
                int cy = getHeight() / 2;
                
                if (type.equals("view")) { // Draw Eye Icon
                    g2.setStroke(new BasicStroke(1.5f));
                    g2.drawOval(cx - 8, cy - 5, 16, 10);
                    g2.fillOval(cx - 3, cy - 3, 6, 6);
                } else if (type.equals("send")) { // Draw paper plane Icon
                    int[] xPoints = { cx - 7, cx + 8, cx - 7, cx - 3 };
                    int[] yPoints = { cy - 7, cy, cy + 7, cy };
                    g2.fillPolygon(xPoints, yPoints, 4);
                }
                
                g2.dispose();
                super.paintComponent(g);
            }
        };
        btn.setBackground(bg);
        btn.setForeground(fg);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setContentAreaFilled(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.setPreferredSize(new Dimension(42, 42));
        return btn;
    }

    /* ===================== FILTER ===================== */
    
    /**
     * Filters the JTable based on the search query input and the selected month.
     * This applies a custom RowFilter to the table's TableRowSorter.
     * Rows are evaluated in real-time on every keystroke in the search field.
     */
    @SuppressWarnings("unchecked")
    private void filterTable() {
        String query = searchField.getText().toLowerCase();
        String month = monthCombo.getSelectedItem().toString();
        
        // Define our custom filtering logic
        RowFilter<DefaultTableModel, Object> rf = new RowFilter<>() {
            public boolean include(Entry<? extends DefaultTableModel, ? extends Object> entry) {
                String name = entry.getStringValue(1).toLowerCase(); // Employee Name (Col 1)
                String id = entry.getStringValue(0).toLowerCase();   // Employee ID (Col 0)
                String m = entry.getStringValue(5);                  // Month (Col 5)
                
                // Must match both the text search and the dropdown month selection
                boolean matchText = name.contains(query) || id.contains(query);
                boolean matchMonth = month.equals("All") || m.equals(month);
                return matchText && matchMonth;
            }
        };
        
        // Apply the filter to the sorter
        TableRowSorter<DefaultTableModel> sorter = (TableRowSorter<DefaultTableModel>) table.getRowSorter();
        if (sorter != null) {
            sorter.setRowFilter(rf);
        }
    }

    /* ===================== FOOTER ===================== */
    /**
     * Builds the application footer containing branding and copyright details.
     * 
     * @return A styled JPanel for the bottom of the window.
     */
    private JPanel buildFooter() {
        JPanel footer = new JPanel();
        footer.setOpaque(false);
        JLabel lbl = new JLabel("\u00A9 Salary Slip Generator  \u2022  Secure  \u2022  Reliable  \u2022  Automated");
        lbl.setFont(FONT_FOOTER);
        lbl.setForeground(TEXT_MUTED);
        footer.add(lbl);
        return footer;
    }

    /* ===================== CUSTOM PANELS ===================== */

    /**
     * Panel that paints a gradient background.
     */
    static class GradientPanel extends JPanel {
        private final GradientPaint paint;

        GradientPanel(GradientPaint p) {
            this.paint = p;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g); // Always call super to handle parent painting logic
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setPaint(paint);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), ARC, ARC); // Draw gradient background
            g2.dispose();
        }
    }

    /**
     * Panel that draws a rounded rectangle with a subtle drop shadow effect.
     */
    static class ShadowPanel extends JPanel {
        ShadowPanel() {
            setOpaque(false); // Make opaque false so parent's background can shine through edges
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            
            // 1) Draw shadow (drawn underneath the main shape)
            g2.setColor(new Color(0, 0, 0, 18)); // semi-transparent black
            g2.fillRoundRect(SHADOW_OFFSET, SHADOW_OFFSET,
                    getWidth() - SHADOW_OFFSET, getHeight() - SHADOW_OFFSET, ARC, ARC);
                    
            // 2) Draw actual panel background on top of shadow
            g2.setColor(getBackground());
            g2.fillRoundRect(0, 0, getWidth() - SHADOW_OFFSET, getHeight() - SHADOW_OFFSET, ARC, ARC);
            
            g2.dispose();
        }
    }

    /* ===================== MAILER HELPERS ===================== */
    private String promptForPassword() {
        JPasswordField pf = new JPasswordField();
        int okCxl = JOptionPane.showConfirmDialog(this, pf, "Enter SMTP Password:", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (okCxl == JOptionPane.OK_OPTION) {
            return new String(pf.getPassword());
        }
        return null;
    }

    // =========================================================
    // MAIL SENDING INTEGRATION (CORE HELPER)
    // Extracts data from the CSV row and passes it to MailUtil
    // =========================================================
    private boolean attemptSendSingleSlip(int modelRow) {
        if (currentRawCsvData == null || modelRow >= currentRawCsvData.size()) return false;
        String[] rawData = currentRawCsvData.get(modelRow);
        
        String empId = rawData.length > 1 ? rawData[1].trim() : "";
        String name = rawData.length > 2 ? rawData[2].trim() : "";
        String doj = rawData.length > 3 ? rawData[3].trim() : "";
        String email = rawData.length > 21 ? rawData[21].trim() : "";
        
        String formattedMonth = getFormattedMonth();
        String outputDir = System.getProperty("user.home") + java.io.File.separator + "SalarySlips" + java.io.File.separator + formattedMonth;
        String filename = empId + "_" + formattedMonth + ".pdf";
        java.io.File pdfFile = new java.io.File(outputDir, filename);

        return Utils.MailUtil.sendAndTrackSlip(empId, formattedMonth, email, name, doj, pdfFile.getAbsolutePath(), smtpPassword);
    }

    /* ===================== MAIN ===================== */
    /**
     * Application entry point. Sets the Native System Look & Feel and creates the frame.
     */
    public static void main(String[] args) {
        try {
            // Attempt to use system-native styles for UI elements like scrollbars
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
            // Fallback to cross-platform L&F if system style fails
        }

        SwingUtilities.invokeLater(() -> {
            SalarySlipGenerator frame = new SalarySlipGenerator();
            // Attach a sorter to our table model so column headers can be clicked to sort rows
            frame.table.setRowSorter(new TableRowSorter<>(frame.model));
            frame.setVisible(true); // Launch!
            Utils.GitUtils.startUpdateCheck(frame);
        });
    }
}