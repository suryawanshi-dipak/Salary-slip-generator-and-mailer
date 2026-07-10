package UI;

import javax.swing.*;
import java.awt.*;

/**
 * Custom Material/iOS style toggle switch button that draws:
 * - A pill-shaped background (Purple when ON, Grey when OFF)
 * - Text ("ON" or "OFF") in bold white centered in the active side
 * - A circular white slider knob
 */
public class SwitchButton extends JToggleButton {
    private final Color colorOn = new Color(147, 51, 234); // Beautiful purple matching the image
    private final Color colorOff = new Color(156, 163, 175); // Slate grey for high contrast when OFF
    
    public SwitchButton() {
        setSelected(true); // Default state is ON
        setPreferredSize(new Dimension(70, 22));
        setCursor(new Cursor(Cursor.HAND_CURSOR));
        setOpaque(false);
        setBorder(null);
        setContentAreaFilled(false);
        setFocusPainted(false);
        setBorderPainted(false);
    }
    
    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        
        int w = getWidth();
        int h = getHeight();
        boolean selected = isSelected();
        
        // Paint background pill
        g2.setColor(selected ? colorOn : colorOff);
        g2.fillRoundRect(0, 0, w, h, h, h);
        
        // Font setup
        g2.setFont(new Font("Segoe UI", Font.BOLD, 8));
        FontMetrics fm = g2.getFontMetrics();
        int textY = (h - fm.getHeight()) / 2 + fm.getAscent();
        
        // Knob parameters
        int margin = 2;
        int knobSize = h - (margin * 2);
        int knobX = selected ? (w - knobSize - margin) : margin;
        
        // Paint Text
        g2.setColor(Color.WHITE);
        if (selected) {
            int availableWidth = w - knobSize - margin; // Space to the left of the knob
            int textX = (availableWidth - fm.stringWidth("LOG ON")) / 2;
            g2.drawString("LOG ON", textX, textY);
        } else {
            int startX = knobSize + margin;
            int availableWidth = w - startX; // Space to the right of the knob
            int textX = startX + (availableWidth - fm.stringWidth("LOG OFF")) / 2;
            g2.drawString("LOG OFF", textX, textY);
        }
        
        // Paint Knob (white circle)
        g2.setColor(Color.WHITE);
        g2.fillOval(knobX, margin, knobSize, knobSize);
        
        g2.dispose();
    }
}
