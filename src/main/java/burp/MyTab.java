package burp;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.FontMetrics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.net.URI;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;

/**
 * The XsessionHandler tab.
 *
 * Deliberately plain: stock Swing components, the host look and feel's own
 * colours and fonts, and grouped titled borders. Nothing here paints itself,
 * so the tab looks like the rest of Burp in both the light and the dark theme
 * and needs no upkeep when Burp restyles.
 */
public class MyTab implements ITab, IExtensionStateListener, MyHttpListener.StatusSink, TokenStore.Listener {

    private static final String[] COLUMNS = { "Token", "Scope", "Len", "Value", "Captured", "Tool", "Source URL" };
    private static final int TAB_TOKENS = 2;

    private final IBurpExtenderCallbacks callbacks;
    private final MyHttpListener httpListener;

    private JPanel root;
    private JTabbedPane tabs;
    private JLabel status;

    private final JPanel captureBody = column();
    private final JPanel injectBody = column();

    private final JCheckBox captureToggle = new JCheckBox("Capture", Config.CAPTURE_ENABLED);
    private final JCheckBox injectToggle = new JCheckBox("Inject", Config.AUTO_REPLACE_ENABLED);
    private final JLabel captureState = new JLabel();
    private final JLabel injectState = new JLabel();

    /**
     * Re-applies each rule's own checkbox state to its fields. A master switch
     * enables a whole tab in one sweep, which would otherwise wake up fields
     * belonging to rules the user had deliberately left off.
     */
    private final List<Runnable> captureSync = new ArrayList<Runnable>();
    private final List<Runnable> injectSync = new ArrayList<Runnable>();

    private final DefaultTableModel tableModel = new DefaultTableModel(COLUMNS, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }

