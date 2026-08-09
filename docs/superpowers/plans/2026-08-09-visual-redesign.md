# Visual Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give the Capture/Inject tabs a clearer type hierarchy, more deliberate use of the accent color, and a flatter, less "boxy" layout — while keeping the theme-aware (light/dark) approach and the density work from v1.1 intact.

**Architecture:** Extend `Theme` with a slightly larger type scale and two new color tokens (`accentSoft()`, `onAccent()`). Add a new `burp.ui.Section` component that replaces `burp.ui.Card` on the Capture/Inject tabs — same shape (step/title/subtitle/add/gap) but flat: bold heading + short accent rule, no border or fill. Add a `Ui.filledButton()` helper for primary actions (Tokens tab toolbar). Delete `Card.java` once nothing references it.

**Tech Stack:** Java 17, Swing (legacy Burp Extender API), Maven (`C:\tools\apache-maven-3.9.9\bin\mvn.cmd` — not on PATH in this environment, prefix commands with it or add to PATH for the session). No test framework in this repo — verification is `mvn clean package` plus a manual check in Burp.

---

### Task 1: Extend `Theme` — type scale and new color tokens

**Files:**
- Modify: `src/main/java/burp/ui/Theme.java`

- [ ] **Step 1: Bump the base font size and heading size, raise the `small()` floor**

Replace:

```java
    public static Font base() {
        return orDefault(UIManager.getFont("Label.font"), new Font(Font.SANS_SERIF, Font.PLAIN, 12));
    }

    public static Font bold() {
        return base().deriveFont(Font.BOLD);
    }

    public static Font title() {
        return base().deriveFont(Font.BOLD, base().getSize2D() + 3f);
    }

    public static Font heading() {
        return base().deriveFont(Font.BOLD, base().getSize2D() + 1f);
    }

    public static Font small() {
        return base().deriveFont(Math.max(9f, base().getSize2D() - 1f));
    }
```

with:

```java
    public static Font base() {
        Font uiFont = orDefault(UIManager.getFont("Label.font"), new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        return uiFont.deriveFont(uiFont.getSize2D() + 1f);
    }

    public static Font bold() {
        return base().deriveFont(Font.BOLD);
    }

    public static Font title() {
        return base().deriveFont(Font.BOLD, base().getSize2D() + 3f);
    }

    public static Font heading() {
        return base().deriveFont(Font.BOLD, base().getSize2D() + 4f);
    }

    public static Font small() {
        return base().deriveFont(Math.max(10f, base().getSize2D() - 1f));
    }
```

- [ ] **Step 2: Add `accentSoft()` and `onAccent()` color tokens**

Replace:

```java
    public static Color accent() {
        return isDark() ? new Color(0x58A6FF) : new Color(0x0969DA);
    }
```

with:

```java
    public static Color accent() {
        return isDark() ? new Color(0x58A6FF) : new Color(0x0969DA);
    }

    /** A low-alpha tint of the accent color, for soft fills (badges, rules' hover state). */
    public static Color accentSoft() {
        return alpha(accent(), isDark() ? 60 : 32);
    }

    /** Near-white text/icon color for use on top of a solid accent() fill. */
    public static Color onAccent() {
        return Color.WHITE;
    }
```

- [ ] **Step 3: Build to verify it compiles**

Run: `& "C:\tools\apache-maven-3.9.9\bin\mvn.cmd" clean package` (PowerShell) or `mvn clean package` if `mvn` is already on PATH.
Expected: `BUILD SUCCESS`

- [ ] **Step 4: Commit**

```bash
git add src/main/java/burp/ui/Theme.java
git commit -m "feat: expand Theme type scale and add accent color tokens

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 2: Add `Ui.filledButton()` for primary actions

**Files:**
- Modify: `src/main/java/burp/ui/Ui.java`

- [ ] **Step 1: Add the missing imports**

Replace:

```java
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
```

with:

```java
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
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.font.TextAttribute;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
```

- [ ] **Step 2: Add the `filledButton()` factory method**

Replace:

```java
    public static JCheckBox checkBox(String text, boolean selected) {
        JCheckBox box = new JCheckBox(text, selected);
        box.setOpaque(false);
        box.setFont(Theme.base());
        box.setForeground(Theme.foreground());
        box.setFocusPainted(false);
        return box;
    }
```

with:

```java
    public static JCheckBox checkBox(String text, boolean selected) {
        JCheckBox box = new JCheckBox(text, selected);
        box.setOpaque(false);
        box.setFont(Theme.base());
        box.setForeground(Theme.foreground());
        box.setFocusPainted(false);
        return box;
    }

    /** A solid, accent-colored button for primary actions (Copy / Remove / Clear, etc). */
    public static JButton filledButton(String text, ActionListener action) {
        JButton button = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color fill;
                if (!isEnabled()) {
                    fill = Theme.mix(Theme.accent(), Theme.background(), 0.6f);
                } else if (getModel().isRollover()) {
                    fill = Theme.accent().brighter();
                } else {
                    fill = Theme.accent();
                }
                g2.setColor(fill);
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        button.setFont(Theme.bold());
        button.setForeground(Theme.onAccent());
        button.setFocusPainted(false);
        button.setContentAreaFilled(false);
        button.setBorderPainted(false);
        button.setBorder(BorderFactory.createEmptyBorder(6, 14, 6, 14));
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        button.addActionListener(action);
        return button;
    }
