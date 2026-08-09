package burp.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.KeyStroke;

/**
 * The vertical navigation down the left of the tab. Each destination gets an
 * icon, a name and an optional live count; the selected one is marked by an
 * accent bar on its leading edge plus a soft accent wash, so the current
 * location survives being read at a glance.
 */
public class Rail extends JPanel {

    /** A small stroked icon. Drawn rather than shipped, so it inherits theme colour. */
    public interface Glyph {
        void paint(Graphics2D g2, int size);
    }

    private final List<Item> items = new ArrayList<Item>();
    private final Consumer<String> onSelect;
    private String selectedKey;

    public Rail(Consumer<String> onSelect) {
        this.onSelect = onSelect;
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(Theme.SPACE_MD, 0, Theme.SPACE_MD, 0));
        setPreferredSize(new Dimension(Theme.RAIL_WIDTH, 0));
        setMinimumSize(new Dimension(Theme.RAIL_WIDTH, 0));
        setMaximumSize(new Dimension(Theme.RAIL_WIDTH, Integer.MAX_VALUE));
        setFocusable(true);

        JComponent caption = Ui.caption("Sections");
        caption.setBorder(BorderFactory.createEmptyBorder(0, Theme.SPACE_LG, Theme.SPACE_SM, Theme.SPACE_LG));
        caption.setAlignmentX(Component.LEFT_ALIGNMENT);
        add(caption);

        getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), "next");
        getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), "previous");
        getActionMap().put("next", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                step(1);
            }
        });
        getActionMap().put("previous", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                step(-1);
            }
        });
    }

    public Rail item(String key, String title, Glyph glyph) {
        Item item = new Item(key, title, glyph);
        items.add(item);
        add(item);
        add(Box.createVerticalStrut(2));
        if (selectedKey == null) {
            select(key);
        }
        return this;
    }

    public void select(String key) {
        selectedKey = key;
        for (Item item : items) {
            item.repaint();
        }
        if (onSelect != null) {
            onSelect.accept(key);
        }
    }

    /** Sets the live count shown on the right of a destination; 0 hides it. */
    public void badge(String key, int count) {
        for (Item item : items) {
            if (item.key.equals(key)) {
                item.count = count;
                item.repaint();
            }
        }
    }

    private void step(int direction) {
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).key.equals(selectedKey)) {
                int next = Math.floorMod(i + direction, items.size());
                select(items.get(next).key);
                return;
            }
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setColor(Theme.surface());
        g2.fillRect(0, 0, getWidth(), getHeight());
        g2.setColor(Theme.lineStrong());
        g2.drawLine(getWidth() - 1, 0, getWidth() - 1, getHeight());
        g2.dispose();
    }

    /** One destination in the rail. */
    private class Item extends JComponent {
        private static final int HEIGHT = 38;
        private static final int ICON = 16;
        private static final int BAR = 3;

        private final String key;
        private final String title;
        private final Glyph glyph;
        private boolean hover;
        private int count;

        Item(String key, String title, Glyph glyph) {
            this.key = key;
            this.title = title;
            this.glyph = glyph;
            setCursor(new Cursor(Cursor.HAND_CURSOR));
            setAlignmentX(Component.LEFT_ALIGNMENT);
            setPreferredSize(new Dimension(Theme.RAIL_WIDTH, HEIGHT));
            setMinimumSize(new Dimension(Theme.RAIL_WIDTH, HEIGHT));
            setMaximumSize(new Dimension(Theme.RAIL_WIDTH, HEIGHT));
            setToolTipText(title);
            addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    Rail.this.requestFocusInWindow();
                    select(key);
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
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            boolean active = key.equals(selectedKey);
            int w = getWidth();
            int h = getHeight();

            if (active) {
                g2.setColor(Theme.accentSoft());
                g2.fillRect(0, 0, w - 1, h);
                g2.setColor(Theme.accent());
                g2.fillRect(0, 0, BAR, h);
            } else if (hover) {
                g2.setColor(Theme.raised());
                g2.fillRect(0, 0, w - 1, h);
            }

            Color ink = active ? Theme.accent() : hover ? Theme.text() : Theme.dim();

            int iconX = Theme.SPACE_LG;
            int iconY = (h - ICON) / 2;
            Graphics2D icon = (Graphics2D) g2.create(iconX, iconY, ICON + 2, ICON + 2);
            icon.setColor(ink);
            icon.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            glyph.paint(icon, ICON);
            icon.dispose();

            g2.setFont(active ? Theme.strong() : Theme.body());
            g2.setColor(ink);
            FontMetrics metrics = g2.getFontMetrics();
            int textX = iconX + ICON + Theme.SPACE_MD;
            g2.drawString(title, textX, (h - metrics.getHeight()) / 2 + metrics.getAscent());

            if (count > 0) {
                String badge = String.valueOf(count);
                g2.setFont(Theme.caption());
                FontMetrics badgeMetrics = g2.getFontMetrics();
                int badgeWidth = badgeMetrics.stringWidth(badge) + 12;
                int badgeHeight = badgeMetrics.getHeight() + 2;
                int badgeX = w - badgeWidth - Theme.SPACE_LG;
                int badgeY = (h - badgeHeight) / 2;
                g2.setColor(active ? Theme.accent() : Theme.alpha(Theme.dim(), 40));
                g2.fillRoundRect(badgeX, badgeY, badgeWidth, badgeHeight, badgeHeight, badgeHeight);
                g2.setColor(active ? Theme.onAccent() : Theme.dim());
                g2.drawString(badge, badgeX + 6, badgeY + badgeMetrics.getAscent() + 1);
            }
            g2.dispose();
        }
    }

    // ----------------------------------------------------------------- icons

    /** Capture: a stream arriving and being funnelled down into a tray. */
    public static final Glyph CAPTURE = (g2, size) -> {
        float unit = size / 16f;
        Path2D arrow = new Path2D.Float();
        arrow.moveTo(8 * unit, 2 * unit);
        arrow.lineTo(8 * unit, 9.5f * unit);
        g2.draw(arrow);
        Path2D head = new Path2D.Float();
        head.moveTo(4.8f * unit, 6.4f * unit);
        head.lineTo(8 * unit, 9.8f * unit);
        head.lineTo(11.2f * unit, 6.4f * unit);
        g2.draw(head);
        Path2D tray = new Path2D.Float();
        tray.moveTo(2.5f * unit, 11 * unit);
        tray.lineTo(2.5f * unit, 13.5f * unit);
        tray.lineTo(13.5f * unit, 13.5f * unit);
        tray.lineTo(13.5f * unit, 11 * unit);
        g2.draw(tray);
    };

    /** Inject: a payload arrow driven into a bracketed request. */
    public static final Glyph INJECT = (g2, size) -> {
        float unit = size / 16f;
        Path2D shaft = new Path2D.Float();
        shaft.moveTo(2 * unit, 8 * unit);
        shaft.lineTo(9.5f * unit, 8 * unit);
        g2.draw(shaft);
        Path2D head = new Path2D.Float();
        head.moveTo(6.6f * unit, 4.8f * unit);
        head.lineTo(9.9f * unit, 8 * unit);
        head.lineTo(6.6f * unit, 11.2f * unit);
        g2.draw(head);
        Path2D bracket = new Path2D.Float();
        bracket.moveTo(11.5f * unit, 2.5f * unit);
        bracket.lineTo(13.5f * unit, 2.5f * unit);
        bracket.lineTo(13.5f * unit, 13.5f * unit);
        bracket.lineTo(11.5f * unit, 13.5f * unit);
        g2.draw(bracket);
    };

    /** Tokens: a key - the thing being collected. */
    public static final Glyph TOKENS = (g2, size) -> {
        float unit = size / 16f;
        g2.drawOval(Math.round(2 * unit), Math.round(5.5f * unit), Math.round(5.5f * unit), Math.round(5.5f * unit));
        Path2D shaft = new Path2D.Float();
        shaft.moveTo(7.5f * unit, 8.25f * unit);
        shaft.lineTo(14 * unit, 8.25f * unit);
        g2.draw(shaft);
        Path2D tooth1 = new Path2D.Float();
        tooth1.moveTo(11.5f * unit, 8.25f * unit);
        tooth1.lineTo(11.5f * unit, 11 * unit);
        g2.draw(tooth1);
        Path2D tooth2 = new Path2D.Float();
        tooth2.moveTo(13.8f * unit, 8.25f * unit);
        tooth2.lineTo(13.8f * unit, 10.2f * unit);
        g2.draw(tooth2);
    };
}
