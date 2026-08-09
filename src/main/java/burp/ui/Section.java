package burp.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * A flat section: a bold heading with a short accent rule beneath it, and a
 * content area below - no border, no background fill. Replaces the boxed
 * card look on the Capture/Inject tabs with a cleaner, less "boxy" layout,
 * while keeping the same shape (step/title/subtitle/add/gap) so callers
 * barely change.
 */
public class Section extends JPanel {

    private static final int RULE_HEIGHT = 3;
    private static final int RULE_WIDTH = 34;

    private final JPanel content = new JPanel();
    private final JPanel headerRight = new JPanel();
    private boolean growable;

    public Section(String step, String title, String subtitle) {
        setLayout(new BorderLayout());
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(4, 0, 10, 0));
        setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel header = new JPanel(new BorderLayout(10, 0));
        header.setOpaque(false);

        JPanel titles = new JPanel();
        titles.setOpaque(false);
        titles.setLayout(new BoxLayout(titles, BoxLayout.Y_AXIS));

        JPanel titleRow = new JPanel();
        titleRow.setOpaque(false);
        titleRow.setLayout(new BoxLayout(titleRow, BoxLayout.X_AXIS));
        titleRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        if (step != null) {
            titleRow.add(new StepBadge(step));
            titleRow.add(Box.createHorizontalStrut(8));
        }
        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(Theme.heading());
        titleLabel.setForeground(Theme.foreground());
        titleRow.add(titleLabel);
        titles.add(titleRow);

        titles.add(Box.createVerticalStrut(4));
        JComponent rule = new AccentRule();
        rule.setAlignmentX(Component.LEFT_ALIGNMENT);
        titles.add(rule);

        if (subtitle != null) {
            JLabel subtitleLabel = new JLabel(subtitle);
            subtitleLabel.setFont(Theme.small());
            subtitleLabel.setForeground(Theme.muted());
            subtitleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            subtitleLabel.setBorder(BorderFactory.createEmptyBorder(6, step == null ? 0 : 30, 0, 0));
            titles.add(subtitleLabel);
        }

        headerRight.setOpaque(false);
        headerRight.setLayout(new BoxLayout(headerRight, BoxLayout.X_AXIS));

        header.add(titles, BorderLayout.WEST);
        header.add(headerRight, BorderLayout.EAST);

        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));

        add(header, BorderLayout.NORTH);
        add(content, BorderLayout.CENTER);
    }

    /** Adds a control to the right hand side of the section heading. */
    public void addHeaderControl(JComponent component) {
        if (headerRight.getComponentCount() > 0) {
            headerRight.add(Box.createHorizontalStrut(10));
        }
        headerRight.add(component);
    }

    public Section add(JComponent component) {
        component.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(component);
        return this;
    }

    public Section gap(int height) {
        content.add(Box.createVerticalStrut(height));
        return this;
    }

    /** Lets this section take the leftover vertical space (used by the table). */
    public Section growable() {
        growable = true;
        return this;
    }

    @Override
    public Dimension getMaximumSize() {
        if (growable) {
            return super.getMaximumSize();
        }
        return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
    }

    /** The small numbered circle in front of a section title. */
    private static class StepBadge extends JComponent {
        private final String text;

        StepBadge(String text) {
            this.text = text;
            setFont(Theme.base().deriveFont(java.awt.Font.BOLD, Theme.base().getSize2D() - 1f));
            int size = getFontMetrics(getFont()).getHeight() + 8;
            setPreferredSize(new Dimension(size, size));
            setMaximumSize(new Dimension(size, size));
            setMinimumSize(new Dimension(size, size));
            setAlignmentY(Component.CENTER_ALIGNMENT);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setColor(Theme.accentSoft());
            g2.fillOval(0, 0, getWidth() - 1, getHeight() - 1);
            g2.setColor(Theme.accent());
            g2.setFont(getFont());
            FontMetrics fm = g2.getFontMetrics();
            int x = (getWidth() - fm.stringWidth(text)) / 2;
            int y = (getHeight() - fm.getHeight()) / 2 + fm.getAscent();
            g2.drawString(text, x, y);
            g2.dispose();
        }
    }

    /** The short colored rule under a section title. */
    private static class AccentRule extends JComponent {
        AccentRule() {
            setPreferredSize(new Dimension(RULE_WIDTH, RULE_HEIGHT));
            setMaximumSize(new Dimension(RULE_WIDTH, RULE_HEIGHT));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(Theme.accent());
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), RULE_HEIGHT, RULE_HEIGHT);
            g2.dispose();
        }
    }
}
