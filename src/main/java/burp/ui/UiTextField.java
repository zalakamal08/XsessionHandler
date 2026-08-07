package burp.ui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;

import javax.swing.JTextField;
import javax.swing.border.AbstractBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/** A text field with a rounded outline that turns red when the input is bad. */
public class UiTextField extends JTextField {

    /** Called with the new text every time the user types. */
    public interface Changed {
        void changed(String text);
    }

    private boolean invalidInput;

    public UiTextField(String value, int columns) {
        super(value, columns);
        setFont(Theme.mono());
        setOpaque(false);
        setBorder(new RoundBorder());
        setCaretColor(Theme.foreground());
        setForeground(Theme.foreground());
    }

    public UiTextField onChange(Changed handler) {
        getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                handler.changed(getText());
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                handler.changed(getText());
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                handler.changed(getText());
            }
        });
        return this;
    }

    public void setInvalidInput(boolean invalidInput, String reason) {
        this.invalidInput = invalidInput;
        setToolTipText(invalidInput ? reason : null);
        repaint();
    }

    public UiTextField hint(String tooltip) {
        setToolTipText(tooltip);
        return this;
    }

    @Override
    public Dimension getMaximumSize() {
        return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Color fill = isEnabled() ? Theme.background() : Theme.mix(Theme.background(), Theme.card(), 0.6f);
        g2.setColor(fill);
        g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
        g2.dispose();
        super.paintComponent(g);
    }

    /** Rounded outline: accent when focused, red when invalid. */
    private class RoundBorder extends AbstractBorder {
        @Override
        public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color color;
            if (invalidInput) {
                color = Theme.danger();
            } else if (isFocusOwner()) {
                color = Theme.accent();
            } else {
                color = Theme.line();
            }
            g2.setColor(isEnabled() ? color : Theme.mix(Theme.line(), Theme.background(), 0.5f));
            g2.drawRoundRect(x, y, width - 1, height - 1, 8, 8);
            g2.dispose();
        }

        @Override
        public Insets getBorderInsets(Component c) {
            return new Insets(5, 8, 5, 8);
        }

        @Override
        public Insets getBorderInsets(Component c, Insets insets) {
            insets.set(5, 8, 5, 8);
            return insets;
        }
    }
}
