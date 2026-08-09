# UI Density Redesign + README Cleanup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reduce vertical scrolling on the Capture/Inject tabs, fix the broken hover-underline on the credit link, and remove the outdated "Injection in action" screenshots/videos from the README.

**Architecture:** No architectural change — this is a mechanical tightening pass over existing Swing UI spacing constants, a rework of one method (`MyTab.addReplace`) to move its live preview from an always-visible grid row into a tooltip, a small rewrite of `Ui.link()` to avoid HTML, and a doc/asset cleanup. No changes to `TokenStore`, `TokenReplacer`, or `MyHttpListener`.

**Tech Stack:** Java 17, Swing (legacy Burp Extender API), Maven. No test framework is present in this repo (`pom.xml` has no JUnit dependency, no `src/test`) — verification for each task is a clean `mvn clean package` build, with a final manual check in Burp (or a standalone Swing harness) at the end.

---

### Task 1: Fix the `Ui.link()` hover-underline bug

**Files:**
- Modify: `src/main/java/burp/ui/Ui.java`

The credit link ("@zalakamal08") currently fakes an underline on hover by swapping the label's text to `"<html><u>" + text + "</u></html>"`. Under Burp's current look-and-feel this HTML isn't rendered, so the literal tags show. Replace it with a real `Font` underline via `TextAttribute`, which never depends on HTML rendering.

- [ ] **Step 1: Update the import block**

In `src/main/java/burp/ui/Ui.java`, replace:

```java
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.net.URI;

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

- [ ] **Step 2: Replace the `link()` method body**

Replace:

```java
    /** A blue clickable label that opens a URL. */
    public static JLabel link(String text, String url) {
        JLabel label = new JLabel(text);
        label.setFont(Theme.base());
        label.setForeground(Theme.accent());
        label.setCursor(new Cursor(Cursor.HAND_CURSOR));
        label.setToolTipText(url);
        label.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                try {
                    Desktop.getDesktop().browse(URI.create(url));
                } catch (Exception ignored) {
                    // No browser available; the tooltip still shows the URL.
                }
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                label.setText("<html><u>" + text + "</u></html>");
            }

            @Override
            public void mouseExited(MouseEvent e) {
                label.setText(text);
            }
        });
        return label;
    }
```

with:

```java
    /** A blue clickable label that opens a URL. */
    public static JLabel link(String text, String url) {
        JLabel label = new JLabel(text);
        Font plainFont = Theme.base();
        Map<TextAttribute, Object> underlineAttrs = new HashMap<TextAttribute, Object>(plainFont.getAttributes());
        underlineAttrs.put(TextAttribute.UNDERLINE, TextAttribute.UNDERLINE_ON);
        Font underlinedFont = plainFont.deriveFont(underlineAttrs);
        label.setFont(plainFont);
        label.setForeground(Theme.accent());
        label.setCursor(new Cursor(Cursor.HAND_CURSOR));
        label.setToolTipText(url);
        label.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                try {
                    Desktop.getDesktop().browse(URI.create(url));
                } catch (Exception ignored) {
                    // No browser available; the tooltip still shows the URL.
                }
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                label.setFont(underlinedFont);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                label.setFont(plainFont);
            }
        });
        return label;
    }
```

- [ ] **Step 3: Build to verify it compiles**

Run: `mvn clean package`
Expected: `BUILD SUCCESS`

- [ ] **Step 4: Commit**

```bash
git add src/main/java/burp/ui/Ui.java
git commit -m "fix: replace HTML hover-underline with a real Font attribute

The credit link faked an underline by swapping in raw <html><u> text on
hover, which showed literal tags under look-and-feels that don't render
it. Use TextAttribute.UNDERLINE on the label's Font instead, which
can't depend on HTML rendering.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 2: Tighten `Card` padding

**Files:**
- Modify: `src/main/java/burp/ui/Card.java`

- [ ] **Step 1: Reduce the card's outer border**

Replace:

```java
        setBorder(BorderFactory.createEmptyBorder(10, 12, 12, 12));
```

