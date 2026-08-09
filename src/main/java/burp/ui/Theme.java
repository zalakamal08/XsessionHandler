package burp.ui;

import java.awt.Color;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.font.TextAttribute;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.swing.UIManager;

/**
 * The single source of truth for colour, type and metrics.
 *
 * Neutrals are derived from whatever look and feel Burp is running so the tab
 * always sits correctly inside the host window, light or dark. Everything on
 * top of the neutrals - the accent, the type scale, the spacing rhythm, the
 * corner radii - is our own, so the tab reads as one designed surface rather
 * than as default Swing.
 *
 * Elevation runs canvas -> surface -> raised, with well() reserved for the
 * inside of an input. Text runs text -> dim -> faint. Nothing outside this
 * class should mix its own colours.
 */
public final class Theme {

    private Theme() {
    }

    // ------------------------------------------------------------- elevation

    /** The base plane: the page behind everything. */
    public static Color canvas() {
        return orDefault(UIManager.getColor("Panel.background"), new Color(0xF3F4F6));
    }

    /** One step up from the canvas: rail, header strip, status bar. */
    public static Color surface() {
        Color base = canvas();
        return isDark() ? lighten(base, 0.05f) : lighten(base, 0.60f);
    }

    /** Two steps up: hovered rows, table stripes, chips. */
    public static Color raised() {
        Color base = canvas();
        return isDark() ? lighten(base, 0.10f) : darken(base, 0.025f);
    }

    /** The inside of a text input - reads as recessed in both themes. */
    public static Color well() {
        Color base = canvas();
        return isDark() ? darken(base, 0.22f) : lighten(base, 0.85f);
    }

    // ------------------------------------------------------------------ text

    public static Color text() {
        return orDefault(UIManager.getColor("Label.foreground"), new Color(0x111827));
    }

    /** Secondary copy: subtitles, helper notes, values in a quiet column. */
    public static Color dim() {
        return mix(text(), canvas(), 0.38f);
    }

    /** Tertiary copy: column captions, units, disabled-ish labels. */
    public static Color faint() {
        return mix(text(), canvas(), 0.58f);
    }

    // ----------------------------------------------------------------- lines

    /** Hairline separators and resting input outlines. */
    public static Color line() {
        return mix(text(), canvas(), 0.80f);
    }

    /** A more assertive divider, for structural edges like the rail. */
    public static Color lineStrong() {
        return mix(text(), canvas(), 0.68f);
    }

    // ---------------------------------------------------------------- accent

    /**
     * Terminal green - the "running" colour of a developer tool. Deliberately
     * darker in light mode so body-size text on the canvas still clears the
     * 4.5:1 contrast bar.
     */
    public static Color accent() {
        return isDark() ? new Color(0x22C55E) : new Color(0x15803D);
    }

    /** A low-alpha wash of the accent, for chips, active nav and step marks. */
    public static Color accentSoft() {
        return alpha(accent(), isDark() ? 46 : 30);
    }

    /** Text/icon colour on top of a solid accent fill. */
    public static Color onAccent() {
        return isDark() ? new Color(0x052E16) : Color.WHITE;
    }

    // ----------------------------------------------------------------- state

    public static Color ok() {
        return accent();
    }

    public static Color warn() {
        return isDark() ? new Color(0xF59E0B) : new Color(0xB45309);
    }

    public static Color danger() {
        return isDark() ? new Color(0xF87171) : new Color(0xDC2626);
    }

    /** Neutral informational state - reads as "noted", not as "good" or "bad". */
    public static Color info() {
        return isDark() ? new Color(0x94A3B8) : new Color(0x475569);
    }

    // ------------------------------------------------------------------ type

    /**
     * Inter when the machine has it (the type pairing this UI was designed
     * around), otherwise whatever Burp is already using, so we degrade into
     * the host look rather than into something arbitrary.
     */
    private static final String FAMILY = firstAvailable("Inter", "Inter Display", "SF Pro Text",
            "Segoe UI Variable Text", "Segoe UI", "Roboto");

