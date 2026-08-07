package burp.ui;

import java.awt.Color;
import java.awt.Font;

import javax.swing.UIManager;

/**
 * Colours and fonts derived from whatever look and feel Burp is running, so
 * the tab sits correctly in both the light and the dark theme instead of
 * hard-coding black on white.
 */
public final class Theme {

    private Theme() {
    }

    public static Color background() {
        return orDefault(UIManager.getColor("Panel.background"), new Color(0xF2F2F2));
    }

    public static Color foreground() {
        return orDefault(UIManager.getColor("Label.foreground"), new Color(0x1F1F1F));
    }

    /** True when the host look and feel is a dark one. */
    public static boolean isDark() {
        return luminance(background()) < 0.5f;
    }

    /** Panel fill for a card, a touch lifted from the window background. */
    public static Color card() {
        Color bg = background();
        return isDark() ? lighten(bg, 0.06f) : lighten(bg, 0.55f);
    }

    /** Slightly stronger fill, for headers and table stripes. */
    public static Color surface() {
        Color bg = background();
        return isDark() ? lighten(bg, 0.12f) : darken(bg, 0.03f);
    }

    /** Hairline separators and component outlines. */
    public static Color line() {
        return mix(foreground(), background(), 0.78f);
    }

    /** De-emphasised text: hints, units, secondary labels. */
    public static Color muted() {
        return mix(foreground(), background(), 0.42f);
    }

    public static Color accent() {
        return isDark() ? new Color(0x58A6FF) : new Color(0x0969DA);
    }

    public static Color success() {
        return isDark() ? new Color(0x3FB950) : new Color(0x1A7F37);
    }

    public static Color danger() {
        return isDark() ? new Color(0xF85149) : new Color(0xCF222E);
    }

    public static Color warning() {
        return isDark() ? new Color(0xD29922) : new Color(0x9A6700);
    }

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

    public static Font mono() {
        return new Font(Font.MONOSPACED, Font.PLAIN, (int) Math.max(10f, base().getSize2D() - 1f));
    }

    // ----------------------------------------------------------------- maths

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
