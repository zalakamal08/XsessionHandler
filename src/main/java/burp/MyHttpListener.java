package burp;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Does both halves of the job:
 * <ul>
 * <li>on a response - capture TOKEN-1 / TOKEN-2 into the {@link TokenStore},</li>
 * <li>on a request - rewrite it with the captured tokens before it is sent.</li>
 * </ul>
 */
public class MyHttpListener implements IHttpListener {

    /** Lets the tab show what just happened without owning the logic. */
    public interface StatusSink {
        void status(String message);
    }

    private final IBurpExtenderCallbacks callbacks;
    private final IExtensionHelpers helpers;
    private StatusSink statusSink;

    public MyHttpListener(IBurpExtenderCallbacks callbacks) {
        this.callbacks = callbacks;
        this.helpers = callbacks.getHelpers();
    }

    public void setStatusSink(StatusSink statusSink) {
        this.statusSink = statusSink;
    }

    private void status(String message) {
        StatusSink sink = statusSink;
        if (sink != null) {
            sink.status(message);
        }
    }

    @Override
    public void processHttpMessage(int toolFlag, boolean messageIsRequest, IHttpRequestResponse currentMessage) {
        try {
            if (messageIsRequest) {
                rewriteRequest(toolFlag, currentMessage);
            } else {
                captureTokens(toolFlag, currentMessage);
            }
        } catch (Exception e) {
            Log.print("[" + Log.now() + "] EXCEPTION OCCURED:\n\t" + e);
        }
    }

    // ------------------------------------------------------------------ send

    private void rewriteRequest(int toolFlag, IHttpRequestResponse currentMessage) {
        if (!Config.AUTO_REPLACE_ENABLED || !Config.isToolEnabled(toolFlag)) {
            return;
        }
        if (TokenStore.size() == 0) {
            return;
        }

        String url = helpers.analyzeRequest(currentMessage).getUrl().toString();
        TokenReplacer.Result result = TokenReplacer.apply(helpers, currentMessage.getRequest(), url);
        if (!result.changed()) {
            return;
        }

        currentMessage.setRequest(result.request);
        Stats.countInjection();

        StringBuilder detail = new StringBuilder();
        if (result.token1Value != null) {
            detail.append(" [").append(Config.VAL_JWT_COOKIE_NAME).append(" path:").append(result.token1Path)
                    .append(" match:").append(PyStr.slice(result.token1Comment, 0, 1)).append("]");
        }
        if (result.token2Value != null) {
            detail.append(" [").append(Config.VAL_REFRESH_TOKEN_COOKIE_NAME).append(" path:").append(result.token2Path)
                    .append(" match:").append(PyStr.slice(result.token2Comment, 0, 1)).append("]");
        }
        Log.print("[" + Log.now() + "] -> REQUEST MODIFIED [" + Config.toolName(toolFlag) + "]" + detail + " ["
                + PyStr.join(result.mods) + "] [URL:" + url + "]");
        status("Request rewritten before sending: " + PyStr.join(result.mods) + "   (" + Config.toolName(toolFlag)
                + ")");
    }

    // --------------------------------------------------------------- receive