with:

```java
        setBorder(BorderFactory.createEmptyBorder(7, 10, 8, 10));
```

- [ ] **Step 2: Reduce the content area's top border**

Replace:

```java
        content.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
```

with:

```java
        content.setBorder(BorderFactory.createEmptyBorder(7, 0, 0, 0));
```

- [ ] **Step 3: Build to verify it compiles**

Run: `mvn clean package`
Expected: `BUILD SUCCESS`

- [ ] **Step 4: Commit**

```bash
git add src/main/java/burp/ui/Card.java
git commit -m "style: tighten Card padding to reduce tab scrolling

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 3: Tighten `MyTab` spacing (tab padding, card gaps, setting-row insets)

**Files:**
- Modify: `src/main/java/burp/MyTab.java`

- [ ] **Step 1: Reduce the scrollable tab's outer padding**

Replace:

```java
        JPanel padded = new JPanel(new BorderLayout());
        padded.setOpaque(false);
        padded.setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));
        padded.add(content, BorderLayout.NORTH);
```

with:

```java
        JPanel padded = new JPanel(new BorderLayout());
        padded.setOpaque(false);
        padded.setBorder(BorderFactory.createEmptyBorder(10, 14, 10, 14));
        padded.add(content, BorderLayout.NORTH);
```

- [ ] **Step 2: Reduce the vertical gap between cards**

There are 5 occurrences of `Box.createVerticalStrut(12)` in this file (3 in `buildCaptureTab()`, 2 in `buildInjectTab()`), all adding the same fixed gap between cards. Replace all of them:

Replace (`replace_all`):

```java
Box.createVerticalStrut(12)
```

with:

```java
Box.createVerticalStrut(8)
```

- [ ] **Step 3: Reduce `addSetting()` row insets**

Replace:

```java
        c.insets = new Insets(3, 0, 3, 8);
        c.gridy = row;
        c.anchor = GridBagConstraints.WEST;

        JCheckBox box = Ui.checkBox(label, checked);
        UiTextField field = field(value, setter, isRegex, label);
        JLabel hintLabel = Ui.muted(hint);
```

with:

```java
        c.insets = new Insets(2, 0, 2, 8);
        c.gridy = row;
        c.anchor = GridBagConstraints.WEST;

        JCheckBox box = Ui.checkBox(label, checked);
        UiTextField field = field(value, setter, isRegex, label);
        JLabel hintLabel = Ui.muted(hint);
```

- [ ] **Step 4: Build to verify it compiles**

Run: `mvn clean package`
Expected: `BUILD SUCCESS`

- [ ] **Step 5: Commit**

```bash
git add src/main/java/burp/MyTab.java
git commit -m "style: tighten tab padding, card gaps and setting-row insets

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 4: Turn the Inject tab's per-row preview into a tooltip

**Files:**
- Modify: `src/main/java/burp/MyTab.java`

Each token position (request header / 2nd header / body row) currently renders a second, always-visible grid row underneath it just to show a live "sends ..." preview — up to 5 extra rows across TOKEN-1 and TOKEN-2. This task removes that row and shows the same live-updating text as a tooltip on the row's fields instead.

- [ ] **Step 1: Replace the `addReplace()` method**

Replace the entire method:

