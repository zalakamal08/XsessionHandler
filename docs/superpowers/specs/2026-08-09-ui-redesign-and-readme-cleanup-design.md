# UI Density Redesign + README Cleanup — Design

Date: 2026-08-09

## Problem

The Capture and Inject tabs require unnecessary scrolling to see all settings.
Two contributing causes:

1. Generous spacing/padding throughout the Swing UI (`Card`, grid rows, tab
   padding).
2. The Inject tab renders a full extra grid row under every token position
   (header / 2nd header / body row, per token) just to show a live "sends ..."
   preview — up to 5 extra rows across TOKEN-1 and TOKEN-2.

Separately, hovering the "@zalakamal08" credit link in the header shows raw
`<html><u>...</u></html>` tags instead of an underlined link, because
`Ui.link()` fakes the underline by swapping the label's text to an HTML
string on `mouseEntered`. Under Burp's current look-and-feel this HTML isn't
rendered, so the tags show literally.

The README also carries a leftover "Injection in action" section with 3
screenshots and 2 YouTube links from the original Jython extension
(Session Handler Plus), which no longer reflects how this fork works.

## Goals

- Reduce vertical space used by the Capture/Inject tabs so less scrolling is
  needed, without removing or hiding any existing setting.
- Fix the hover-link rendering bug so it can't break under any look-and-feel.
- Remove the outdated "Injection in action" section and its now-orphaned
  image files from the README/repo.

## Non-goals

- No settings are removed, hidden behind an "advanced" toggle, or merged into
  fewer sections — same functionality, same 3-tab structure (Capture /
  Inject / Tokens).
- No structural rewrite of the README beyond removing the old section and
  light wording/consistency polish.
- No changes to `TokenStore`/`TokenReplacer`/`MyHttpListener` logic — this is
  a UI/doc-only change.

## Design

### 1. Denser layout (Card, grids, tab padding)

Tighten spacing constants used across `Card`, `MyTab`'s `grid()` helper, and
the scrollable tab padding, so more content fits per screen:

- `Card`: reduce header/content `EmptyBorder` insets.
- `scrollable()`: reduce the outer padding around each tab's content.
- Vertical struts between cards (`Box.createVerticalStrut(12)` and similar):
  reduce.
- `GridBagConstraints.insets` on setting rows (`addSetting`, `addReplace`,
  `addReplaceHeader`): reduce top/bottom insets.

This is a mechanical tightening pass — no layout restructuring, no settings
moved or removed.

### 2. Inject tab: preview becomes a live tooltip, not a row

Currently `addReplace()` in `MyTab` adds two things per token position: the
control row (checkbox/match field/replace-with/format/add-checkbox) and a
second grid row directly beneath it holding a "sends ..." preview label,
updated live as the user types.

New behavior: drop the dedicated preview row entirely. Instead, the live
preview text is set as the **tooltip** of the row's match/format/replace-with
fields, recomputed on every change exactly as today, and shown on hover or
focus via the fields' existing tooltip mechanism (`setToolTipText`).

- Same computation (`String.format(formatField.getText(), name, token)`,
  same fallback to plain concatenation on a bad format string).
- Same sample-token fallback (`sampleToken(tokenName)`) when nothing has
  been captured yet.
- Applies to all 5 preview rows across TOKEN-1 (header, 2nd header, body) and
  TOKEN-2 (header, body).

Net effect: each token position shrinks from 2 grid rows to 1, cutting a
large chunk of the Inject tab's height.

### 3. Fix the hover-link HTML bug

Replace `Ui.link()`'s `mouseEntered`/`mouseExited` HTML-text-swap with a real
underline via `Font`/`TextAttribute.UNDERLINE`, applied to the label's font
on hover and removed on exit. No HTML is used, so rendering can't depend on
whatever look-and-feel Burp is running.

### 4. README cleanup

- Remove the entire "Injection in action" section: the 3 screenshots
  (`images/common/3ASimple.png`, `images/common/3BSimple.png`,
  `images/demo/Multi-App/1.Get-Tokens.png`) and the "Videos (original
  extension)" subsection with its 2 YouTube links.
- Delete the now-orphaned files: `images/common/3ASimple.png`,
  `images/common/3BSimple.png`, and the whole `images/demo/` folder
  (including `2.Use-Tokens.png`, which was already unreferenced).
- Light wording/consistency polish elsewhere in the README; no structural
  rewrite of remaining sections.
- Out of scope: retaking the 4 current UI screenshots
  (`ui-capture.png`, `ui-inject.png`, `ui-tokens.png`, `ui-dark.png`). They
  remain visually representative (same 3-tab structure) but won't reflect
  the tightened spacing/removed preview rows until the user retakes them.

## Testing / verification

- `mvn clean package` builds cleanly.
- Manual check in Burp (or a quick standalone Swing harness) that:
  - Capture and Inject tabs need less/no scrolling at a typical Burp window
    size.
  - Inject tab's live preview still updates correctly, now as a tooltip.
  - Hovering the "@zalakamal08" credit label underlines it without showing
    literal tags.
- README renders correctly on GitHub with the removed section and no broken
  image links.
