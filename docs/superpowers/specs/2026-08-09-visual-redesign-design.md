# Visual Redesign (Typography, Color, Layout) — Design

Date: 2026-08-09

## Problem

The previous pass (v1.1) fixed density and a hover bug, but the visual
language itself still reads as flat and busy:

1. **Weak typography hierarchy** — base text, labels, and hints are all close
   to the same small size, so nothing draws the eye and the UI is harder to
   scan quickly.
2. **Underused accent color** — the UI is almost entirely gray/muted; the
   accent blue only shows up in a couple of pills and links, making the tool
   feel monochrome.
3. **Boxy layout** — every section is a bordered, rounded `Card`, so the
   Capture/Inject tabs read as boxes-inside-boxes rather than a clean,
   confident layout (the look referenced as more usable/eye-catching,
   Postman2Burp, avoids stacking bordered boxes for every section).

## Goals

- Establish a clear 3-tier text hierarchy: **Heading** (section titles) →
  **Label** (setting names/body text) → **Hint** (secondary/help text) —
  each visibly distinct in size/weight, all still readable at typical Burp
  window sizes.
- Use the accent color with intent: active tab, primary buttons, master
  ON/OFF switches, and section-heading accents, instead of confining it to
  a couple of pills/links.
- Replace the "every section is a bordered rounded card" pattern on the
  Capture/Inject tabs with flat sections: a bold heading, a short colored
  accent rule beneath it, and generous whitespace — no box border, no card
  background fill.
- Make primary actions (Copy value / Remove / Clear all on the Tokens tab)
  visually heavier — filled/tinted buttons instead of flat default Swing
  buttons — so they read as actionable.
- Preserve everything that already works: Burp look-and-feel awareness
  (light/dark themes both still derive from `Theme`, nothing hardcoded),
  the 3-tab structure (Capture/Inject/Tokens), the header strip with master
  switches, the Tokens table, and the density/tooltip-preview work from the
  previous pass.

## Non-goals

- No new settings, no restructuring of what each tab contains.
- No change to `TokenStore`/`TokenReplacer`/`MyHttpListener` logic.
- The Tokens tab's table itself is not being redesigned structurally (it
  already reads clean) — only its toolbar buttons get the heavier button
  treatment.
- Not attempting to literally clone Postman2Burp's UI — using it only as a
  reference point for "bigger headers, more deliberate color, less
  border clutter."

## Design

### 1. `Theme` — new/adjusted type and color tokens

Add a clearer type scale and a couple of new color helpers, still all
derived from Burp's look-and-feel so both themes keep working automatically:

- `Theme.base()`: bump from `size` to `size + 1` (one step larger baseline
  text).
- `Theme.heading()`: bump from `size + 1` to `size + 4`, bold — used for
  section titles so they're unmistakably bigger than body text.
- `Theme.small()`: keep as the hint/secondary tier, but raise the floor from
  `9f` to `10f` so it never becomes unreadably tiny on small Burp fonts.
- New `Theme.accentSoft()`: a low-alpha tint of `accent()` (like the
  existing pill/step-badge tint), reusable as a background for the new
  flat-section heading accent rule and for filled buttons' hover state.
- New `Theme.onAccent()`: a near-white color for text/icons drawn on top of
  a solid `accent()` fill (used by the new filled buttons).

### 2. `Card` → flat `Section` (new component, replaces `Card` on
   Capture/Inject)

Add a new `burp.ui.Section` component: a heading row (bold, `Theme.heading()`
size, plus a short 3px-tall colored accent rule directly beneath the title)
and a content area beneath it — no border, no background fill, no rounded
rect painting. Same public shape as `Card` (`step`, `title`, `subtitle`,
`add()`, `gap()`) so `MyTab` call sites need minimal changes — swap
`new Card(...)` for `new Section(...)`.

`Card` itself is not deleted: nothing else currently depends on removing it,
but since every current caller in `MyTab` moves to `Section`, `Card.java`
becomes unused. It's removed as part of this change (YAGNI — no reason to
keep an unused class).

### 3. `MyTab` — apply the new type scale, accent usage, and flat sections

- Replace every `new Card(...)` with `new Section(...)` on the Capture and
  Inject tabs.
- Master switches (Capture/Inject ON-OFF) and their labels pick up the
  larger `Theme.bold()`/heading treatment already implicitly via the
  `Theme.base()` bump; no structural change needed there beyond the token
  updates in `Theme`.
- Tokens tab toolbar buttons (`Copy value`, `Remove`, `Clear all`) switch
  from plain `JButton` to a new filled-style button: solid `Theme.accent()`
  background, `Theme.onAccent()` text, rounded corners — matching the
  weight of the master ON/OFF switch so primary actions are visually
  consistent.

### 4. Scope check on `UiTextField` / `SwitchButton`

Both already derive all colors from `Theme`, so they automatically pick up
the refreshed base font size and accent color with no code change needed
beyond what's already in `Theme`.

## Testing / verification

- `mvn clean package` builds cleanly.
- Manual check in Burp (light and dark theme) that:
  - Section headings are clearly larger/bolder than labels, which are
    clearly larger than hint text.
  - The accent color shows up with intent (active tab, switches, filled
    buttons, section accent rule) rather than being confined to 1-2 spots.
  - Capture/Inject tabs no longer show bordered card boxes — flat sections
    with heading + accent rule instead.
  - Tokens tab's Copy/Remove/Clear buttons read as filled, primary-style
    buttons.
  - Nothing regresses from the v1.1 density/tooltip-preview work (still no
    unnecessary scrolling, tooltips still work).