```java
    /**
     * One replacement position: a control row plus a live preview row beneath
     * it showing what the request will actually end up carrying.
     */
    private void addReplace(JPanel grid, int row, String label, boolean checked, Consumer<Boolean> onToggle,
                            String matchValue, Consumer<String> matchSetter, boolean matchIsRegex,
                            String withValue, Consumer<String> withSetter,
                            String formatValue, Consumer<String> formatSetter,
                            Boolean addChecked, Consumer<Boolean> onAddToggle, String tokenName) {
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(3, 0, 1, 8);
        c.gridy = row;
        c.anchor = GridBagConstraints.WEST;

        JCheckBox box = Ui.checkBox(label, checked);
        UiTextField matchField = field(matchValue, matchSetter, matchIsRegex, label);
        matchField.hint(matchIsRegex ? "RegEx matched against the request body"
                : "the header prefix to look for, ex: Authorization: Bearer");
        UiTextField withField = withValue == null ? null : field(withValue, withSetter, false, label);
        UiTextField formatField = field(formatValue, formatSetter, false, label);
        formatField.setColumns(5);
        formatField.hint("First %s is the name, second %s is the token");
        JCheckBox addBox = addChecked == null ? null : Ui.checkBox("Add if missing", addChecked);
        if (addBox != null) {
            addBox.setToolTipText("Add this header when the request does not already carry it");
        }

        JLabel preview = Ui.muted("");
        Runnable updatePreview = () -> {
            String name = withField != null ? withField.getText() : matchField.getText();
            String token = sampleToken(tokenName);
            String composed;
            try {
                composed = String.format(formatField.getText(), name, token);
            } catch (Exception e) {
                composed = name + token;
            }
            preview.setText("sends   " + composed);
            preview.setToolTipText(composed);
        };
        updatePreview.run();
        matchField.onChange(t -> updatePreview.run());
        formatField.onChange(t -> updatePreview.run());
        if (withField != null) {
            withField.onChange(t -> updatePreview.run());
        }

        box.addActionListener((ActionEvent e) -> {
            boolean on = box.isSelected();
            onToggle.accept(on);
            matchField.setEnabled(on);
            if (withField != null) {
                withField.setEnabled(on);
            }
            formatField.setEnabled(on);
            if (addBox != null) {
                addBox.setEnabled(on);
            }
            preview.setEnabled(on);
            say(label + " " + (on ? "will be rewritten with " + tokenName + "." : "will be left alone."),
                    Theme.accent());
        });
        if (addBox != null) {
            addBox.addActionListener((ActionEvent e) -> {
                onAddToggle.accept(addBox.isSelected());
                say("'Add if missing' for " + label + " " + (addBox.isSelected() ? "on." : "off."), Theme.accent());
            });
            addBox.setEnabled(checked);
        }
        matchField.setEnabled(checked);
        if (withField != null) {
            withField.setEnabled(checked);
        }
        formatField.setEnabled(checked);
        preview.setEnabled(checked);

        c.gridx = 0;
        c.weightx = 0;
        c.fill = GridBagConstraints.NONE;
        grid.add(box, c);

        c.gridx = 1;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        grid.add(matchField, c);

        c.gridx = 2;
        if (withField != null) {
            grid.add(withField, c);
        } else {
            grid.add(Ui.muted("  -"), c);
        }

        c.gridx = 3;
        c.weightx = 0;
        grid.add(formatField, c);

        c.gridx = 4;
        c.fill = GridBagConstraints.NONE;
        grid.add(addBox == null ? Ui.muted("") : addBox, c);

        // preview line, indented under the match column
        GridBagConstraints p = new GridBagConstraints();
        p.gridy = row + 1;
        p.gridx = 1;
        p.gridwidth = 4;
        p.weightx = 1;
        p.anchor = GridBagConstraints.WEST;
        p.fill = GridBagConstraints.HORIZONTAL;
        p.insets = new Insets(0, 2, 7, 8);
        grid.add(preview, p);
    }
```

with:

