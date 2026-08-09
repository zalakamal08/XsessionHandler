package burp.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.ActionListener;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * A master switch and its live counter, as one block in the header.
 *
 * The count is the reason this sits in the header rather than on a tab: it is
 * the only continuous evidence that the extension is doing anything, so it is
 * set in large tabular figures and tinted with the accent while the feature is
 * running, and drains to a quiet neutral the moment it is paused.
 */
public class Meter extends JPanel {

    private final SwitchButton toggle;
    private final JLabel state = Ui.caption("On");
    private final JLabel value = new JLabel("0");
    private final JLabel unit;
    private boolean on;

    public Meter(String title, String unitText, boolean initiallyOn, ActionListener onToggle) {
        this.on = initiallyOn;
        this.unit = Ui.caption(unitText);

        setLayout(new BorderLayout(0, Theme.SPACE_XS));
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(Theme.SPACE_SM, Theme.SPACE_MD, Theme.SPACE_SM, Theme.SPACE_MD));

        toggle = new SwitchButton(initiallyOn);
        toggle.addActionListener(onToggle);

        JPanel top = Ui.row(Theme.SPACE_SM);
        top.add(toggle);
        JLabel name = Ui.caption(title);
        name.setForeground(Theme.text());
        top.add(name);
        top.add(state);

        value.setFont(Theme.metric());
        JPanel bottom = Ui.row(Theme.SPACE_SM);
        bottom.add(value);
        bottom.add(unit);

        add(top, BorderLayout.NORTH);
        add(bottom, BorderLayout.SOUTH);
        setState(initiallyOn);
    }

    public SwitchButton toggle() {
        return toggle;
    }

    public boolean isOn() {
        return toggle.isSelected();
    }

    /** Repaints the block for the given running state. */
    public void setState(boolean running) {
        this.on = running;
        state.setText(running ? "On" : "Off");
        state.setForeground(running ? Theme.accent() : Theme.faint());
        value.setForeground(running ? Theme.text() : Theme.faint());
        unit.setForeground(running ? Theme.dim() : Theme.faint());
        repaint();
    }

    public void setCount(long count) {
        value.setText(String.valueOf(count));
    }

    @Override
    public Dimension getMaximumSize() {
        return new Dimension(260, getPreferredSize().height);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(on ? Theme.accentSoft() : Theme.canvas());
        g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, Theme.RADIUS_LG, Theme.RADIUS_LG);
        g2.setColor(on ? Theme.alpha(Theme.accent(), 110) : Theme.line());
        g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, Theme.RADIUS_LG, Theme.RADIUS_LG);
        g2.dispose();
    }

    @Override
    public float getAlignmentY() {
        return Component.CENTER_ALIGNMENT;
    }
}
