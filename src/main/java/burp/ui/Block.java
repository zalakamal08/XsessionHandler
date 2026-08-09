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

/**
 * One titled block of settings.
 *
 * No card, no border, no fill - the title is set against a rule that runs to
 * the right edge, which separates blocks by rhythm instead of by boxes. Boxes
 * inside a tab that is already inside Burp's own boxes read as clutter; a rule
 * does the same job with none of the weight.
 */
public class Block extends JPanel {

    private final JPanel content = new JPanel();
    private final JPanel headerRight = new JPanel();
    private boolean growable;

    public Block(String step, String title, String subtitle) {
        setLayout(new BorderLayout());
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(0, 0, Theme.SPACE_XL, 0));
        setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel titles = new JPanel();
        titles.setOpaque(false);
        titles.setLayout(new BoxLayout(titles, BoxLayout.Y_AXIS));
        titles.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel titleRow = new JPanel(new BorderLayout(Theme.SPACE_MD, 0));
        titleRow.setOpaque(false);
        titleRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        titleRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));

        JPanel titleLeft = Ui.row(Theme.SPACE_SM);
        if (step != null) {
            titleLeft.add(new StepMark(step));
        }
        titleLeft.add(Ui.text(title, Theme.h2(), Theme.text()));

        headerRight.setOpaque(false);
        headerRight.setLayout(new BoxLayout(headerRight, BoxLayout.X_AXIS));

        titleRow.add(titleLeft, BorderLayout.WEST);
        titleRow.add(new Rule(), BorderLayout.CENTER);
        titleRow.add(headerRight, BorderLayout.EAST);
        titles.add(titleRow);

        if (subtitle != null) {
            JLabel subtitleLabel = Ui.text(subtitle, Theme.small(), Theme.dim());
            subtitleLabel.setBorder(BorderFactory.createEmptyBorder(Theme.SPACE_XS, 0, 0, 0));
            titles.add(subtitleLabel);
        }

        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(BorderFactory.createEmptyBorder(Theme.SPACE_MD, 0, 0, 0));

        add(titles, BorderLayout.NORTH);
        add(content, BorderLayout.CENTER);
    }

    /** Adds a control to the right hand end of the block heading. */
    public Block headerControl(JComponent component) {
        if (headerRight.getComponentCount() > 0) {
            headerRight.add(Box.createHorizontalStrut(Theme.SPACE_SM));
        }
        headerRight.add(component);
        return this;
    }

    public Block add(JComponent component) {
        component.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(component);
        return this;
    }

    public Block gap(int height) {
        content.add(Box.createVerticalStrut(height));
        return this;
    }

    /** Lets this block take the leftover vertical space (used by the table). */
    public Block growable() {
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

    /** The accent-tinted step number in front of a block title. */
    private static class StepMark extends JComponent {
        private final String text;

        StepMark(String text) {
            this.text = text;
            setFont(Theme.caption());
            int height = getFontMetrics(Theme.caption()).getHeight() + 6;
            Dimension size = new Dimension(height + 4, height);
            setPreferredSize(size);
            setMaximumSize(size);
            setMinimumSize(size);
            setAlignmentY(Component.CENTER_ALIGNMENT);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            boolean on = isEnabled();
            g2.setColor(on ? Theme.accentSoft() : Theme.alpha(Theme.faint(), 30));
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, Theme.RADIUS_SM, Theme.RADIUS_SM);
            g2.setColor(on ? Theme.accent() : Theme.faint());
            g2.setFont(getFont());
            java.awt.FontMetrics metrics = g2.getFontMetrics();
            int x = (getWidth() - metrics.stringWidth(text)) / 2;
            int y = (getHeight() - metrics.getHeight()) / 2 + metrics.getAscent();
            g2.drawString(text, x, y);
            g2.dispose();
        }
    }

    /** The hairline that carries the title out to the right edge. */
    private static class Rule extends JComponent {
        Rule() {
            setPreferredSize(new Dimension(40, 1));
        }

        @Override
        protected void paintComponent(Graphics g) {
            g.setColor(Theme.line());
            g.fillRect(0, getHeight() / 2, getWidth(), 1);
        }
    }
}