```java
    /**
     * One replacement position: a single control row. The live "sends ..."
     * preview is shown as a tooltip on the row's fields instead of an
     * always-visible extra row, so each position takes one line, not two.
     */
    private void addReplace(JPanel grid, int row, String label, boolean checked, Consumer<Boolean> onToggle,
                            String matchValue, Consumer<String> matchSetter, boolean matchIsRegex,
                            String withValue, Consumer<String> withSetter,
                            String formatValue, Consumer<String> formatSetter,
                            Boolean addChecked, Consumer<Boolean> onAddToggle, String tokenName) {
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(2, 0, 1, 8);
        c.gridy = row;
        c.anchor = GridBagConstraints.WEST;

        String matchHint = matchIsRegex ? "RegEx matched against the request body"
                : "the header prefix to look for, ex: Authorization: Bearer";
        String formatHint = "First %s is the name, second %s is the token";

        JCheckBox box = Ui.checkBox(label, checked);
        UiTextField matchField = field(matchValue, matchSetter, matchIsRegex, label);
        UiTextField withField = withValue == null ? null : field(withValue, withSetter, false, label);
        UiTextField formatField = field(formatValue, formatSetter, false, label);
        formatField.setColumns(5);
        JCheckBox addBox = addChecked == null ? null : Ui.checkBox("Add if missing", addChecked);
        if (addBox != null) {
            addBox.setToolTipText("Add this header when the request does not already carry it");
        }

        Runnable updatePreview = () -> {
            String name = withField != null ? withField.getText() : matchField.getText();
            String token = sampleToken(tokenName);
            String composed;
            try {
                composed = String.format(formatField.getText(), name, token);
            } catch (Exception e) {
                composed = name + token;
            }
            String sends = "sends   " + composed;
            matchField.hint(matchHint + "   —   " + sends);
            formatField.hint(formatHint + "   —   " + sends);
            if (withField != null) {
                withField.hint(sends);
            }
        };
        updatePreview.run();
        matchField.onChange(t -> updatePreview.run());
        formatField.onChange(t -> updatePreview.run());
        if (withField != null) {
            withField.onChange(t -> updatePreview.run());
        }

        box.addActionListener((ActionEvent e) -> {
            boolean on = box.isSelected();
            onToggle.accept(on);
            matchField.setEnabled(on);
            if (withField != null) {
                withField.setEnabled(on);
            }
            formatField.setEnabled(on);
            if (addBox != null) {
                addBox.setEnabled(on);
            }
            say(label + " " + (on ? "will be rewritten with " + tokenName + "." : "will be left alone."),
                    Theme.accent());
        });
        if (addBox != null) {
            addBox.addActionListener((ActionEvent e) -> {
                onAddToggle.accept(addBox.isSelected());
                say("'Add if missing' for " + label + " " + (addBox.isSelected() ? "on." : "off."), Theme.accent());
            });
            addBox.setEnabled(checked);
        }
        matchField.setEnabled(checked);
        if (withField != null) {
            withField.setEnabled(checked);
        }
        formatField.setEnabled(checked);

        c.gridx = 0;
        c.weightx = 0;
        c.fill = GridBagConstraints.NONE;
        grid.add(box, c);

        c.gridx = 1;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        grid.add(matchField, c);

        c.gridx = 2;
        if (withField != null) {
            grid.add(withField, c);
        } else {
            grid.add(Ui.muted("  -"), c);
        }

        c.gridx = 3;
        c.weightx = 0;
        grid.add(formatField, c);

        c.gridx = 4;
        c.fill = GridBagConstraints.NONE;
        grid.add(addBox == null ? Ui.muted("") : addBox, c);
    }
```

- [ ] **Step 2: Update the TOKEN-1 grid's row numbers**

Since each position now takes 1 row instead of 2, the call sites in `buildInjectTab()` must use consecutive row numbers. Replace:

