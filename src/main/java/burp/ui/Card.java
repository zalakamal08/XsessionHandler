package burp.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

/** A rounded, outlined panel with a numbered heading - one step of the flow. */
public class Card extends JPanel {

    private static final int RADIUS = 10;

    private final JPanel content = new JPanel();
    private final JPanel headerRight = new JPanel();
    private boolean growable;

    public Card(String step, String title, String subtitle) {
        setLayout(new BorderLayout());
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(10, 12, 12, 12));
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

        if (subtitle != null) {
            JLabel subtitleLabel = new JLabel(subtitle);
            subtitleLabel.setFont(Theme.small());
            subtitleLabel.setForeground(Theme.muted());
            subtitleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            subtitleLabel.setBorder(BorderFactory.createEmptyBorder(3, step == null ? 0 : 30, 0, 0));
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

    /** Adds a control to the right hand side of the card heading. */
    public void addHeaderControl(JComponent component) {
        if (headerRight.getComponentCount() > 0) {
            headerRight.add(Box.createHorizontalStrut(10));
        }
        headerRight.add(component);
    }

    public Card add(JComponent component) {
        component.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(component);
        return this;
    }

    public Card gap(int height) {
        content.add(Box.createVerticalStrut(height));
        return this;
    }

    /** Lets this card take the leftover vertical space (used by the table). */
    public Card growable() {
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

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(Theme.card());
        g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, RADIUS, RADIUS);
        g2.setColor(Theme.line());
        g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, RADIUS, RADIUS);
        g2.dispose();
        super.paintComponent(g);
    }

    /** The small numbered circle in front of a card title. */
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
            g2.setColor(Theme.alpha(Theme.accent(), Theme.isDark() ? 60 : 32));
            g2.fillOval(0, 0, getWidth() - 1, getHeight() - 1);
            g2.setColor(Theme.accent());
            g2.setFont(getFont());
            java.awt.FontMetrics fm = g2.getFontMetrics();
            int x = (getWidth() - fm.stringWidth(text)) / 2;
            int y = (getHeight() - fm.getHeight()) / 2 + fm.getAscent();
            g2.drawString(text, x, y);
            g2.dispose();
        }
    }
}