```

- [ ] **Step 3: Build to verify it compiles**

Run: `mvn clean package`
Expected: `BUILD SUCCESS`

- [ ] **Step 4: Commit**

```bash
git add src/main/java/burp/ui/Ui.java
git commit -m "feat: add Ui.filledButton() for accent-colored primary actions

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 3: Add `Section` component, delete `Card`

**Files:**
- Create: `src/main/java/burp/ui/Section.java`
- Delete: `src/main/java/burp/ui/Card.java`

- [ ] **Step 1: Create `Section.java`**

```java
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
 * {@link Card} look on the Capture/Inject tabs with a cleaner, less "boxy"
 * layout, while keeping the same shape (step/title/subtitle/add/gap) so
 * callers barely change.
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
```

- [ ] **Step 2: Delete `Card.java`**

`Card` will have no remaining callers after Task 4 migrates `MyTab` to `Section`. Delete it now so the build fails loudly in the meantime if anything was missed:

```bash
git rm src/main/java/burp/ui/Card.java
```

- [ ] **Step 3: Commit (build is expected to fail until Task 4 lands — that's fine, this is one logical change split across two tasks for reviewability)**

```bash
git add src/main/java/burp/ui/Section.java
git commit -m "feat: add flat Section component, remove boxed Card

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 4: Migrate `MyTab` from `Card` to `Section`, use `filledButton` for toolbar actions

**Files:**
- Modify: `src/main/java/burp/MyTab.java`

- [ ] **Step 1: Update the import**

Replace:

```java
import burp.ui.Card;
import burp.ui.SwitchButton;
import burp.ui.Theme;
import burp.ui.Ui;
import burp.ui.UiTextField;
```

with:

```java
import burp.ui.Section;
import burp.ui.SwitchButton;
import burp.ui.Theme;
import burp.ui.Ui;
import burp.ui.UiTextField;
```

- [ ] **Step 2: Swap the 4 `Card` usages in `buildCaptureTab()`**

Replace:

```java
        Card filters = new Card("1", "Which responses to read",
```

with:

```java
        Section filters = new Section("1", "Which responses to read",
```

Replace:

```java
        Card extract = new Card("2", "What to pull out",
```

with:

```java
        Section extract = new Section("2", "What to pull out",
```

Replace:

```java
        Card scope = new Card("3", "Testing more than one app at once?",
```

with:

```java
        Section scope = new Section("3", "Testing more than one app at once?",
```

Replace:

```java
        Card debug = new Card("4", "Not seeing your token?", "Turn this on to find out which check is rejecting it.");
```

with:

```java
        Section debug = new Section("4", "Not seeing your token?", "Turn this on to find out which check is rejecting it.");
```

- [ ] **Step 3: Swap the 3 `Card` usages in `buildInjectTab()`**

Replace:

```java
        Card tools = new Card("1", "Which Burp tools to rewrite",
```

with:

```java
        Section tools = new Section("1", "Which Burp tools to rewrite",
```

Replace:

```java
        Card token1 = new Card("2", "TOKEN-1 goes here", "JWT / access token. Tick every position your app uses.");
```

with:

```java
        Section token1 = new Section("2", "TOKEN-1 goes here", "JWT / access token. Tick every position your app uses.");
```

Replace:

```java
        Card token2 = new Card("3", "TOKEN-2 goes here", "Refresh / CSRF token.");
```

with:

```java
        Section token2 = new Section("3", "TOKEN-2 goes here", "Refresh / CSRF token.");
```

- [ ] **Step 4: Use `Ui.filledButton()` for the Tokens tab toolbar**

Replace:

```java
    private JButton button(String text, java.awt.event.ActionListener action) {
        JButton button = new JButton(text);
        button.setFont(Theme.base());
        button.setFocusPainted(false);
        button.addActionListener(action);
        return button;
    }
```

with:

```java
    private JButton button(String text, java.awt.event.ActionListener action) {
        return Ui.filledButton(text, action);
    }
```

- [ ] **Step 5: Build to verify it compiles**

Run: `mvn clean package`
Expected: `BUILD SUCCESS`

- [ ] **Step 6: Commit**

```bash
git add src/main/java/burp/MyTab.java
git commit -m "refactor: migrate MyTab from boxed Card to flat Section

Also switches the Tokens tab's Copy/Remove/Clear buttons to the new
filled accent-colored button style so primary actions read as
actionable rather than plain text buttons.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 5: Final build and manual verification

**Files:** none (verification only)

- [ ] **Step 1: Full clean build**

Run: `mvn clean package`
Expected: `BUILD SUCCESS`, jar written to `target/session-handler-plus.jar`

- [ ] **Step 2: Manual check in Burp (light and dark theme)**

Load `target/session-handler-plus.jar` in Burp Suite and confirm:
- Section headings on Capture/Inject are clearly bigger/bolder than before, each with a short colored accent rule beneath it, and no bordered box around the section.
- Body text and hints are legible and each tier (heading/label/hint) is visually distinct.
- The accent color shows up with intent: section accent rules, step badges, and the Tokens tab's Copy/Remove/Clear buttons are now solid accent-filled buttons.
- The Inject tab's tooltip preview (from v1.1) and the tightened spacing still work as before — no regressions.
- Both light and dark Burp themes render correctly with no hardcoded colors clashing.

- [ ] **Step 3: Report result**

If everything checks out, this plan is complete. If any manual check fails, note which one and fix before considering the work done.