```java
        Card token1 = new Card("2", "TOKEN-1 goes here", "JWT / access token. Tick every position your app uses.");
        JPanel grid1 = grid();
        addReplaceHeader(grid1);
        addReplace(grid1, 1, "Request header", Config.CHECK_TOKEN1_HEADER_1, v -> Config.CHECK_TOKEN1_HEADER_1 = v,
                Config.VAL_JWT_HEADER_NAME_1, v -> Config.VAL_JWT_HEADER_NAME_1 = v, false, null, null,
                Config.REPLACE_STRUCTURE_TOKEN1_HEAD_1, v -> Config.REPLACE_STRUCTURE_TOKEN1_HEAD_1 = v,
                Config.CHECK_TOKEN1_HEADER_ADD_IF_NOT_EXIST_1,
                v -> Config.CHECK_TOKEN1_HEADER_ADD_IF_NOT_EXIST_1 = v, "TOKEN-1");
        addReplace(grid1, 3, "A second header", Config.CHECK_TOKEN1_HEADER_2, v -> Config.CHECK_TOKEN1_HEADER_2 = v,
                Config.VAL_JWT_HEADER_NAME_2, v -> Config.VAL_JWT_HEADER_NAME_2 = v, false, null, null,
                Config.REPLACE_STRUCTURE_TOKEN1_HEAD_2, v -> Config.REPLACE_STRUCTURE_TOKEN1_HEAD_2 = v,
                Config.CHECK_TOKEN1_HEADER_ADD_IF_NOT_EXIST_2,
                v -> Config.CHECK_TOKEN1_HEADER_ADD_IF_NOT_EXIST_2 = v, "TOKEN-1");
        addReplace(grid1, 5, "A body parameter", Config.CHECK_TOKEN1_BODY, v -> Config.CHECK_TOKEN1_BODY = v,
                Config.VAL_JWT_BODY_NAME_1, v -> Config.VAL_JWT_BODY_NAME_1 = v, true,
                Config.VAL_JWT_BODY_NAME_2, v -> Config.VAL_JWT_BODY_NAME_2 = v,
                Config.REPLACE_STRUCTURE_TOKEN1_BODY, v -> Config.REPLACE_STRUCTURE_TOKEN1_BODY = v, null, null,
                "TOKEN-1");
        token1.add(grid1);
```

with:

```java
        Card token1 = new Card("2", "TOKEN-1 goes here", "JWT / access token. Tick every position your app uses.");
        JPanel grid1 = grid();
        addReplaceHeader(grid1);
        addReplace(grid1, 1, "Request header", Config.CHECK_TOKEN1_HEADER_1, v -> Config.CHECK_TOKEN1_HEADER_1 = v,
                Config.VAL_JWT_HEADER_NAME_1, v -> Config.VAL_JWT_HEADER_NAME_1 = v, false, null, null,
                Config.REPLACE_STRUCTURE_TOKEN1_HEAD_1, v -> Config.REPLACE_STRUCTURE_TOKEN1_HEAD_1 = v,
                Config.CHECK_TOKEN1_HEADER_ADD_IF_NOT_EXIST_1,
                v -> Config.CHECK_TOKEN1_HEADER_ADD_IF_NOT_EXIST_1 = v, "TOKEN-1");
        addReplace(grid1, 2, "A second header", Config.CHECK_TOKEN1_HEADER_2, v -> Config.CHECK_TOKEN1_HEADER_2 = v,
                Config.VAL_JWT_HEADER_NAME_2, v -> Config.VAL_JWT_HEADER_NAME_2 = v, false, null, null,
                Config.REPLACE_STRUCTURE_TOKEN1_HEAD_2, v -> Config.REPLACE_STRUCTURE_TOKEN1_HEAD_2 = v,
                Config.CHECK_TOKEN1_HEADER_ADD_IF_NOT_EXIST_2,
                v -> Config.CHECK_TOKEN1_HEADER_ADD_IF_NOT_EXIST_2 = v, "TOKEN-1");
        addReplace(grid1, 3, "A body parameter", Config.CHECK_TOKEN1_BODY, v -> Config.CHECK_TOKEN1_BODY = v,
                Config.VAL_JWT_BODY_NAME_1, v -> Config.VAL_JWT_BODY_NAME_1 = v, true,
                Config.VAL_JWT_BODY_NAME_2, v -> Config.VAL_JWT_BODY_NAME_2 = v,
                Config.REPLACE_STRUCTURE_TOKEN1_BODY, v -> Config.REPLACE_STRUCTURE_TOKEN1_BODY = v, null, null,
                "TOKEN-1");
        token1.add(grid1);
```

- [ ] **Step 3: Update the TOKEN-2 grid's row numbers**

Replace:

