package burp;

/** Extension entry point. */
public class BurpExtender implements IBurpExtender {

    private MyHttpListener httpListener;
    private MyTab tab;

    @Override
    public void registerExtenderCallbacks(IBurpExtenderCallbacks callbacks) {
        Log.init(callbacks);
        callbacks.setExtensionName("Session Handler Plus (SH+)");

        // Restore before the tab is built, so the controls come up showing
        // what the user last set rather than the defaults.
        Settings.init(callbacks);
        Settings.load();

        httpListener = new MyHttpListener(callbacks);
        tab = new MyTab(callbacks, httpListener);

        // Registered once and left in place: capture and auto-replace are
        // switched from the UI, not by adding and removing the listener.
        callbacks.registerHttpListener(httpListener);
        callbacks.customizeUiComponent(tab.getUiComponent());
        callbacks.addSuiteTab(tab);

        Log.print("INFO: XsessionHandler " + Config.EXT_VERSION + " loaded. Built by @" + Config.AUTHOR_HANDLE + " - "
                + Config.AUTHOR_LINK);
        Log.print("INFO: Tokens are captured from responses and injected into outgoing requests automatically."
                + " No session handling rules or macros are needed.");
    }
}
