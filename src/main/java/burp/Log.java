package burp;

import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Equivalent of Jython's print(), which lands in the extension output tab,
 * plus the datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S") helper.
 */
public final class Log {

    private static PrintWriter out;

    private Log() {
    }

    public static synchronized void init(IBurpExtenderCallbacks callbacks) {
        out = new PrintWriter(callbacks.getStdout(), true);
    }

    public static synchronized void print(String message) {
        if (out != null) {
            out.println(message);
            out.flush();
        } else {
            System.out.println(message);
        }
    }

    public static String now() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
    }
}