        @Override
        public Class<?> getColumnClass(int column) {
            return column == 2 ? Integer.class : String.class;
        }
    };
    /** Columns are narrower than a token, so the full value lives in a tooltip. */
    private final JTable table = new JTable(tableModel) {
        @Override
        public String getToolTipText(MouseEvent event) {
            int row = rowAtPoint(event.getPoint());
            int column = columnAtPoint(event.getPoint());
            if (row < 0 || column < 0) {
                return null;
            }
            Object value = getValueAt(row, column);
            return value == null ? null : String.valueOf(value);
        }
    };
    private final CardLayout tableSwap = new CardLayout();
    private final JPanel tableArea = new JPanel(tableSwap);

    public MyTab(IBurpExtenderCallbacks callbacks, MyHttpListener httpListener) {
        this.callbacks = callbacks;
        this.httpListener = httpListener;
        build();
        httpListener.setStatusSink(this);
        TokenStore.addListener(this);
        refreshTokenTable();
        say("Ready. Log in through Burp once and every following request carries the token.", false);
    }

    // ----------------------------------------------------------------- build

    private void build() {
        tabs = new JTabbedPane();
        tabs.addTab("Capture", scrollable(buildCaptureTab()));
        tabs.addTab("Inject", scrollable(buildInjectTab()));
        tabs.addTab("Tokens", buildTokensTab());
        tabs.setToolTipTextAt(0, "Where tokens come from - which responses to read and what to pull out of them");
        tabs.setToolTipTextAt(1, "Where tokens go - which requests get rewritten and in which position");
        tabs.setToolTipTextAt(2, "Everything captured so far");

        status = new JLabel(" ");
        status.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));

        JPanel statusBar = new JPanel(new BorderLayout());
        statusBar.add(status, BorderLayout.WEST);
        statusBar.add(link("XsessionHandler on GitHub", Config.PROJECT_LINK), BorderLayout.EAST);
        statusBar.setBorder(BorderFactory.createEmptyBorder(0, 0, 2, 8));

        root = new JPanel(new BorderLayout());
        root.setBorder(BorderFactory.createEmptyBorder(6, 8, 4, 8));
        root.add(buildHeader(), BorderLayout.NORTH);
        root.add(tabs, BorderLayout.CENTER);
        root.add(statusBar, BorderLayout.SOUTH);
    }

    private JScrollPane scrollable(JComponent content) {
        JPanel padded = new JPanel(new BorderLayout());
        padded.add(content, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(padded);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setUnitIncrement(18);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        return scroll;
    }

    // ---------------------------------------------------------------- header

    private JComponent buildHeader() {
        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));

        JPanel titleRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        JLabel name = new JLabel("XsessionHandler " + Config.EXT_VERSION);
        name.setFont(name.getFont().deriveFont(java.awt.Font.BOLD));
        titleRow.add(name);
        titleRow.add(new JLabel("- token capture and injection, straight from the HTTP listener."));
        titleRow.add(new JLabel("  Built by"));
        titleRow.add(link("@" + Config.AUTHOR_HANDLE, Config.AUTHOR_LINK));

        JPanel switchRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        captureToggle.addActionListener((ActionEvent e) -> {
            boolean on = captureToggle.isSelected();
            Config.CAPTURE_ENABLED = on;
            setEnabledDeep(captureBody, on);
            if (on) {
                for (Runnable sync : captureSync) {
                    sync.run();
                }
            }
            refreshStats();
            say("Capture " + (on ? "enabled - responses are being read." : "paused - no tokens will be read."), !on);
        });
        injectToggle.addActionListener((ActionEvent e) -> {
            boolean on = injectToggle.isSelected();
            Config.AUTO_REPLACE_ENABLED = on;
            setEnabledDeep(injectBody, on);
            if (on) {
                for (Runnable sync : injectSync) {
                    sync.run();
                }
            }
            refreshStats();
            say("Inject " + (on ? "enabled - outgoing requests are rewritten." : "paused - requests go out as-is."), !on);
        });
        captureToggle.setToolTipText("Read tokens out of responses");
        injectToggle.setToolTipText("Rewrite outgoing requests with the tokens");

        switchRow.add(captureToggle);
        switchRow.add(captureState);
        switchRow.add(Box.createHorizontalStrut(16));
        switchRow.add(injectToggle);
        switchRow.add(injectState);

        header.add(titleRow);
        header.add(switchRow);
        header.add(Box.createVerticalStrut(4));
        return header;
    }

    // ----------------------------------------------------------- capture tab

    private JComponent buildCaptureTab() {
        JPanel filters = group("Which responses to read");
        filters.add(note("A response is only inspected when every ticked check passes."));
        JPanel filterGrid = grid();
        int row = 0;
        addSetting(filterGrid, row++, captureSync, "Request URL matches", Config.CHECK_REQ_URL_REGEX,
                v -> Config.CHECK_REQ_URL_REGEX = v, Config.VALUE_REQ_URL_REGEX,
                v -> Config.VALUE_REQ_URL_REGEX = v, true, "RegEx, tested against the whole URL");
        addSetting(filterGrid, row++, captureSync, "Response header contains", Config.CHECK_RESP_CONTENT_TYPE,
                v -> Config.CHECK_RESP_CONTENT_TYPE = v, Config.VALUE_RESP_CONTENT_TYPE_HEADER,
                v -> Config.VALUE_RESP_CONTENT_TYPE_HEADER = v, false, "plain text, not a RegEx");
        filters.add(filterGrid);

        JPanel extract = group("What to pull out");
        extract.add(note("Group 1 of each RegEx is stored as the token. "
                + "Leave one unticked if your app has no such token."));
        JPanel extractGrid = grid();
        row = 0;
        addSetting(extractGrid, row++, captureSync, "TOKEN-1", Config.LISTEN_4_ACCESS_TOKEN,
                v -> Config.LISTEN_4_ACCESS_TOKEN = v, Config.VALUE_READ_JWT_REGEX,
                v -> Config.VALUE_READ_JWT_REGEX = v, true, "JWT / access token");
        addSetting(extractGrid, row++, captureSync, "TOKEN-2", Config.LISTEN_4_REFRESH_TOKEN,
                v -> Config.LISTEN_4_REFRESH_TOKEN = v, Config.VALUE_READ_REFRESH_TOKEN_REGEX,
                v -> Config.VALUE_READ_REFRESH_TOKEN_REGEX = v, true, "refresh / CSRF token");
        extract.add(extractGrid);

        JPanel scope = group("Testing more than one app at once (optional)");
        scope.add(note("Two apps that both return access_token would otherwise share one slot and keep overwriting "
                + "each other. To keep them apart, add a header to each login request naming the path that token "
                + "belongs to - for example \"Shp-Path: /appA/\" - and the token is filed under /appA/ instead of /. "
                + "Requests to /appA/... then get that token back. You add the header yourself, with a Proxy "
                + "match/replace rule or by hand."));
        JPanel scopeGrid = grid();
        addSetting(scopeGrid, 0, captureSync, "Read the scope from this request header", Config.CHECK_REQ_PATH,
                v -> Config.CHECK_REQ_PATH = v, Config.VALUE_REQ_PATH_HEADER,
                v -> Config.VALUE_REQ_PATH_HEADER = v, true, "group 1 becomes the scope");
        scope.add(scopeGrid);

        JPanel debug = group("Not seeing your token?");
        JCheckBox rejectLogs = new JCheckBox("Log every response that was skipped, and say why",
                Config.CHECK_PRINT_REJECT_LOGS_1A_1B);
        rejectLogs.addActionListener((ActionEvent e) -> {
            Config.CHECK_PRINT_REJECT_LOGS_1A_1B = rejectLogs.isSelected();
            say("Skipped-response logging " + (rejectLogs.isSelected()
                    ? "on - check the Extensions output tab." : "off."), false);
        });
        debug.add(leftAligned(rejectLogs));

        captureBody.add(filters);
        captureBody.add(extract);
        captureBody.add(scope);
        captureBody.add(debug);
        setEnabledDeep(captureBody, Config.CAPTURE_ENABLED);
        return captureBody;
    }

    // ------------------------------------------------------------ inject tab

    private JComponent buildInjectTab() {
        JPanel tools = group("Which Burp tools to rewrite");
        tools.add(note("Extender is off by default so your own token-fetch traffic "
                + "is not rewritten with a stale value."));
        JPanel toolRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 2));
        final JCheckBox[] boxes = new JCheckBox[Config.TOOLS.length];
        for (int i = 0; i < Config.TOOLS.length; i++) {
            final Config.Tool tool = Config.TOOLS[i];
            JCheckBox box = new JCheckBox(tool.label, tool.enabled);
            box.addActionListener((ActionEvent e) -> {
                tool.enabled = box.isSelected();
                say("Injection for " + tool.label + " " + (tool.enabled ? "on." : "off."), false);
            });
            boxes[i] = box;
            toolRow.add(box);
        }
        tools.add(toolRow);
        JPanel quick = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        quick.add(button("Select All", e -> setAllTools(boxes, true)));
        quick.add(button("Select None", e -> setAllTools(boxes, false)));
        tools.add(quick);

        JPanel token1 = group("TOKEN-1 goes here (JWT / access token)");
        JPanel grid1 = grid();
        addReplaceHeader(grid1);
        addReplace(grid1, 1, "Request header", Config.CHECK_TOKEN1_HEADER_1, v -> Config.CHECK_TOKEN1_HEADER_1 = v,
                Config.VAL_JWT_HEADER_NAME_1, v -> Config.VAL_JWT_HEADER_NAME_1 = v, false, null, null,
                Config.REPLACE_STRUCTURE_TOKEN1_HEAD_1, v -> Config.REPLACE_STRUCTURE_TOKEN1_HEAD_1 = v,
                Config.CHECK_TOKEN1_HEADER_ADD_IF_NOT_EXIST_1,
                v -> Config.CHECK_TOKEN1_HEADER_ADD_IF_NOT_EXIST_1 = v, "TOKEN-1");
        addReplace(grid1, 2, "A second header", Config.CHECK_TOKEN1_HEADER_2, v -> Config.CHECK_TOKEN1_HEADER_2 = v,
                Config.VAL_JWT_HEADER_NAME_2, v -> Config.VAL_JWT_HEADER_NAME_2 = v, false, null, null,
                Config.REPLACE_STRUCTURE_TOKEN1_HEAD_2, v -> Config.REPLACE_STRUCTURE_TOKEN1_HEAD_2 = v,
                Config.CHECK_TOKEN1_HEADER_ADD_IF_NOT_EXIST_2,
                v -> Config.CHECK_TOKEN1_HEADER_ADD_IF_NOT_EXIST_2 = v, "TOKEN-1");
        addReplace(grid1, 3, "A body parameter", Config.CHECK_TOKEN1_BODY, v -> Config.CHECK_TOKEN1_BODY = v,
                Config.VAL_JWT_BODY_NAME_1, v -> Config.VAL_JWT_BODY_NAME_1 = v, true,
                Config.VAL_JWT_BODY_NAME_2, v -> Config.VAL_JWT_BODY_NAME_2 = v,
                Config.REPLACE_STRUCTURE_TOKEN1_BODY, v -> Config.REPLACE_STRUCTURE_TOKEN1_BODY = v, null, null,
                "TOKEN-1");
        token1.add(grid1);

        JPanel token2 = group("TOKEN-2 goes here (refresh / CSRF token)");
        JPanel grid2 = grid();
        addReplaceHeader(grid2);
        addReplace(grid2, 1, "Request header", Config.CHECK_TOKEN2_HEADER, v -> Config.CHECK_TOKEN2_HEADER = v,
                Config.VAL_TOKEN2_HDR_PARAM_NAME_1, v -> Config.VAL_TOKEN2_HDR_PARAM_NAME_1 = v, false, null, null,
                Config.REPLACE_STRUCTURE_TOKEN2_HEAD, v -> Config.REPLACE_STRUCTURE_TOKEN2_HEAD = v,
                Config.CHECK_TOKEN2_HEADER_ADD_IF_NOT_EXIST, v -> Config.CHECK_TOKEN2_HEADER_ADD_IF_NOT_EXIST = v,
                "TOKEN-2");
        addReplace(grid2, 2, "A body parameter", Config.CHECK_TOKEN2_BODY, v -> Config.CHECK_TOKEN2_BODY = v,
                Config.VAL_TOKEN2_BODY_PARAM_NAME_1, v -> Config.VAL_TOKEN2_BODY_PARAM_NAME_1 = v, true,
                Config.VAL_TOKEN2_BODY_PARAM_NAME_2, v -> Config.VAL_TOKEN2_BODY_PARAM_NAME_2 = v,
                Config.REPLACE_STRUCTURE_TOKEN2_BODY, v -> Config.REPLACE_STRUCTURE_TOKEN2_BODY = v, null, null,
                "TOKEN-2");
        token2.add(grid2);

        injectBody.add(tools);
        injectBody.add(token1);
        injectBody.add(token2);
        setEnabledDeep(injectBody, Config.AUTO_REPLACE_ENABLED);
        return injectBody;
    }

    private void setAllTools(JCheckBox[] boxes, boolean on) {
        for (int i = 0; i < boxes.length; i++) {
            boxes[i].setSelected(on);
            Config.TOOLS[i].enabled = on;
        }
        say(on ? "Injecting into every Burp tool." : "Injection disabled for every tool.", !on);
    }

    // ------------------------------------------------------------ tokens tab

    private JComponent buildTokensTab() {
        JPanel panel = new JPanel(new BorderLayout(0, 4));
        panel.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

        styleTable();
        JScrollPane scroll = new JScrollPane(table);
        // JTable only installs its header on addNotify; set it up front so the
        // column titles are there from the first paint.
        scroll.setColumnHeaderView(table.getTableHeader());

        JPanel empty = new JPanel(new BorderLayout());
        empty.setBorder(UIManager.getBorder("ScrollPane.border"));
        JLabel hint = new JLabel("<html><div style='text-align:center'>"
                + "No tokens captured yet.<br><br>"
                + "Log in to your application through Burp.<br>"
                + "If nothing shows up, check the RegEx on the Capture tab."
                + "</div></html>", SwingConstants.CENTER);
        empty.add(hint, BorderLayout.CENTER);

        tableArea.add(empty, "empty");
        tableArea.add(scroll, "table");

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        toolbar.add(button("Copy Value", e -> copySelectedValue()));
        toolbar.add(button("Remove Selected", e -> removeSelected()));
        toolbar.add(button("Clear All", e -> {
            int removed = TokenStore.clear();
            Log.print("[" + Log.now() + "] TOKENS CLEARED [" + removed + "]");
            say(removed + " token(s) cleared.", false);
        }));

        panel.add(toolbar, BorderLayout.NORTH);
        panel.add(tableArea, BorderLayout.CENTER);
        panel.add(note("Tokens are held in memory only and are cleared when the extension unloads. "
                + "Click a column heading to sort."), BorderLayout.SOUTH);
        return panel;
    }

    private void styleTable() {
        table.setAutoCreateRowSorter(true);
        table.setRowHeight(Math.max(20, table.getFontMetrics(table.getFont()).getHeight() + 6));
        int[] widths = { 80, 130, 50, 520, 80, 80, 320 };
        for (int i = 0; i < widths.length; i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }
    }

    private void copySelectedValue() {
        int row = table.getSelectedRow();
        if (row < 0) {
            say("Select a row in the table first.", true);
            return;
        }
        String value = String.valueOf(tableModel.getValueAt(table.convertRowIndexToModel(row), 3));
        java.awt.Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new java.awt.datatransfer.StringSelection(value), null);
        say("Value copied to clipboard (" + value.length() + " chars).", false);
    }

    private void removeSelected() {
        int row = table.getSelectedRow();
        if (row < 0) {
            say("Select a row in the table first.", true);
            return;
        }
        int modelRow = table.convertRowIndexToModel(row);
        String name = String.valueOf(tableModel.getValueAt(modelRow, 0));
        String path = String.valueOf(tableModel.getValueAt(modelRow, 1));
        TokenStore.remove(name, path);
        say("Removed " + name + " for scope '" + path + "'.", false);
    }

    @Override
    public void tokensChanged() {
        SwingUtilities.invokeLater(this::refreshTokenTable);
    }

    private void refreshTokenTable() {
        List<TokenStore.Entry> entries = TokenStore.all();
        SimpleDateFormat format = new SimpleDateFormat("HH:mm:ss");
        tableModel.setRowCount(0);
        for (TokenStore.Entry entry : entries) {
            tableModel.addRow(new Object[] { entry.name, entry.path, entry.value.length(), entry.value,
                    format.format(entry.updated), entry.tool, entry.sourceUrl });
        }
        tableSwap.show(tableArea, entries.isEmpty() ? "empty" : "table");
        if (tabs != null) {
            tabs.setTitleAt(TAB_TOKENS, entries.isEmpty() ? "Tokens" : "Tokens (" + entries.size() + ")");
        }
        refreshStats();
    }

    private void refreshStats() {
        captureState.setText("(" + (Config.CAPTURE_ENABLED ? "ON" : "OFF") + ")  "
                + Stats.captured() + " captured");
        injectState.setText("(" + (Config.AUTO_REPLACE_ENABLED ? "ON" : "OFF") + ")  "
                + Stats.injected() + " injected");
    }

    // -------------------------------------------------------------- controls

    /** A titled group box, the way the rest of Burp separates settings. */
    private static JPanel group(String title) {
        return new Group(title);
    }

    /**
     * A vertical group that forces every child to the left margin.
     *
     * BoxLayout lines children up on their alignmentX, and a plain JPanel
     * defaults to centred - so one FlowLayout row in the stack is enough to
     * drag every left-aligned label in the same group off the margin with it.
     */
    private static class Group extends JPanel {
        Group(String title) {
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createTitledBorder(title),
                    BorderFactory.createEmptyBorder(4, 4, 4, 4)));
            setAlignmentX(Component.LEFT_ALIGNMENT);
        }

        @Override
        public Component add(Component component) {
            if (component instanceof JComponent) {
                ((JComponent) component).setAlignmentX(Component.LEFT_ALIGNMENT);
            }
            return super.add(component);
        }

        @Override
        public Dimension getMaximumSize() {
            return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
        }
    }

    private static JPanel column() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        return panel;
    }

    private static JPanel grid() {
        JPanel panel = new JPanel(new GridBagLayout()) {
            @Override
            public Dimension getMaximumSize() {
                return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
            }
        };
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        return panel;
    }

    /**
     * Pins a component to the left margin. BorderLayout.WEST hands it exactly
     * its preferred width at x=0, which a stretched component in a vertical
     * BoxLayout does not reliably do.
     */
    private static JComponent leftAligned(JComponent component) {
        JPanel row = new JPanel(new BorderLayout()) {
            @Override
            public Dimension getMaximumSize() {
                // Asked for on every layout pass rather than captured once, so
                // a wrapped label that turns out taller than first measured is
                // not clipped against a stale height.
                return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
            }
        };
        row.add(component, BorderLayout.WEST);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        return row;
    }

    /** A wrapped line of explanatory text inside a group. */
    private static JComponent note(String text) {
        JLabel label = new JLabel("<html><body style='width:820px'>" + text + "</body></html>");
        label.setBorder(BorderFactory.createEmptyBorder(0, 2, 4, 2));
        // Wrapped in a left-flowing row: a bare label stretches to the width of
        // the group and a short single line then floats away from the margin.
        return leftAligned(label);
    }

    private static JButton button(String text, java.awt.event.ActionListener action) {
        JButton button = new JButton(text);
        button.addActionListener(action);
        return button;
    }

    /** A clickable label that opens a URL in the browser. */
    private static JLabel link(String text, String url) {
        JLabel label = new JLabel("<html><a href=''>" + text + "</a></html>");
        label.setCursor(new Cursor(Cursor.HAND_CURSOR));
        label.setToolTipText(url);
        label.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                try {
                    Desktop.getDesktop().browse(URI.create(url));
                } catch (Exception ignored) {
                    // No browser available; the tooltip still shows the URL.
                }
            }
        });
        return label;
    }

    private void addSetting(JPanel grid, int row, List<Runnable> syncs, String label, boolean checked,
                            Consumer<Boolean> onToggle, String value, Consumer<String> setter, boolean isRegex,
                            String hint) {
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(2, 2, 2, 8);
        c.gridy = row;
        c.anchor = GridBagConstraints.WEST;

        JCheckBox box = new JCheckBox(label, checked);
        JTextField field = field(value, setter, isRegex, label);

        box.addActionListener((ActionEvent e) -> {
            onToggle.accept(box.isSelected());
            field.setEnabled(box.isSelected());
            say(label + " " + (box.isSelected() ? "on." : "off."), false);
        });
        field.setEnabled(checked);
        syncs.add(() -> field.setEnabled(box.isSelected()));

        c.gridx = 0;
        c.weightx = 0;
        c.fill = GridBagConstraints.NONE;
        grid.add(box, c);

        c.gridx = 1;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        grid.add(field, c);

        c.gridx = 2;
        c.weightx = 0;
        c.fill = GridBagConstraints.NONE;
        grid.add(new JLabel(hint), c);
    }

    private void addReplaceHeader(JPanel grid) {
        String[] titles = { "Position", "Match", "Replace with", "Format", "", "Sends" };
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(2, 2, 2, 8);
        c.gridy = 0;
        c.anchor = GridBagConstraints.WEST;
        for (int i = 0; i < titles.length; i++) {
            c.gridx = i;
            c.weightx = (i == 1 || i == 2 || i == 5) ? 1 : 0;
            c.fill = GridBagConstraints.HORIZONTAL;
            grid.add(new JLabel(titles[i]), c);
        }
    }

    /**
     * One injection position on a single line. The last cell previews exactly
     * what will go on the wire, composed from this row's inputs and a real
     * captured token when there is one, so a format string can be checked
     * without sending a request.
     */
    private void addReplace(JPanel grid, int row, String label, boolean checked, Consumer<Boolean> onToggle,
                            String matchValue, Consumer<String> matchSetter, boolean matchIsRegex,
                            String withValue, Consumer<String> withSetter,
                            String formatValue, Consumer<String> formatSetter,
                            Boolean addChecked, Consumer<Boolean> onAddToggle, String tokenName) {
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(2, 2, 2, 8);
        c.gridy = row;
        c.anchor = GridBagConstraints.WEST;

        JCheckBox box = new JCheckBox(label, checked);
        JTextField matchField = field(matchValue, matchSetter, matchIsRegex, label);
        matchField.setToolTipText(matchIsRegex ? "RegEx matched against the request body"
                : "The header prefix to look for, ex: Authorization: Bearer");
        JTextField withField = withValue == null ? null : field(withValue, withSetter, false, label);
        if (withField != null) {
            withField.setToolTipText("The literal text put back in front of the token");
        }
        JTextField formatField = field(formatValue, formatSetter, false, label);
        formatField.setColumns(5);
        formatField.setToolTipText("First %s is the name, second %s is the token");

        JCheckBox addBox = addChecked == null ? null : new JCheckBox("Add if missing", addChecked);
        if (addBox != null) {
            addBox.setToolTipText("Add this header when the request does not already carry it");
        }

        Preview preview = new Preview();
        Runnable updatePreview = () -> {
            String name = withField != null ? withField.getText() : matchField.getText();
            String token = sampleToken(tokenName);
            String composed;
            try {
                composed = String.format(formatField.getText(), name, token);
            } catch (Exception e) {
                composed = name + token;
            }
            preview.set(composed);
        };
        updatePreview.run();
        onChange(matchField, t -> updatePreview.run());
        onChange(formatField, t -> updatePreview.run());
        if (withField != null) {
            onChange(withField, t -> updatePreview.run());
        }

        Runnable sync = () -> {
            boolean on = box.isSelected();
            matchField.setEnabled(on);
            if (withField != null) {
                withField.setEnabled(on);
            }
            formatField.setEnabled(on);
            if (addBox != null) {
                addBox.setEnabled(on);
            }
            preview.setEnabled(on);
        };
        injectSync.add(sync);

        box.addActionListener((ActionEvent e) -> {
            boolean on = box.isSelected();
            onToggle.accept(on);
            sync.run();
            say(label + " " + (on ? "will be rewritten with " + tokenName + "." : "will be left alone."), false);
        });
        if (addBox != null) {
            addBox.addActionListener((ActionEvent e) -> {
                onAddToggle.accept(addBox.isSelected());
                say("'Add if missing' for " + label + " " + (addBox.isSelected() ? "on." : "off."), false);
            });
        }
        sync.run();

        c.gridx = 0;
        c.weightx = 0;
        c.fill = GridBagConstraints.NONE;
        grid.add(box, c);

        c.gridx = 1;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        grid.add(matchField, c);

        c.gridx = 2;
        grid.add(withField == null ? new JLabel("-") : withField, c);

        c.gridx = 3;
        c.weightx = 0;
        c.fill = GridBagConstraints.NONE;
        grid.add(formatField, c);

        c.gridx = 4;
        grid.add(addBox == null ? new JLabel("") : addBox, c);

        c.gridx = 5;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        grid.add(preview, c);
    }

    /** A real captured token if there is one, otherwise a readable placeholder. */
    private static String sampleToken(String tokenName) {
        for (TokenStore.Entry entry : TokenStore.all()) {
            if (entry.name.equals(tokenName)) {
                return PyStr.slice(entry.value, 0, 16) + (entry.value.length() > 16 ? "..." : "");
            }
        }
        return "<" + tokenName + ">";
    }

    /**
     * A read-only line that shrinks with its column instead of forcing the row
     * wider, since a token is far longer than the space a preview can have.
     */
    private static class Preview extends JLabel {
        void set(String text) {
            setText(text);
            setToolTipText(text);
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(60, super.getPreferredSize().height);
        }

        @Override
        public Dimension getMinimumSize() {
            return new Dimension(40, super.getPreferredSize().height);
        }

        @Override
        public String getText() {
            String text = super.getText();
            if (text == null || getWidth() <= 0) {
                return text;
            }
            FontMetrics metrics = getFontMetrics(getFont());
            if (metrics.stringWidth(text) <= getWidth()) {
                return text;
            }
            int end = text.length();
            while (end > 0 && metrics.stringWidth(text.substring(0, end) + "...") > getWidth()) {
                end--;
            }
            return text.substring(0, end) + "...";
        }
    }

    private JTextField field(String value, Consumer<String> setter, boolean isRegex, String label) {
        JTextField field = new JTextField(value, 16);
        final Border normal = field.getBorder();
        final Border invalid = BorderFactory.createLineBorder(Color.RED);
        onChange(field, text -> {
            // A blank value is never a usable rule, and refusing it also keeps
            // the previous value intact while a paste is in flight: replacing
            // the text deletes it first, and that empty moment would otherwise
            // be applied as a pattern that matches everything.
            if (text.trim().isEmpty()) {
                field.setBorder(invalid);
                field.setToolTipText("Empty - the previous value is still in use");
                say(label + " is empty, not applied - the previous value is still in use.", true);
                return;
            }
            if (isRegex) {
                try {
                    Pattern.compile(text);
                } catch (Exception ex) {
                    field.setBorder(invalid);
                    field.setToolTipText(ex.getMessage());
                    say(label + ": invalid RegEx, not applied - " + firstLine(ex.getMessage()), true);
                    return;
                }
            }
            field.setBorder(normal);
            setter.accept(text);
            say(label + " set to " + text, false);
        });
        return field;
    }

    private static void onChange(JTextField field, Consumer<String> handler) {
        field.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                handler.accept(field.getText());
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                handler.accept(field.getText());
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                handler.accept(field.getText());
            }
        });
    }

    // ------------------------------------------------------------ status bar

    @Override
    public void status(String message) {
        SwingUtilities.invokeLater(() -> {
            say(message, false);
            refreshStats();
        });
    }

    /** Last thing that happened. Only a problem gets colour. */
    private void say(String text, boolean problem) {
        if (status != null) {
            status.setText(text);
            status.setForeground(problem ? Color.RED : UIManager.getColor("Label.foreground"));
        }
    }

    private static String firstLine(String text) {
        if (text == null) {
            return "";
        }
        int newline = text.indexOf('\n');
        return newline < 0 ? text : text.substring(0, newline);
    }

    private static void setEnabledDeep(Container container, boolean enabled) {
        for (Component component : container.getComponents()) {
            component.setEnabled(enabled);
            if (component instanceof Container) {
                setEnabledDeep((Container) component, enabled);
            }
        }
    }

    @Override
    public String getTabCaption() {
        return "SH+";
    }

    @Override
    public Component getUiComponent() {
        return root;
    }

    @Override
    public void extensionUnloaded() {
    }
}
