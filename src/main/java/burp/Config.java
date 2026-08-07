package burp;

/** Global settings, all driven from the SH+ tab. */
public final class Config {

    private Config() {
    }

    public static final String VAL_ABOUT_SHP_MINI = "Author: Vinaya Kumar      [GitHub: https://github.com/V9Y1nf0S3C/Session-Handler-Plus]      Purpose: Session handling for JWT/AccessToken/RefreshToken";
    public static final String VAL_REF_LINK = "https://github.com/V9Y1nf0S3C/Session-Handler-Plus";

    public static final String EXT_VERSION = "v1.0";
    public static final String AUTHOR_HANDLE = "zalakamal08";
    public static final String AUTHOR_LINK = "https://github.com/zalakamal08";
    public static final String PROJECT_LINK = "https://github.com/zalakamal08/XsessionHandler";
    public static final String VAL_JWT_COOKIE_NAME = "TOKEN-1";
    public static final String VAL_REFRESH_TOKEN_COOKIE_NAME = "TOKEN-2";

    // ---------------------------------------------------------------- capture
    public static boolean CAPTURE_ENABLED = true;
    public static boolean CHECK_PRINT_REJECT_LOGS_1A_1B = false;
    public static boolean CHECK_REQ_URL_REGEX = true;
    public static String VALUE_REQ_URL_REGEX = "/token$|/token\\?";
    public static boolean CHECK_RESP_CONTENT_TYPE = true;
    public static String VALUE_RESP_CONTENT_TYPE_HEADER = "Content-Type: application/json";
    public static boolean CHECK_REQ_PATH = true;
    public static String VALUE_REQ_PATH_HEADER = "SHP-Path:(.*)";
    public static boolean LISTEN_4_ACCESS_TOKEN = true;
    public static String VALUE_READ_JWT_REGEX = "access_token\":\"(.+?)\"";
    public static boolean LISTEN_4_REFRESH_TOKEN = true;
    public static String VALUE_READ_REFRESH_TOKEN_REGEX = "refresh_token\":\"(.+?)\"";

    // ---------------------------------------------------------------- replace
    /** Master switch for rewriting outgoing requests before they are sent. */
    public static boolean AUTO_REPLACE_ENABLED = true;

    public static String REPLACE_STRUCTURE_TOKEN1_HEAD_1 = "%s %s";
    public static String REPLACE_STRUCTURE_TOKEN1_HEAD_2 = "%s %s";
    public static String REPLACE_STRUCTURE_TOKEN1_BODY = "%s%s";

    public static boolean CHECK_TOKEN1_HEADER_1 = true;
    public static boolean CHECK_TOKEN1_HEADER_2 = false;
    public static boolean CHECK_TOKEN1_BODY = false;
    public static String VAL_JWT_HEADER_NAME_1 = "Authorization: Bearer";
    public static String VAL_JWT_HEADER_NAME_2 = "custom-auth-gw:";
    public static boolean CHECK_TOKEN1_HEADER_ADD_IF_NOT_EXIST_1 = false;
    public static String VAL_JWT_BODY_NAME_1 = "access_token=\\w+";
    public static String VAL_JWT_BODY_NAME_2 = "access_token=";
    public static boolean CHECK_TOKEN1_HEADER_ADD_IF_NOT_EXIST_2 = false;

    public static boolean CHECK_TOKEN2_HEADER = false;
    public static boolean CHECK_TOKEN2_BODY = true;
    public static String VAL_TOKEN2_HDR_PARAM_NAME_1 = "My-Custom-Header: Prefix";
    public static boolean CHECK_TOKEN2_HEADER_ADD_IF_NOT_EXIST = false;
    public static String VAL_TOKEN2_BODY_PARAM_NAME_1 = "&refresh_token=(.*?)$";
    public static String VAL_TOKEN2_BODY_PARAM_NAME_2 = "&refresh_token=";
    public static String REPLACE_STRUCTURE_TOKEN2_HEAD = "%s %s";
    public static String REPLACE_STRUCTURE_TOKEN2_BODY = "%s%s";

    /** Burp tools whose outgoing requests are rewritten, one entry per checkbox. */
    public static final class Tool {
        public final String label;
        public final int flag;
        public boolean enabled;

        Tool(String label, int flag, boolean enabled) {
            this.label = label;
            this.flag = flag;
            this.enabled = enabled;
        }
    }

    public static final Tool[] TOOLS = {
            new Tool("Proxy", IBurpExtenderCallbacks.TOOL_PROXY, true),
            new Tool("Repeater", IBurpExtenderCallbacks.TOOL_REPEATER, true),
            new Tool("Scanner", IBurpExtenderCallbacks.TOOL_SCANNER, true),
            new Tool("Intruder", IBurpExtenderCallbacks.TOOL_INTRUDER, true),
            new Tool("Target", IBurpExtenderCallbacks.TOOL_TARGET, false),
            new Tool("Spider", IBurpExtenderCallbacks.TOOL_SPIDER, false),
            new Tool("Sequencer", IBurpExtenderCallbacks.TOOL_SEQUENCER, false),
            new Tool("Extender", IBurpExtenderCallbacks.TOOL_EXTENDER, false),
    };

    public static boolean isToolEnabled(int toolFlag) {
        for (Tool tool : TOOLS) {
            if (tool.flag == toolFlag) {
                return tool.enabled;
            }
        }
        return false;
    }

    public static String toolName(int toolFlag) {
        for (Tool tool : TOOLS) {
            if (tool.flag == toolFlag) {
                return tool.label;
            }
        }
        return "Tool:" + toolFlag;
    }
}
