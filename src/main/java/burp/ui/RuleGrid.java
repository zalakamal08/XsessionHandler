package burp.ui;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.KeyboardFocusManager;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JComponent;
import javax.swing.JPanel;

/**
 * The dense settings grid: captioned columns, one rule per line.
 *
 * Two things make this readable at density. Rows alternate against a faint
 * stripe so the eye can track a single rule across five columns, and the row
 * holding keyboard focus is washed in the accent - which doubles as the focus
 * indicator that a grid of borderless cells would otherwise lack.
 */
public class RuleGrid extends JPanel {

    private static final int ROW_PAD = 3;

    private final float[] weights;
    private final List<List<Component>> rows = new ArrayList<List<Component>>();
    private int focusedRow = -1;

    /**
     * @param captions one per column; an empty string leaves the column
     *                 uncaptioned but still reserves its slot
     * @param weights  horizontal share per column; 0 means "size to content"
     */
    public RuleGrid(String[] captions, float[] weights) {
        this.weights = weights;
        setLayout(new GridBagLayout());
        setOpaque(false);
        setAlignmentX(Component.LEFT_ALIGNMENT);

        GridBagConstraints c = new GridBagConstraints();
        c.gridy = 0;
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(0, Theme.SPACE_SM, Theme.SPACE_SM, Theme.SPACE_MD);
        boolean anyCaption = false;
        for (String caption : captions) {
            if (!caption.isEmpty()) {
                anyCaption = true;
            }
        }
        if (anyCaption) {
            for (int i = 0; i < captions.length; i++) {
                c.gridx = i;
                c.weightx = weights[i];
                c.fill = weights[i] > 0 ? GridBagConstraints.HORIZONTAL : GridBagConstraints.NONE;
                add(captions[i].isEmpty() ? Box() : Ui.caption(captions[i]), c);
            }
        }

        KeyboardFocusManager.getCurrentKeyboardFocusManager()
                .addPropertyChangeListener("focusOwner", new PropertyChangeListener() {
                    @Override
                    public void propertyChange(PropertyChangeEvent event) {
                        updateFocusedRow(event.getNewValue());
                    }
                });
    }

    private static JComponent Box() {
        JPanel spacer = new JPanel();
        spacer.setOpaque(false);
        spacer.setPreferredSize(new Dimension(0, 0));
        return spacer;
    }

    /**
     * Adds one rule line. Cells line up with the captions passed to the
     * constructor; a null cell leaves that column empty on this line.
     */
    public RuleGrid row(Component... cells) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = rows.size() + 1;
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(ROW_PAD, Theme.SPACE_SM, ROW_PAD, Theme.SPACE_MD);

        List<Component> line = new ArrayList<Component>();
        for (int i = 0; i < cells.length; i++) {
            Component cell = cells[i] == null ? Box() : cells[i];
            c.gridx = i;
            c.weightx = i < weights.length ? weights[i] : 0;
            c.fill = c.weightx > 0 ? GridBagConstraints.HORIZONTAL : GridBagConstraints.NONE;
            add(cell, c);
            line.add(cell);
        }
        rows.add(line);
        return this;
    }

    private void updateFocusedRow(Object focusOwner) {
        int found = -1;
        if (focusOwner instanceof Component) {
            for (int i = 0; i < rows.size() && found < 0; i++) {
                for (Component cell : rows.get(i)) {
                    if (cell == focusOwner || isAncestor(cell, (Component) focusOwner)) {
                        found = i;
                        break;
                    }
                }
            }
        }
        if (found != focusedRow) {
            focusedRow = found;
            repaint();
        }
    }

    private static boolean isAncestor(Component possibleParent, Component child) {
        return possibleParent instanceof Container && ((Container) possibleParent).isAncestorOf(child);
    }

    /** The vertical extent of a row, taken from the cells actually laid out. */
    private Rectangle bandOf(int index) {
        int top = Integer.MAX_VALUE;
        int bottom = Integer.MIN_VALUE;
        for (Component cell : rows.get(index)) {
            if (cell.getHeight() == 0) {
                continue;
            }
            top = Math.min(top, cell.getY());
            bottom = Math.max(bottom, cell.getY() + cell.getHeight());
        }
        if (top == Integer.MAX_VALUE) {
            return null;
        }
        return new Rectangle(0, top - ROW_PAD, getWidth(), bottom - top + ROW_PAD * 2);
    }

    @Override
    public Dimension getMaximumSize() {
        return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        for (int i = 0; i < rows.size(); i++) {
            Rectangle band = bandOf(i);
            if (band == null) {
                continue;
            }
            if (i == focusedRow && isEnabled()) {
                g2.setColor(Theme.accentSoft());
                g2.fillRoundRect(band.x, band.y, band.width, band.height, Theme.RADIUS_MD, Theme.RADIUS_MD);
                g2.setColor(Theme.accent());
                g2.fillRoundRect(band.x, band.y, 2, band.height, 2, 2);
            } else if (i % 2 == 1) {
                g2.setColor(Theme.raised());
                g2.fillRoundRect(band.x, band.y, band.width, band.height, Theme.RADIUS_MD, Theme.RADIUS_MD);
            }
        }
        g2.dispose();
        super.paintComponent(g);
    }
}
