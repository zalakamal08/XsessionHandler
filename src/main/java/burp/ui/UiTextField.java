package burp.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;

import javax.swing.JTextField;
import javax.swing.border.AbstractBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * The one input in the tab: a recessed monospace well that rings in the accent
 * when focused and in the danger colour when what is typed will not compile.
 * Everything the user types here is machine-read, so it is always monospace.
 */
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
        setBorder(new Well());
        setCaretColor(Theme.accent());
        setForeground(Theme.text());
        setSelectionColor(Theme.accentSoft());
        setSelectedTextColor(Theme.text());
        addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                repaint();
            }

            @Override
            public void focusLost(FocusEvent e) {
                repaint();
            }
        });
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

    public boolean isInvalidInput() {
        return invalidInput;
    }

    public UiTextField hint(String tooltip) {
        setToolTipText(invalidInput ? getToolTipText() : tooltip);
        return this;
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        setForeground(enabled ? Theme.text() : Theme.muted(Theme.text()));
    }

    @Override
    public Dimension getMaximumSize() {
        return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
    }

    @Override
    public Dimension getMinimumSize() {
        // Small enough that a dense row can squeeze every column onto one
        // line; the layout hands out real width by weight.
        return new Dimension(48, getPreferredSize().height);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Color fill;
        if (!isEnabled()) {
            fill = Theme.canvas();
        } else if (invalidInput) {
            fill = Theme.mix(Theme.well(), Theme.danger(), 0.10f);
        } else {
            fill = Theme.well();
        }
        g2.setColor(fill);
        g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, Theme.RADIUS_MD, Theme.RADIUS_MD);
        g2.dispose();
        super.paintComponent(g);
    }

    /** The outline: quiet at rest, accent when focused, danger when invalid. */
    private class Well extends AbstractBorder {
        @Override
        public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color color;
            float weight = 1f;
            if (!isEnabled()) {
                color = Theme.line();
            } else if (invalidInput) {
                color = Theme.danger();
                weight = 1.6f;
            } else if (isFocusOwner()) {
                color = Theme.accent();
                weight = 1.6f;
            } else {
                color = Theme.line();
            }
            g2.setColor(color);
            g2.setStroke(new BasicStroke(weight));
            g2.drawRoundRect(x, y, width - 1, height - 1, Theme.RADIUS_MD, Theme.RADIUS_MD);
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