    private void captureTokens(int toolFlag, IHttpRequestResponse currentMessage) {
        if (!Config.CAPTURE_ENABLED) {
            return;
        }
        if (!Config.LISTEN_4_ACCESS_TOKEN && !Config.LISTEN_4_REFRESH_TOKEN) {
            return;
        }

        String url = helpers.analyzeRequest(currentMessage).getUrl().toString();
        if (Config.CHECK_REQ_URL_REGEX && !find(Config.VALUE_REQ_URL_REGEX, url)) {
            if (Config.CHECK_PRINT_REJECT_LOGS_1A_1B) {
                Log.print("[" + Log.now() + "] - SKIPPED 1A: URL does not match RegEx '" + Config.VALUE_REQ_URL_REGEX
                        + "' - [" + Config.toolName(toolFlag) + "] [URL:" + url + "]");
            }
            return;
        }

        IResponseInfo response = helpers.analyzeResponse(currentMessage.getResponse());
        List<String> headers = response.getHeaders();
        int thirdSlash = url.indexOf('/', url.indexOf('/', url.indexOf('/') + 1) + 1);
        String reqURL = PyStr.sliceFrom(url, thirdSlash);

        if (Config.CHECK_RESP_CONTENT_TYPE) {
            boolean matched = false;
            for (String header : headers) {
                if (header.contains(Config.VALUE_RESP_CONTENT_TYPE_HEADER)) {
                    matched = true;
                    break;
                }
            }
            if (!matched) {
                if (Config.CHECK_PRINT_REJECT_LOGS_1A_1B) {
                    Log.print("[" + Log.now() + "] - SKIPPED 1B: response header '"
                            + Config.VALUE_RESP_CONTENT_TYPE_HEADER + "' not found - [" + Config.toolName(toolFlag)
                            + "] [URL:" + url + "]");
                }
                return;
            }
        }

        byte[] responseBytes = currentMessage.getResponse();
        String body = helpers
                .bytesToString(Arrays.copyOfRange(responseBytes, response.getBodyOffset(), responseBytes.length));

        String accessToken = Config.LISTEN_4_ACCESS_TOKEN ? group1(Config.VALUE_READ_JWT_REGEX, body) : null;
        String refreshToken = Config.LISTEN_4_REFRESH_TOKEN ? group1(Config.VALUE_READ_REFRESH_TOKEN_REGEX, body)
                : null;

        if (accessToken == null && refreshToken == null) {
            return;
        }

        boolean storedForCustomPath = false;
        if (Config.CHECK_REQ_PATH) {
            String[] requestHeaders = helpers.bytesToString(currentMessage.getRequest()).split("\r\n", -1);
            Pattern pathPattern = compile(Config.VALUE_REQ_PATH_HEADER);
            if (pathPattern != null) {
                for (String reqHeader : requestHeaders) {
                    Matcher matcher = pathPattern.matcher(reqHeader);
                    if (matcher.find() && matcher.groupCount() >= 1 && matcher.group(1) != null) {
                        storedForCustomPath = true;
                        String path = matcher.group(1).trim();
                        store(accessToken, refreshToken, path, reqURL, url, toolFlag);
                    }
                }
            }
        }
        if (!storedForCustomPath) {
            store(accessToken, refreshToken, "/", reqURL, url, toolFlag);
        }
    }

    private void store(String accessToken, String refreshToken, String path, String reqURL, String url, int toolFlag) {
        if (Config.LISTEN_4_ACCESS_TOKEN && accessToken != null) {
            record(Config.VAL_JWT_COOKIE_NAME, accessToken, path, reqURL, url, toolFlag);
        }
        if (Config.LISTEN_4_REFRESH_TOKEN && refreshToken != null) {
            record(Config.VAL_REFRESH_TOKEN_COOKIE_NAME, refreshToken, path, reqURL, url, toolFlag);
        }
    }

    private void record(String name, String value, String path, String reqURL, String url, int toolFlag) {
        String previous = TokenStore.put(name, path, value, url, Config.toolName(toolFlag));
        Stats.countCapture();
        boolean unchanged = value.equals(previous);
        String verb = previous == null ? "CAPTURED" : (unchanged ? "UNCHANGED" : "UPDATED");

        Log.print("[" + Log.now() + "] <- " + name + " " + verb + " [" + Config.toolName(toolFlag) + "] [path:" + path
                + "] [len:" + value.length() + "] [URL:" + reqURL + "]\n         new value: " + value
                + (previous != null && !unchanged ? "\n         old value: " + previous : ""));
        status(name + " " + verb + " for path '" + path + "'  ->  " + value);
    }

    // ----------------------------------------------------------------- utils

    private static Pattern compile(String regex) {
        try {
            return Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean find(String regex, String haystack) {
        try {
            return Pattern.compile(regex).matcher(haystack).find();
        } catch (Exception e) {
            return false;
        }
    }

    private static String group1(String regex, String haystack) {
        try {
            Matcher matcher = Pattern.compile(regex).matcher(haystack);
            if (matcher.find() && matcher.groupCount() >= 1) {
                return matcher.group(1);
            }
        } catch (Exception e) {
            // A half-typed regex must not break traffic.
        }
        return null;
    }
}