```java
        Card token2 = new Card("3", "TOKEN-2 goes here", "Refresh / CSRF token.");
        JPanel grid2 = grid();
        addReplaceHeader(grid2);
        addReplace(grid2, 1, "Request header", Config.CHECK_TOKEN2_HEADER, v -> Config.CHECK_TOKEN2_HEADER = v,
                Config.VAL_TOKEN2_HDR_PARAM_NAME_1, v -> Config.VAL_TOKEN2_HDR_PARAM_NAME_1 = v, false, null, null,
                Config.REPLACE_STRUCTURE_TOKEN2_HEAD, v -> Config.REPLACE_STRUCTURE_TOKEN2_HEAD = v,
                Config.CHECK_TOKEN2_HEADER_ADD_IF_NOT_EXIST, v -> Config.CHECK_TOKEN2_HEADER_ADD_IF_NOT_EXIST = v,
                "TOKEN-2");
        addReplace(grid2, 3, "A body parameter", Config.CHECK_TOKEN2_BODY, v -> Config.CHECK_TOKEN2_BODY = v,
                Config.VAL_TOKEN2_BODY_PARAM_NAME_1, v -> Config.VAL_TOKEN2_BODY_PARAM_NAME_1 = v, true,
                Config.VAL_TOKEN2_BODY_PARAM_NAME_2, v -> Config.VAL_TOKEN2_BODY_PARAM_NAME_2 = v,
                Config.REPLACE_STRUCTURE_TOKEN2_BODY, v -> Config.REPLACE_STRUCTURE_TOKEN2_BODY = v, null, null,
                "TOKEN-2");
        token2.add(grid2);
```

with:

```java
        Card token2 = new Card("3", "TOKEN-2 goes here", "Refresh / CSRF token.");
        JPanel grid2 = grid();
        addReplaceHeader(grid2);
        addReplace(grid2, 1, "Request header", Config.CHECK_TOKEN2_HEADER, v -> Config.CHECK_TOKEN2_HEADER = v,
                Config.VAL_TOKEN2_HDR_PARAM_NAME_1, v -> Config.VAL_TOKEN2_HDR_PARAM_NAME_1 = v, false, null, null,
                Config.REPLACE_STRUCTURE_TOKEN2_HEAD, v -> Config.REPLACE_STRUCTURE_TOKEN2_HEAD = v,
                Config.CHECK_TOKEN2_HEADER_ADD_IF_NOT_EXIST, v -> Config.CHECK_TOKEN2_HEADER_ADD_IF_NOT_EXIST = v,
                "TOKEN-2");
        addReplace(grid2, 2, "A body parameter", Config.CHECK_TOKEN2_BODY, v -> Config.CHECK_TOKEN2_BODY = v,
                Config.VAL_TOKEN2_BODY_PARAM_NAME_1, v -> Config.VAL_TOKEN2_BODY_PARAM_NAME_1 = v, true,
                Config.VAL_TOKEN2_BODY_PARAM_NAME_2, v -> Config.VAL_TOKEN2_BODY_PARAM_NAME_2 = v,
                Config.REPLACE_STRUCTURE_TOKEN2_BODY, v -> Config.REPLACE_STRUCTURE_TOKEN2_BODY = v, null, null,
                "TOKEN-2");
        token2.add(grid2);
```

- [ ] **Step 4: Build to verify it compiles**

Run: `mvn clean package`
Expected: `BUILD SUCCESS`

- [ ] **Step 5: Commit**

```bash
git add src/main/java/burp/MyTab.java
git commit -m "refactor: show Inject row preview as a tooltip instead of an extra row

Each TOKEN-1/TOKEN-2 position used to render a second, always-visible
grid row just for the live 'sends ...' preview - up to 5 extra rows on
the Inject tab. The same live text now shows as a tooltip on the row's
fields, cutting the tab's height roughly in half without losing the
preview.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 5: Update README — remove the old "Injection in action" section and its images

**Files:**
- Modify: `README.md`
- Delete: `images/common/3ASimple.png`
- Delete: `images/common/3BSimple.png`
- Delete: `images/demo/Multi-App/1.Get-Tokens.png`
- Delete: `images/demo/Multi-App/2.Use-Tokens.png`

- [ ] **Step 1: Remove the "Injection in action" section from the README**

Replace:

```markdown
## Injection in action

