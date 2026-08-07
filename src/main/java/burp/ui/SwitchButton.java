package burp.ui;

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

/** A painted on/off switch - the master control for a whole feature. */
public class SwitchButton extends JComponent {

    private static final int WIDTH = 38;
    private static final int HEIGHT = 20;

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

        int h = getHeight();
        int w = getWidth();
        Color track;
        if (!isEnabled()) {
            track = Theme.mix(Theme.muted(), Theme.background(), 0.6f);
        } else if (selected) {
            track = hover ? Theme.success().brighter() : Theme.success();
        } else {
            track = hover ? Theme.mix(Theme.muted(), Theme.background(), 0.2f) : Theme.muted();
        }
        g2.setColor(track);
        g2.fillRoundRect(0, 0, w, h, h, h);

        if (isFocusOwner()) {
            g2.setColor(Theme.accent());
            g2.drawRoundRect(0, 0, w - 1, h - 1, h, h);
        }

        int knob = h - 6;
        int x = selected ? w - knob - 3 : 3;
        g2.setColor(Theme.isDark() && !isEnabled() ? Theme.mix(Color.WHITE, Theme.background(), 0.4f) : Color.WHITE);
        g2.fillOval(x, 3, knob, knob);
        g2.dispose();
    }
}
