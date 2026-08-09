package burp.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JComponent;
import javax.swing.KeyStroke;

/**
 * The painted on/off switch behind a whole feature. Off is a hollow track so
 * "paused" reads as absence rather than as another colour; on fills with the
 * accent and slides the knob across.
 */
public class SwitchButton extends JComponent {

    private static final int WIDTH = 42;
    private static final int HEIGHT = 22;
    private static final int PAD = 3;

    private final List<ActionListener> listeners = new ArrayList<ActionListener>();
    private boolean selected;
    private boolean hover;

    public SwitchButton(boolean selected) {
        this.selected = selected;
        setOpaque(false);
        setFocusable(true);
        setCursor(new Cursor(Cursor.HAND_CURSOR));
        setPreferredSize(new Dimension(WIDTH, HEIGHT));
        setMinimumSize(new Dimension(WIDTH, HEIGHT));
        setMaximumSize(new Dimension(WIDTH, HEIGHT));
        setAlignmentY(CENTER_ALIGNMENT);

        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (isEnabled()) {
                    requestFocusInWindow();
                    toggle();
                }
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                hover = true;
                repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hover = false;
                repaint();
            }
        });

        getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), "toggle");
        getActionMap().put("toggle", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (isEnabled()) {
                    toggle();
                }
            }
        });
    }

    private void toggle() {
        selected = !selected;
        repaint();
        ActionEvent event = new ActionEvent(this, ActionEvent.ACTION_PERFORMED, "toggle");
        for (ActionListener listener : new ArrayList<ActionListener>(listeners)) {
            listener.actionPerformed(event);
        }
    }

    public void addActionListener(ActionListener listener) {
        listeners.add(listener);
    }

    public boolean isSelected() {
        return selected;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();
        boolean on = isEnabled();

        if (selected) {
            Color fill = !on ? Theme.muted(Theme.accent())
                    : hover ? Theme.lighten(Theme.accent(), 0.10f) : Theme.accent();
            g2.setColor(fill);
            g2.fillRoundRect(0, 0, w, h, h, h);
        } else {
            g2.setColor(on ? Theme.well() : Theme.canvas());
            g2.fillRoundRect(0, 0, w, h, h, h);
            g2.setColor(on && hover ? Theme.lineStrong() : Theme.line());
            g2.setStroke(new BasicStroke(1.4f));
            g2.drawRoundRect(1, 1, w - 2, h - 2, h - 2, h - 2);
        }

        int knob = h - PAD * 2;
        int x = selected ? w - knob - PAD : PAD;
        Color knobColor;
        if (selected) {
            knobColor = on ? Theme.onAccent() : Theme.mix(Theme.onAccent(), Theme.canvas(), 0.45f);
        } else {
            knobColor = on ? Theme.faint() : Theme.line();
        }
        g2.setColor(knobColor);
        g2.fillOval(x, PAD, knob, knob);

        if (isFocusOwner()) {
            g2.setColor(Theme.accent());
            g2.setStroke(new BasicStroke(1.5f));
            g2.drawRoundRect(-2, -2, w + 3, h + 3, h + 4, h + 4);
        }
        g2.dispose();
    }
}
