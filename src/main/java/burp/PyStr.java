package burp;

import java.util.List;

/**
 * Small helpers that reproduce the exact semantics of the Python string
 * operations used by the original extension (negative indices, clamping,
 * rfind with a bounded range, list repr in log lines).
 */
public final class PyStr {

    private PyStr() {
    }

    /** Python s[start:end] with negative index and out-of-range clamping. */
    public static String slice(String s, int start, int end) {
        int n = s.length();
        if (start < 0) {
            start += n;
            if (start < 0) {
                start = 0;
            }
        } else if (start > n) {
            start = n;
        }
        if (end < 0) {
            end += n;
            if (end < 0) {
                end = 0;
            }
        } else if (end > n) {
            end = n;
        }
        if (end < start) {
            return "";
        }
        return s.substring(start, end);
    }

    /** Python s[start:] */
    public static String sliceFrom(String s, int start) {
        return slice(s, start, s.length());
    }

    /** Python s[-count:] */
    public static String tail(String s, int count) {
        return slice(s, -count, s.length());
    }

    /** Python s.rfind(sub, start, end) */
    public static int rfind(String s, String sub, int start, int end) {
        int n = s.length();
        if (start < 0) {
            start += n;
            if (start < 0) {
                start = 0;
            }
        } else if (start > n) {
            start = n;
        }
        if (end < 0) {
            end += n;
            if (end < 0) {
                end = 0;
            }
        } else if (end > n) {
            end = n;
        }
        if (end < start) {
            return -1;
        }
        int idx = s.substring(start, end).lastIndexOf(sub);
        return idx < 0 ? -1 : start + idx;
    }

    /** Python repr() of a list of strings, as used in the log messages. */
    public static String repr(List<String> items) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append('\'').append(items.get(i)).append('\'');
        }
        return sb.append(']').toString();
    }

    /** Python str(True) / str(False) */
    public static String bool(boolean value) {
        return value ? "True" : "False";
    }

    /** Python ', '.join(items) */
    public static String join(List<String> items) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(items.get(i));
        }
        return sb.toString();
    }
}
