package burp.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.font.TextAttribute;
import java.awt.geom.Path2D;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

/** The shared widget set. Every control in the tab is built from these. */
public final class Ui {

    private Ui() {
    }

    // ----------------------------------------------------------------- text

    public static JLabel label(String text) {
        return text(text, Theme.body(), Theme.text());
    }

    public static JLabel strong(String text) {
        return text(text, Theme.strong(), Theme.text());
    }

    /** Secondary copy - helper notes sitting beside a control. */
    public static JLabel dim(String text) {
        return text(text, Theme.small(), Theme.dim());
    }

    /** An uppercase, letter-spaced column caption or eyebrow. */
    public static JLabel caption(String text) {
        return text(text.toUpperCase(), Theme.caption(), Theme.faint());
    }

    /** A label in the given role, which keeps its colour across enable/disable. */
    public static JLabel text(String value, Font font, Color color) {
        JLabel label = new FitLabel(value) {
            @Override
            public void setEnabled(boolean enabled) {
                super.setEnabled(enabled);
                setForeground(enabled ? color : Theme.muted(color));
            }
        };
        label.setFont(font);
        label.setForeground(color);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    /**
     * The width a string genuinely needs.
     *
     * Once a component is inside a realised window, the look and feel measures
     * text through the desktop's subpixel-antialiasing metrics, which report
     * narrower advances than the same string is then painted at - so a long
     * label is sized a few pixels short and silently loses its last word. This
     * measures against a plain, screen-independent render context, which is an
     * upper bound on what painting needs, and the callers take whichever is
     * larger.
     */
    private static int neededWidth(Font font, String text) {
        if (font == null || text == null || text.isEmpty() || text.startsWith("<html")) {
            return 0;
        }
        return (int) Math.ceil(font.getStringBounds(text, PLAIN).getWidth()) + 2;
    }

    private static final java.awt.font.FontRenderContext PLAIN =
            new java.awt.font.FontRenderContext(null, false, false);

    /** A JLabel sized for how its text actually paints. */
    public static class FitLabel extends JLabel {
        public FitLabel(String text) {
            super(text);
        }

        @Override
        public Dimension getPreferredSize() {
            Dimension size = super.getPreferredSize();
            Insets insets = getInsets();
            size.width = Math.max(size.width,
                    neededWidth(getFont(), getText()) + insets.left + insets.right);
            return size;
        }

        @Override
        public Dimension getMaximumSize() {
            return atLeastPreferred(super.getMaximumSize(), getPreferredSize());
        }
    }

    /**
     * An unset maximum size resolves to the look and feel's own idea of the
     * preferred size, which is computed without the correction above - so a
     * BoxLayout would clamp the component back to the too-narrow width. Keep
     * the maximum at or above what we actually need.
     */
    private static Dimension atLeastPreferred(Dimension maximum, Dimension preferred) {
        return new Dimension(Math.max(maximum.width, preferred.width),
                Math.max(maximum.height, preferred.height));
    }

    /**
     * A single line of monospace that truncates with an ellipsis instead of
     * forcing its container wider. Used for the live preview column, where the
     * text is unbounded but the column is not.
     */
    public static class Ellipsis extends JComponent {
        private String text = "";
        private Color color;
        private Font font;

        public Ellipsis(Font font, Color color) {
            this.font = font;
            this.color = color;
            setFont(font);
        }

        public void set(String text) {
            this.text = text == null ? "" : text;
            setToolTipText(this.text.isEmpty() ? null : this.text);
            repaint();
        }

        public void recolor(Color color) {
            this.color = color;
            repaint();
        }

        @Override
        public Dimension getPreferredSize() {
            FontMetrics metrics = getFontMetrics(font);
            // A deliberately small width so the layout gives this column its
            // share by weight rather than by content length.
            return new Dimension(60, metrics.getHeight());
        }

        @Override
        public Dimension getMinimumSize() {
            return new Dimension(40, getPreferredSize().height);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setFont(font);
            g2.setColor(isEnabled() ? color : Theme.muted(color));
            FontMetrics metrics = g2.getFontMetrics();
            String shown = clip(text, metrics, getWidth());
            g2.drawString(shown, 0, (getHeight() - metrics.getHeight()) / 2 + metrics.getAscent());
            g2.dispose();
        }

        private static String clip(String text, FontMetrics metrics, int width) {
            // A little slack, because the metrics used here measure narrower
            // than the same string paints - see neededWidth above.
            int room = width - 4;
            if (metrics.stringWidth(text) <= room) {
                return text;
            }
            String ellipsis = "…";
            room -= metrics.stringWidth(ellipsis);
            if (room <= 0) {
                return "";
            }
            int end = text.length();
            while (end > 0 && metrics.stringWidth(text.substring(0, end)) > room) {
                end--;
            }
            return text.substring(0, end) + ellipsis;
        }
    }

    // ------------------------------------------------------------- controls

    /**
     * A checkbox drawn by us rather than by the platform, so the tick matches
     * the accent and the box matches the input radius everywhere else.
     */
    public static JCheckBox checkBox(String text, boolean selected) {
        JCheckBox box = new JCheckBox(text, selected) {
            @Override
            public Dimension getPreferredSize() {
                Dimension size = super.getPreferredSize();
                Insets insets = getInsets();
                int glyph = getIcon() == null ? 0 : getIcon().getIconWidth() + getIconTextGap();
                size.width = Math.max(size.width,
                        neededWidth(getFont(), getText()) + glyph + insets.left + insets.right);
                return size;
            }

            @Override
            public Dimension getMaximumSize() {
                return atLeastPreferred(super.getMaximumSize(), getPreferredSize());
            }
        };
        box.setOpaque(false);
        box.setFont(Theme.body());
        box.setForeground(Theme.text());
        box.setFocusPainted(false);
        box.setIcon(new CheckIcon(false));
        box.setSelectedIcon(new CheckIcon(true));
        box.setDisabledIcon(new CheckIcon(false));
        box.setDisabledSelectedIcon(new CheckIcon(true));
        box.setIconTextGap(Theme.SPACE_SM);
        box.setCursor(new Cursor(Cursor.HAND_CURSOR));
        box.addChangeListener(e -> box.setForeground(box.isEnabled() ? Theme.text() : Theme.muted(Theme.text())));
        return box;
    }

    /** The box-and-tick glyph. */
    private static class CheckIcon implements Icon {
        private static final int SIZE = 15;
        private final boolean checked;

        CheckIcon(boolean checked) {
            this.checked = checked;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            boolean on = c.isEnabled();
            if (checked) {
                g2.setColor(on ? Theme.accent() : Theme.muted(Theme.accent()));
                g2.fillRoundRect(x, y, SIZE, SIZE, Theme.RADIUS_SM, Theme.RADIUS_SM);
                g2.setColor(Theme.onAccent());
                g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                Path2D tick = new Path2D.Float();
                tick.moveTo(x + SIZE * 0.26f, y + SIZE * 0.52f);
                tick.lineTo(x + SIZE * 0.44f, y + SIZE * 0.70f);
                tick.lineTo(x + SIZE * 0.76f, y + SIZE * 0.31f);
                g2.draw(tick);
            } else {
                g2.setColor(on ? Theme.well() : Theme.canvas());
                g2.fillRoundRect(x, y, SIZE, SIZE, Theme.RADIUS_SM, Theme.RADIUS_SM);
                g2.setColor(on ? Theme.lineStrong() : Theme.line());
                g2.drawRoundRect(x, y, SIZE - 1, SIZE - 1, Theme.RADIUS_SM, Theme.RADIUS_SM);
            }
            g2.dispose();
        }

        @Override
        public int getIconWidth() {
            return SIZE;
        }

        @Override
        public int getIconHeight() {
            return SIZE;
        }
    }

    /** Solid accent button - the one obvious action in a toolbar. */
    public static JButton primaryButton(String text, ActionListener action) {
        return button(text, action, true);
    }

    /** Outlined button - the supporting actions beside a primary one. */
    public static JButton ghostButton(String text, ActionListener action) {
        return button(text, action, false);
    }

    private static JButton button(String text, ActionListener action, boolean filled) {
        JButton button = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                boolean hover = getModel().isRollover() && isEnabled();
                boolean down = getModel().isPressed() && isEnabled();
                int w = getWidth();
                int h = getHeight();
                if (filled) {
                    Color fill = !isEnabled() ? Theme.muted(Theme.accent())
                            : down ? Theme.darken(Theme.accent(), 0.12f)
                            : hover ? Theme.lighten(Theme.accent(), 0.10f)
                            : Theme.accent();
                    g2.setColor(fill);
                    g2.fillRoundRect(0, 0, w, h, Theme.RADIUS_MD, Theme.RADIUS_MD);
                    setForeground(isEnabled() ? Theme.onAccent() : Theme.mix(Theme.onAccent(), Theme.canvas(), 0.4f));
                } else {
                    if (hover || down) {
                        g2.setColor(down ? Theme.accentSoft() : Theme.raised());
                        g2.fillRoundRect(0, 0, w, h, Theme.RADIUS_MD, Theme.RADIUS_MD);
                    }
                    g2.setColor(isEnabled() ? (hover ? Theme.accent() : Theme.line()) : Theme.line());
                    g2.drawRoundRect(0, 0, w - 1, h - 1, Theme.RADIUS_MD, Theme.RADIUS_MD);
                    setForeground(!isEnabled() ? Theme.muted(Theme.text()) : hover ? Theme.accent() : Theme.text());
                }
                if (isFocusOwner()) {
                    g2.setColor(Theme.accent());
                    g2.setStroke(new BasicStroke(1.5f));
                    g2.drawRoundRect(1, 1, w - 3, h - 3, Theme.RADIUS_MD, Theme.RADIUS_MD);
                }
                g2.dispose();
                super.paintComponent(g);
            }
        };
        button.setFont(Theme.strong());
        button.setFocusPainted(false);
        button.setContentAreaFilled(false);
        button.setBorderPainted(false);
        button.setRolloverEnabled(true);
        button.setBorder(BorderFactory.createEmptyBorder(6, 14, 6, 14));
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        button.addActionListener(action);
        return button;
    }

    /** A blue-in-spirit clickable label that opens a URL in the browser. */
    public static JLabel link(String text, String url) {
        return clickable(text, () -> {
            try {
                Desktop.getDesktop().browse(URI.create(url));
            } catch (Exception ignored) {
                // No browser available; the tooltip still shows the URL.
            }
        }, url);
    }

    /** A link-styled label that runs an action instead of opening a URL. */
    public static JLabel clickable(String text, Runnable action, String tooltip) {
        JLabel label = new FitLabel(text);
        Font plain = Theme.small();
        Map<TextAttribute, Object> attributes = new HashMap<TextAttribute, Object>(plain.getAttributes());
        attributes.put(TextAttribute.UNDERLINE, TextAttribute.UNDERLINE_ON);
        Font underlined = plain.deriveFont(attributes);
        label.setFont(plain);
        label.setForeground(Theme.accent());
        label.setCursor(new Cursor(Cursor.HAND_CURSOR));
        if (tooltip != null) {
            label.setToolTipText(tooltip);
        }
        label.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (label.isEnabled()) {
                    action.run();
                }
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                if (label.isEnabled()) {
                    label.setFont(underlined);
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                label.setFont(plain);
            }
        });
        return label;
    }

    /** A small rounded chip: version tags, ON/OFF state, token names. */
    public static class Chip extends FitLabel {
        private Color tint;
        private final boolean solid;

        public Chip(String text, Color tint) {
            this(text, tint, false);
        }

        public Chip(String text, Color tint, boolean solid) {
            super(text);
            this.tint = tint;
            this.solid = solid;
            setFont(Theme.caption());
            setForeground(solid ? Theme.onAccent() : tint);
            setBorder(BorderFactory.createEmptyBorder(3, 8, 3, 8));
            setAlignmentY(CENTER_ALIGNMENT);
        }

        public void set(String text, Color tint) {
            this.tint = tint;
            setText(text);
            setForeground(solid ? Theme.onAccent() : tint);
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
            g2.setColor(solid ? tint : Theme.alpha(tint, Theme.isDark() ? 42 : 26));
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), Theme.RADIUS_SM + 2, Theme.RADIUS_SM + 2);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    // ------------------------------------------------------------- layout

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
    public static JComponent hairline() {
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
}
