package burp;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rewrites an outgoing request with the tokens captured so far. This is what
 * used to require a Session Handling Action; it now runs straight from the HTTP
 * listener, before the request leaves Burp.
 */
public final class TokenReplacer {

    /** Outcome of a rewrite attempt. */
    public static final class Result {
        public byte[] request;
        public final List<String> mods = new ArrayList<String>();
        public String token1Path;
        public String token1Comment;
        public String token1Value;
        public String token2Path;
        public String token2Comment;
        public String token2Value;

        Result(byte[] request) {
            this.request = request;
        }

        public boolean changed() {
            return !mods.isEmpty();
        }
    }

    private TokenReplacer() {
    }

    public static Result apply(IExtensionHelpers helpers, byte[] request, String url) {
        IRequestInfo requestInfo = helpers.analyzeRequest(request);
        List<String> headers = new ArrayList<String>(requestInfo.getHeaders());
        byte[] body = Arrays.copyOfRange(request, requestInfo.getBodyOffset(), request.length);

        Result result = new Result(request);

        boolean wantToken1 = Config.CHECK_TOKEN1_HEADER_1 || Config.CHECK_TOKEN1_HEADER_2 || Config.CHECK_TOKEN1_BODY;
        if (wantToken1) {
            TokenStore.Match match = TokenStore.lookup(Config.VAL_JWT_COOKIE_NAME, url);
            if (match.value != null) {
                result.token1Path = match.path;
                result.token1Comment = match.comment;
                result.token1Value = match.value;
                body = applyToken1(helpers, headers, body, match.value, result.mods);
            }
        }

        boolean wantToken2 = Config.CHECK_TOKEN2_HEADER || Config.CHECK_TOKEN2_BODY;
        if (wantToken2) {
            TokenStore.Match match = TokenStore.lookup(Config.VAL_REFRESH_TOKEN_COOKIE_NAME, url);
            if (match.value != null) {
                result.token2Path = match.path;
                result.token2Comment = match.comment;
                result.token2Value = match.value;
                body = applyToken2(helpers, headers, body, match.value, result.mods);
            }
        }

        if (result.changed()) {
            result.request = helpers.buildHttpMessage(headers, body);
        }
        return result;
    }

    private static byte[] applyToken1(IExtensionHelpers helpers, List<String> headers, byte[] body, String jwt,
                                      List<String> mods) {
        boolean header1Found = false;
        boolean header2Found = false;
        for (String header : headers) {
            if (header.toLowerCase().startsWith(Config.VAL_JWT_HEADER_NAME_1.toLowerCase())) {
                header1Found = true;
            }
            if (header.toLowerCase().startsWith(Config.VAL_JWT_HEADER_NAME_2.toLowerCase())) {
                header2Found = true;
            }
        }

        if (header1Found && Config.CHECK_TOKEN1_HEADER_1) {
            for (int i = 0; i < headers.size(); i++) {
                if (headers.get(i).toLowerCase().startsWith(Config.VAL_JWT_HEADER_NAME_1.toLowerCase())) {
                    headers.set(i, format(Config.REPLACE_STRUCTURE_TOKEN1_HEAD_1, Config.VAL_JWT_HEADER_NAME_1, jwt));
                    break;
                }
            }
            mods.add(Config.VAL_JWT_HEADER_NAME_1);
        } else if (!header1Found && Config.CHECK_TOKEN1_HEADER_1 && Config.CHECK_TOKEN1_HEADER_ADD_IF_NOT_EXIST_1) {
            headers.add(format(Config.REPLACE_STRUCTURE_TOKEN1_HEAD_1, Config.VAL_JWT_HEADER_NAME_1, jwt));
            mods.add("NEW_" + Config.VAL_JWT_HEADER_NAME_1);
        }

        if (header2Found && Config.CHECK_TOKEN1_HEADER_2) {
            for (int i = 0; i < headers.size(); i++) {
                if (headers.get(i).toLowerCase().startsWith(Config.VAL_JWT_HEADER_NAME_2.toLowerCase())) {
                    headers.set(i, format(Config.REPLACE_STRUCTURE_TOKEN1_HEAD_2, Config.VAL_JWT_HEADER_NAME_2, jwt));
                    break;
                }
            }
            mods.add(Config.VAL_JWT_HEADER_NAME_2);
        } else if (!header2Found && Config.CHECK_TOKEN1_HEADER_2 && Config.CHECK_TOKEN1_HEADER_ADD_IF_NOT_EXIST_2) {
            headers.add(format(Config.REPLACE_STRUCTURE_TOKEN1_HEAD_2, Config.VAL_JWT_HEADER_NAME_2, jwt));
            mods.add("NEW_" + Config.VAL_JWT_HEADER_NAME_2);
        }

        if (Config.CHECK_TOKEN1_BODY) {
            String bodyString = helpers.bytesToString(body);
            if (find(Config.VAL_JWT_BODY_NAME_1, bodyString)) {
                String replacement = format(Config.REPLACE_STRUCTURE_TOKEN1_BODY, Config.VAL_JWT_BODY_NAME_2, jwt);
                bodyString = Pattern.compile(Config.VAL_JWT_BODY_NAME_1).matcher(bodyString)
                        .replaceAll(Matcher.quoteReplacement(replacement));
                body = helpers.stringToBytes(bodyString);
                mods.add("BODY_" + Config.VAL_JWT_BODY_NAME_1);
            }
        }
        return body;
    }