    private static float baseSize() {
        Font host = orDefault(UIManager.getFont("Label.font"), new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        return host.getSize2D() + 1f;
    }

    private static Font at(float size, int style) {
        if (FAMILY != null) {
            return new Font(FAMILY, style, 12).deriveFont(style, size);
        }
        Font host = orDefault(UIManager.getFont("Label.font"), new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        return host.deriveFont(style, size);
    }

    /** Default body copy. */
    public static Font body() {
        return at(baseSize(), Font.PLAIN);
    }

    /** Body copy carrying emphasis - control names, active nav. */
    public static Font strong() {
        return at(baseSize(), Font.BOLD);
    }

    /** Helper notes and secondary rows. */
    public static Font small() {
        return at(Math.max(10f, baseSize() - 1f), Font.PLAIN);
    }

    /**
     * Uppercase column captions and eyebrow labels. Letter-spaced, because at
     * this size all-caps without tracking closes up and stops being readable.
     */
    public static Font caption() {
        return track(at(Math.max(9f, baseSize() - 2f), Font.BOLD), 0.11f);
    }

    /** A section title. */
    public static Font h2() {
        return at(baseSize() + 3f, Font.BOLD);
    }

    /** The screen title inside the content pane. */
    public static Font h1() {
        return at(baseSize() + 5f, Font.BOLD);
    }

    /** The product wordmark in the header. */
    public static Font display() {
        return track(at(baseSize() + 7f, Font.BOLD), -0.015f);
    }

    /** Large tabular numerals for the header counters. */
    public static Font metric() {
        return new Font(Font.MONOSPACED, Font.BOLD, Math.round(baseSize() + 6f));
    }

    /** Everything the user typed that a machine will read: regexes, tokens. */
    public static Font mono() {
        return new Font(Font.MONOSPACED, Font.PLAIN, Math.round(Math.max(10f, baseSize() - 1f)));
    }

    public static Font monoSmall() {
        return new Font(Font.MONOSPACED, Font.PLAIN, Math.round(Math.max(9f, baseSize() - 2f)));
    }

    // --------------------------------------------------------------- metrics

    /** Spacing rhythm - dense, as befits a settings console. */
    public static final int SPACE_XS = 4;
    public static final int SPACE_SM = 8;
    public static final int SPACE_MD = 12;
    public static final int SPACE_LG = 16;
    public static final int SPACE_XL = 24;
    public static final int SPACE_XXL = 32;

    /** Corner radii. */
    public static final int RADIUS_SM = 5;
    public static final int RADIUS_MD = 7;
    public static final int RADIUS_LG = 10;

    /** Width of the navigation rail. */
    public static final int RAIL_WIDTH = 176;

    // ----------------------------------------------------------------- utils

    /** True when the host look and feel is a dark one. */
    public static boolean isDark() {
        return luminance(canvas()) < 0.5f;
    }

    public static Color mix(Color a, Color b, float weightOfB) {
        float w = clamp(weightOfB);
        return new Color(Math.round(a.getRed() * (1 - w) + b.getRed() * w),
                Math.round(a.getGreen() * (1 - w) + b.getGreen() * w),
                Math.round(a.getBlue() * (1 - w) + b.getBlue() * w));
    }

    public static Color lighten(Color color, float amount) {
        return mix(color, Color.WHITE, amount);
    }

    public static Color darken(Color color, float amount) {
        return mix(color, Color.BLACK, amount);
    }

    public static Color alpha(Color color, int alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
    }

    /** The disabled rendering of any colour: pushed most of the way to canvas. */
    public static Color muted(Color color) {
        return mix(color, canvas(), 0.62f);
    }

    private static Font track(Font font, float amount) {
        Map<TextAttribute, Object> attributes = new HashMap<TextAttribute, Object>();
        attributes.put(TextAttribute.TRACKING, Float.valueOf(amount));
        return font.deriveFont(attributes);
    }

    private static String firstAvailable(String... families) {
        try {
            Set<String> installed = new HashSet<String>();
            for (String name : GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()) {
                installed.add(name);
            }
            for (String family : families) {
                if (installed.contains(family)) {
                    return family;
                }
            }
        } catch (Exception ignored) {
            // Headless or a locked-down environment; fall back to the host font.
        }
        return null;
    }

    private static float luminance(Color color) {
        return (0.299f * color.getRed() + 0.587f * color.getGreen() + 0.114f * color.getBlue()) / 255f;
    }

    private static float clamp(float value) {
        return value < 0 ? 0 : (value > 1 ? 1 : value);
    }

    private static <T> T orDefault(T value, T fallback) {
        return value == null ? fallback : value;
    }
}
