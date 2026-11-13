package Client;

import javax.swing.JButton;
import javax.swing.BorderFactory;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

public class StyledButton extends JButton {

    private Color baseColor;

    public StyledButton(String text) {

        super(text);

        setFont(AppFonts.BUTTON);

        setForeground(Color.WHITE);
        baseColor = AppColors.PRIMARY_LIGHT;
        setBackground(baseColor);

        setFocusPainted(false);
        setBorderPainted(false);
        setContentAreaFilled(false);
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(10, 18, 10, 18));

        setCursor(new Cursor(Cursor.HAND_CURSOR));

        setPreferredSize(new Dimension(150, 42));
    }

    public void setButtonColor(Color color) {
        baseColor = color;
        setBackground(color);
        repaint();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);

        Color fill = baseColor;
        if (getModel().isPressed()) {
            fill = baseColor.darker();
        } else if (getModel().isRollover()) {
            fill = baseColor.brighter();
        }

        g.setColor(fill);
        g.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
        g.dispose();
        super.paintComponent(graphics);
    }
}