Token-1 taken from the store and written into the request header (left: original request, right: what SH+ actually sent):

<kbd>![](images/common/3ASimple.png)</kbd>

Token-2 written into the request:

<kbd>![](images/common/3BSimple.png)</kbd>

Tokens captured for several applications with different paths:

<kbd>![](images/demo/Multi-App/1.Get-Tokens.png)</kbd>

**Note:** the three screenshots above come from the original Jython extension, which needed session handling rules. The request rewriting they show is the same; the setup they show is no longer needed.

#### Videos (original extension)

[![Simple Demo on SH+ Burp Suite Extension](https://img.youtube.com/vi/2vjhKZMKqco/0.jpg)](https://www.youtube.com/watch?v=2vjhKZMKqco)

[![Multi-application Demo on SH+ Burp Suite Extension](https://img.youtube.com/vi/0zRph3K8LWw/0.jpg)](https://www.youtube.com/watch?v=0zRph3K8LWw)

## Build & Installation
```

with:

```markdown
## Build & Installation
```

- [ ] **Step 2: Update the Inject tab description to reflect the tooltip preview**

Replace:

```markdown
Under every row a live **sends** preview shows exactly what the request will carry, using a real captured token when there is one.
```

with:

```markdown
Hover a row's fields to see a live **sends** preview of exactly what the request will carry, using a real captured token when there is one.
```

- [ ] **Step 3: Add a changelog entry**

Replace:

```markdown
## Changelog

**v1.0** - first release of XsessionHandler
```

with:

```markdown
## Changelog

**v1.1** - UI density pass

- Tightened spacing across the Capture/Inject tabs so both fit with far less scrolling.
- The Inject tab's per-row "sends" preview is now a hover tooltip instead of a permanent extra row.
- Fixed the credit link's hover state, which could show raw HTML tags under some look-and-feels.
- Removed the old Jython-extension screenshots/videos from this README.

**v1.0** - first release of XsessionHandler
```

- [ ] **Step 4: Delete the orphaned image files**

```bash
git rm images/common/3ASimple.png images/common/3BSimple.png images/demo/Multi-App/1.Get-Tokens.png images/demo/Multi-App/2.Use-Tokens.png
```

- [ ] **Step 5: Verify no remaining references to the deleted images**

Run: `grep -rn "3ASimple\|3BSimple\|demo/Multi-App" README.md`
Expected: no output (no matches)

- [ ] **Step 6: Commit**

```bash
git add README.md
git commit -m "docs: remove outdated Jython-extension screenshots and videos

The 'Injection in action' section showed the original Session Handler
Plus (Jython) extension's setup, which this fork no longer needs.
Also documents the v1.1 UI density pass.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 6: Final build and manual verification

**Files:** none (verification only)

- [ ] **Step 1: Full clean build**

Run: `mvn clean package`
Expected: `BUILD SUCCESS`, jar written to `target/session-handler-plus.jar`

- [ ] **Step 2: Manual check in Burp**

Load `target/session-handler-plus.jar` in Burp Suite (Extensions → Installed → Add → Java) and confirm:
- The Capture tab shows all 4 cards with noticeably less scrolling than before.
- The Inject tab shows all 3 cards (tools, TOKEN-1, TOKEN-2) with noticeably less scrolling, and each token position is a single row.
- Hovering a token position's match/format/replace-with field shows the live "sends ..." tooltip with the correct composed value.
- Hovering "@zalakamal08" in the header underlines the text — no literal `<html>`/`<u>` tags shown.
- Both light and dark Burp themes still render correctly (colours/fonts unchanged, only spacing changed).

- [ ] **Step 3: Report result**

If everything checks out, this plan is complete. If any manual check fails, note which one and fix before considering the work done.
