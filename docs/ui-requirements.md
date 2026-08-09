# XsessionHandler — UI Requirements

This document describes what the tool does and exactly which fields, controls,
and content each screen needs. It intentionally contains no colors, fonts, or
layout/visual direction — it's a functional spec for a UI designer/agent to
design the look from scratch.

## What the tool is

XsessionHandler is a Burp Suite extension. It watches HTTP responses, pulls
two kinds of tokens (an access/JWT-style token and a refresh/CSRF-style
token) out of them using user-defined rules, holds those tokens in memory,
and automatically rewrites outgoing requests to carry the freshest token —
so a user logs in once through Burp and every following request in scope is
authenticated automatically, with no manual session-handling setup.

The extension has one tab inside Burp, containing:
- A header strip that is always visible, regardless of which inner tab is open.
- Three inner tabs: **Capture**, **Inject**, **Tokens**.
- A status bar that is always visible at the bottom.

All settings apply immediately as the user types or toggles them — there are
no "Save"/"Apply" buttons anywhere in the tool. Any text field that accepts a
regular expression must be able to indicate when the current text is not a
valid regex, and while invalid, that field's value is not applied.

---

## Header (always visible, above the three inner tabs)

Required content/controls:

- Extension name/title text.
- A version label (short text, e.g. "v1.1").
- A short one-line tagline describing what the tool does.
- Author credit: a short "Built by" label plus the author's name/handle,
  which is a clickable link to the author's profile.
- **Capture master switch**: an on/off toggle, a "Capture" label, a
  live text label showing "ON"/"OFF" state, and a live counter showing how
  many tokens have been captured so far.
- **Inject master switch**: an on/off toggle, an "Inject" label, a live
  text label showing "ON"/"OFF" state, and a live counter showing how many
  requests have been rewritten so far.
- Toggling the Capture switch off must disable every control on the Capture
  tab (visually indicate they are disabled); toggling it on re-enables them.
  Same behavior for Inject switch and the Inject tab.

---

## Tab 1: Capture — "where tokens come from"

### Section: Which responses to read

A response is only inspected if every enabled check in this section passes.

- Checkbox: "Request URL matches" — paired with a text field holding a
  regular expression that is tested against the full request URL. Needs a
  short helper note: "RegEx, tested against the whole URL."
- Checkbox: "Response header contains" — paired with a text field holding
  plain text (not a regex) that must appear somewhere in the response
  headers. Needs a short helper note: "plain text, not a RegEx."

### Section: What to pull out

Group 1 of each regex is what gets stored as the token value. A user can
leave either one unticked if their application doesn't use that kind of
token.

- Checkbox: "TOKEN-1" — paired with a text field holding a regular
  expression run against the response body. Needs a short helper note:
  "JWT / access token."
- Checkbox: "TOKEN-2" — paired with a text field holding a regular
  expression run against the response body. Needs a short helper note:
  "refresh / CSRF token."

### Section: Testing more than one app at once? (optional)

- A short explanatory paragraph: two applications that both return a token
  under the same name would otherwise overwrite each other's stored value;
  adding a specific request header to each login request lets the tool file
  tokens under a named scope instead of one shared slot.
- Checkbox: "Read the scope from this request header" — paired with a text
  field holding a regular expression run against request headers, where
  group 1 becomes the scope name. Needs a short helper note: "group 1
  becomes the scope."

### Section: Not seeing your token? (debug helper)

- A single checkbox: "Log every response that was skipped, and say why" —
  when on, every rejected response and the specific reason it was rejected
  is written to the extension's log output.

---

## Tab 2: Inject — "where tokens go"

### Section: Which Burp tools to rewrite

Requests are only rewritten for the Burp tools selected here.

- A row of 8 checkboxes, one per Burp tool: **Proxy, Repeater, Scanner,
  Intruder, Target, Spider, Sequencer, Extender** — each independently
  toggleable.
- Two quick-action controls: "Select all" and "Select none," each of which
  toggles all 8 checkboxes at once.

### Section: TOKEN-1 goes here

TOKEN-1 can be injected into up to three separate positions in a request.
Each position needs its own row of controls, and every row needs a live
preview of exactly what will be sent, generated from the row's current
inputs and a real captured token when one exists (otherwise a readable
placeholder).

**Position 1 — Request header**
- Enable checkbox.
- "Match" field: the header name/prefix to look for (e.g.
  `Authorization: Bearer`).
- "Format" field: a pattern describing how the header name and the token
  are joined (e.g. `%s %s` for a space between them, `%s%s` for none). Needs
  a short helper note: "First %s is the name, second %s is the token."
- "Add if missing" checkbox: whether to add this header to the request if
  it isn't already present.
- Live preview of the resulting header.

**Position 2 — A second header**
- Same fields as Position 1 (enable checkbox, Match field, Format field,
  "Add if missing" checkbox, live preview) — a separate, independent header
  position.

**Position 3 — A body parameter**
- Enable checkbox.
- "Match" field: a regular expression run against the request body to find
  where the token currently sits (e.g. `access_token=\w+`).
- "Replace with" field: the literal text to put back in front of the token
  once matched (e.g. `access_token=`).
- "Format" field: same joining-pattern behavior as the header rows.
- Live preview of the resulting body fragment.

### Section: TOKEN-2 goes here

TOKEN-2 can be injected into up to two separate positions, with the same
requirements as TOKEN-1's positions above:

**Position 1 — Request header**
- Enable checkbox, Match field, Format field, "Add if missing" checkbox,
  live preview.

**Position 2 — A body parameter**
- Enable checkbox, Match field, Replace-with field, Format field, live
  preview.

---

## Tab 3: Tokens — "what has been captured"

### Toolbar

- Button: "Copy value" — copies the selected row's full token value.
- Button: "Remove" — deletes the selected row's token entry.
- Button: "Clear all" — deletes every captured token.
- A short informational note: tokens are held in memory only and are
  cleared when the extension is unloaded; clicking a column heading sorts
  the table by that column.

### Table

A sortable table with one row per captured token, with these columns:

- **Token** — which token this is (TOKEN-1 or TOKEN-2).
- **Scope** — the path/application scope this token is filed under.
- **Len** — the character length of the token value.
- **Value** — the full captured token value.
- **Captured** — the time the value was last captured/updated.
- **Tool** — which Burp tool the response came through (Proxy, Repeater,
  etc).
- **Source URL** — the URL of the response the token was captured from.

### Empty state

When no tokens have been captured yet, the table area needs to show a
message instead: that no tokens have been captured, an instruction to log
in through the target application via Burp, and a pointer to check the
RegEx settings on the Capture tab if nothing shows up.

### Tab label

The tab's own label needs to show a live count of captured tokens once
there is at least one (e.g. the tab reads differently when it holds
entries vs. when it's empty).

---

## Status bar (always visible, below the three inner tabs)

- A short text message reflecting the last action taken anywhere in the
  tool (e.g. a setting was changed, a token was captured, a request was
  rewritten, an error occurred).
- A status indicator distinguishing at least these states: informational,
  success, warning, error.
- A link to the project's GitHub page.

---

## Cross-cutting requirements

- Every setting (checkbox, text field) takes effect immediately when
  changed — no explicit save step anywhere.
- Every regex-holding text field must be able to show an "invalid input"
  state when its current text does not compile as a regular expression,
  and the previous valid value continues to be used until the text is
  fixed.
- Text fields that benefit from extra context (what format is expected,
  what an example value looks like) need a way to surface that guidance
  without it being permanently on-screen taking up space (e.g. on
  hover/focus).
- Disabling a tab's master switch must visibly disable every control inside
  that tab; re-enabling it must restore them.
