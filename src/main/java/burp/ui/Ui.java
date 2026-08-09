package burp.ui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.font.TextAttribute;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

/** Small shared widgets and layout helpers. */
public final class Ui {

    private Ui() {
    }

    public static JLabel label(String text) {
        JLabel label = new JLabel(text);
        label.setFont(Theme.base());
        label.setForeground(Theme.foreground());
        return label;
    }

    public static JLabel muted(String text) {
        JLabel label = new JLabel(text);
        label.setFont(Theme.small());
        label.setForeground(Theme.muted());
        return label;
    }

    public static JLabel sectionLabel(String text) {
        JLabel label = new JLabel(text.toUpperCase());
        label.setFont(Theme.small().deriveFont(java.awt.Font.BOLD));
        label.setForeground(Theme.muted());
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        label.setBorder(BorderFactory.createEmptyBorder(6, 0, 4, 0));
        return label;
    }

    public static JCheckBox checkBox(String text, boolean selected) {
        JCheckBox box = new JCheckBox(text, selected);
        box.setOpaque(false);
        box.setFont(Theme.base());
        box.setForeground(Theme.foreground());
        box.setFocusPainted(false);
        return box;
    }

    /** A blue clickable label that opens a URL. */
    public static JLabel link(String text, String url) {
        JLabel label = new JLabel(text);
        Font plainFont = Theme.base();
        Map<TextAttribute, Object> underlineAttrs = new HashMap<TextAttribute, Object>(plainFont.getAttributes());
        underlineAttrs.put(TextAttribute.UNDERLINE, TextAttribute.UNDERLINE_ON);
        Font underlinedFont = plainFont.deriveFont(underlineAttrs);
        label.setFont(plainFont);
        label.setForeground(Theme.accent());
        label.setCursor(new Cursor(Cursor.HAND_CURSOR));
        label.setToolTipText(url);
        label.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                try {
                    Desktop.getDesktop().browse(URI.create(url));
                } catch (Exception ignored) {
                    // No browser available; the tooltip still shows the URL.
                }
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                label.setFont(underlinedFont);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                label.setFont(plainFont);
            }
        });
        return label;
    }

    /** A transparent left-aligned row that never stretches vertically. */
    public static JPanel row(int gap) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, gap, 0)) {
            @Override
            public Dimension getMaximumSize() {
                return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
            }
        };
        panel.setOpaque(false);
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        return panel;
    }

    /** A transparent vertical stack. */
    public static JPanel column() {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        return panel;
    }

    public static Component strut(int width) {
        return Box.createHorizontalStrut(width);
    }

    /** A 1px horizontal rule. */
    public static JComponent separator() {
        JPanel line = new JPanel() {
            @Override
            public Dimension getMaximumSize() {
                return new Dimension(Integer.MAX_VALUE, 1);
            }
        };
        line.setPreferredSize(new Dimension(1, 1));
        line.setBackground(Theme.line());
        line.setAlignmentX(Component.LEFT_ALIGNMENT);
        return line;
    }

    /** A rounded, coloured status pill. */
    public static class Pill extends JLabel {
        private Color fill;

        public Pill(String text, Color color) {
            super(text);
            this.fill = color;
            setFont(Theme.small().deriveFont(java.awt.Font.BOLD));
            setForeground(color);
            setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));
            setAlignmentY(CENTER_ALIGNMENT);
        }

        public void set(String text, Color color) {
            this.fill = color;
            setText(text);
            setForeground(color);
            repaint();
        }

        @Override
        public Dimension getMaximumSize() {
            return getPreferredSize();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(Theme.alpha(fill, Theme.isDark() ? 48 : 28));
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), getHeight(), getHeight());
            g2.dispose();
            super.paintComponent(g);
        }
    }
}