    private static byte[] applyToken2(IExtensionHelpers helpers, List<String> headers, byte[] body, String tkn2,
                                      List<String> mods) {
        boolean headerFound = false;
        for (String header : headers) {
            if (header.toLowerCase().startsWith(Config.VAL_TOKEN2_HDR_PARAM_NAME_1.toLowerCase())) {
                headerFound = true;
                break;
            }
        }

        if (headerFound && Config.CHECK_TOKEN2_HEADER) {
            for (int i = 0; i < headers.size(); i++) {
                if (headers.get(i).toLowerCase().startsWith(Config.VAL_TOKEN2_HDR_PARAM_NAME_1.toLowerCase())) {
                    headers.set(i,
                            format(Config.REPLACE_STRUCTURE_TOKEN2_HEAD, Config.VAL_TOKEN2_HDR_PARAM_NAME_1, tkn2));
                    break;
                }
            }
            mods.add(Config.VAL_TOKEN2_HDR_PARAM_NAME_1);
        } else if (!headerFound && Config.CHECK_TOKEN2_HEADER && Config.CHECK_TOKEN2_HEADER_ADD_IF_NOT_EXIST) {
            headers.add(format(Config.REPLACE_STRUCTURE_TOKEN2_HEAD, Config.VAL_TOKEN2_HDR_PARAM_NAME_1, tkn2));
            mods.add("NEW_" + Config.VAL_TOKEN2_HDR_PARAM_NAME_1);
        }

        if (Config.CHECK_TOKEN2_BODY) {
            String bodyString = helpers.bytesToString(body);
            if (find(Config.VAL_TOKEN2_BODY_PARAM_NAME_1, bodyString)) {
                String replacement = format(Config.REPLACE_STRUCTURE_TOKEN2_BODY, Config.VAL_TOKEN2_BODY_PARAM_NAME_2,
                        tkn2);
                bodyString = Pattern.compile(Config.VAL_TOKEN2_BODY_PARAM_NAME_1).matcher(bodyString)
                        .replaceAll(Matcher.quoteReplacement(replacement));
                body = helpers.stringToBytes(bodyString);
                mods.add("BODY_" + Config.VAL_TOKEN2_BODY_PARAM_NAME_1);
            }
        }
        return body;
    }

    /** String.format that degrades to plain concatenation on a bad pattern. */
    private static String format(String pattern, String name, String token) {
        try {
            return String.format(pattern, name, token);
        } catch (Exception e) {
            return name + token;
        }
    }

    /** Case-insensitive search that never throws on a half-typed regex. */
    private static boolean find(String regex, String haystack) {
        try {
            return Pattern.compile(regex, Pattern.CASE_INSENSITIVE).matcher(haystack).find();
        } catch (Exception e) {
            return false;
        }
    }
}
