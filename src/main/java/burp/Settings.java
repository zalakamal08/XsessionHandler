package burp;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import javax.swing.Timer;

/**
 * Makes the tab's settings survive a Burp restart.
 *
 * Everything the user can change lives in public static fields on
 * {@link Config}, so rather than maintaining a list that would drift every time
 * a setting is added, this walks those fields by reflection. A new setting is
 * persisted the moment it is declared, with nothing to remember here.
 *
 * Writes are coalesced: typing in a RegEx field fires on every keystroke, and
 * each save goes to disk through Burp.
 */
public final class Settings {

    private static final String PREFIX = "shp.";
    private static final int SAVE_DELAY_MS = 800;

    private static IBurpExtenderCallbacks callbacks;
    private static Timer saveTimer;

    private Settings() {
    }

    public static void init(IBurpExtenderCallbacks burpCallbacks) {
        callbacks = burpCallbacks;
    }

    /** Applies whatever was stored last session over the defaults. */
    public static void load() {
        if (callbacks == null) {
            return;
        }
        int restored = 0;
        for (Field field : settableFields()) {
            String stored = read(PREFIX + field.getName());
            if (stored == null) {
                continue;
            }
            try {
                field.set(null, parse(field.getType(), stored));
                restored++;
            } catch (Exception e) {
                // A stored value we can no longer make sense of: keep the
                // default rather than refusing to start.
                Log.print("[" + Log.now() + "] SETTINGS: ignored '" + field.getName() + "' - " + e);
            }
        }
        for (Config.Tool tool : Config.TOOLS) {
            String stored = read(PREFIX + "tool." + tool.label);
            if (stored != null) {
                tool.enabled = Boolean.parseBoolean(stored);
                restored++;
            }
        }
        if (restored > 0) {
            Log.print("[" + Log.now() + "] SETTINGS: restored " + restored + " saved value(s).");
        }
    }

    /** Queues a save, collapsing a burst of edits into one write. */
    public static void scheduleSave() {
        if (callbacks == null) {
            return;
        }
        if (saveTimer == null) {
            saveTimer = new Timer(SAVE_DELAY_MS, e -> saveNow());
            saveTimer.setRepeats(false);
        }
        saveTimer.restart();
    }

    /** Writes every setting out immediately. */
    public static void saveNow() {
        if (callbacks == null) {
            return;
        }
        try {
            for (Field field : settableFields()) {
                Object value = field.get(null);
                write(PREFIX + field.getName(), value == null ? null : String.valueOf(value));
            }
            for (Config.Tool tool : Config.TOOLS) {
                write(PREFIX + "tool." + tool.label, String.valueOf(tool.enabled));
            }
        } catch (Exception e) {
            Log.print("[" + Log.now() + "] SETTINGS: save failed - " + e);
        }
    }

    /** Clears the stored settings; the defaults apply again on next load. */
    public static void reset() {
        if (callbacks == null) {
            return;
        }
        for (Field field : settableFields()) {
            write(PREFIX + field.getName(), null);
        }
        for (Config.Tool tool : Config.TOOLS) {
            write(PREFIX + "tool." + tool.label, null);
        }
    }

    // ----------------------------------------------------------------- utils

    /** The public, static, non-final Config fields - i.e. the user's settings. */
    private static java.util.List<Field> settableFields() {
        java.util.List<Field> fields = new java.util.ArrayList<Field>();
        for (Field field : Config.class.getDeclaredFields()) {
            int modifiers = field.getModifiers();
            if (!Modifier.isStatic(modifiers) || !Modifier.isPublic(modifiers) || Modifier.isFinal(modifiers)) {
                continue;
            }
            if (supported(field.getType())) {
                fields.add(field);
            }
        }
        return fields;
    }

    private static boolean supported(Class<?> type) {
        return type == boolean.class || type == String.class || type.isEnum();
    }

    private static Object parse(Class<?> type, String stored) {
        if (type == boolean.class) {
            return Boolean.valueOf(stored);
        }
        if (type == String.class) {
            return stored;
        }
        if (type == Config.SearchIn.class) {
            // Stored via toString(), which is the human-readable label.
            for (Config.SearchIn value : Config.SearchIn.values()) {
                if (value.label.equals(stored) || value.name().equals(stored)) {
                    return value;
                }
            }
            throw new IllegalArgumentException("unknown value '" + stored + "'");
        }
        throw new IllegalArgumentException("unsupported type " + type);
    }

    private static String read(String key) {
        try {
            String value = callbacks.loadExtensionSetting(key);
            return value == null || value.isEmpty() ? null : value;
        } catch (Exception e) {
            return null;
        }
    }

    private static void write(String key, String value) {
        try {
            callbacks.saveExtensionSetting(key, value);
        } catch (Exception e) {
            // Nothing useful to do; the session simply will not be remembered.
        }
    }
}
