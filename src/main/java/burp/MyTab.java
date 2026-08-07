package burp;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.text.SimpleDateFormat;
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
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellRenderer;

import burp.ui.Card;
import burp.ui.SwitchButton;
import burp.ui.Theme;
import burp.ui.Ui;
import burp.ui.UiTextField;

/**
 * The XsessionHandler tab.
 *
 * Layout: a header that is always visible (branding, the two master switches
 * and the live counters), a tabbed middle - Capture / Inject / Tokens - and a
 * status bar pinned to the bottom. New functionality gets its own tab.
 */
public class MyTab implements ITab, IExtensionStateListener, MyHttpListener.StatusSink, TokenStore.Listener {

    private static final String[] COLUMNS = { "Token", "Scope", "Len", "Value", "Captured", "Tool", "Source URL" };
    private static final int TAB_TOKENS = 2;

    private final IBurpExtenderCallbacks callbacks;
    private final MyHttpListener httpListener;

    private JPanel root;
    private JTabbedPane tabs;
    private StatusBar statusBar;

    private final JPanel captureBody = Ui.column();
    private final JPanel injectBody = Ui.column();

    private final Ui.Pill capturePill = new Ui.Pill("ON", Theme.success());
    private final Ui.Pill injectPill = new Ui.Pill("ON", Theme.success());
    private final JLabel captureStat = Ui.muted("0 captured");
    private final JLabel injectStat = Ui.muted("0 injected");

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
    private final JTable table = new JTable(tableModel);
    private final CardLayout tableSwap = new CardLayout();
    private final JPanel tableArea = new JPanel(tableSwap);

    public MyTab(IBurpExtenderCallbacks callbacks, MyHttpListener httpListener) {
        this.callbacks = callbacks;
        this.httpListener = httpListener;
        build();
        httpListener.setStatusSink(this);
        TokenStore.addListener(this);
        refreshTokenTable();
        say("Ready. Log in through Burp once and every following request carries the token.", Theme.success());
    }

    // ----------------------------------------------------------------- build

    private void build() {
        tabs = new JTabbedPane();
        tabs.setFont(Theme.base());
        tabs.addTab("  Capture  ", scrollable(buildCaptureTab()));
        tabs.addTab("  Inject  ", scrollable(buildInjectTab()));
        tabs.addTab("  Tokens  ", buildTokensTab());
        tabs.setToolTipTextAt(0, "Where tokens come from - which responses to read and what to pull out of them");
        tabs.setToolTipTextAt(1, "Where tokens go - which requests get rewritten and in which position");
        tabs.setToolTipTextAt(2, "Everything captured so far");

        statusBar = new StatusBar();

        root = new JPanel(new BorderLayout());
        root.setBackground(Theme.background());
        root.add(buildHeader(), BorderLayout.NORTH);
        root.add(tabs, BorderLayout.CENTER);
        root.add(statusBar, BorderLayout.SOUTH);
    }

    private JScrollPane scrollable(JComponent content) {
        JPanel padded = new JPanel(new BorderLayout());
        padded.setOpaque(false);
        padded.setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));
        padded.add(content, BorderLayout.NORTH);

        JScrollPane scroll = new JScrollPane(padded);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(Theme.background());
        scroll.getVerticalScrollBar().setUnitIncrement(18);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        return scroll;
    }

    // ---------------------------------------------------------------- header

    private JComponent buildHeader() {
        JPanel header = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setColor(Theme.surface());
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.setColor(Theme.line());
                g2.drawLine(0, getHeight() - 1, getWidth(), getHeight() - 1);
                g2.dispose();
            }
        };
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setBorder(BorderFactory.createEmptyBorder(11, 16, 11, 16));

        JPanel titleRow = new JPanel(new BorderLayout());
        titleRow.setOpaque(false);
        // BoxLayout aligns children by alignmentX; without this the title row
        // (0.5 by default) and the control row (0.0) do not line up.
        titleRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        JPanel left = Ui.row(8);
        JLabel name = new JLabel("XsessionHandler");
        name.setFont(Theme.title());
        name.setForeground(Theme.foreground());
        left.add(name);
        left.add(new Ui.Pill(Config.EXT_VERSION, Theme.accent()));
        left.add(Ui.muted("   Token capture and injection, straight from the HTTP listener"));
        JPanel credit = Ui.row(6);
        credit.add(Ui.muted("Built by"));
        credit.add(Ui.link("@" + Config.AUTHOR_HANDLE, Config.AUTHOR_LINK));
        titleRow.add(left, BorderLayout.WEST);
        titleRow.add(credit, BorderLayout.EAST);

        JPanel controls = Ui.row(0);
        controls.setBorder(BorderFactory.createEmptyBorder(9, 0, 0, 0));
        controls.add(masterSwitch(true));
        controls.add(Ui.strut(26));
        controls.add(masterSwitch(false));

        header.add(titleRow);
        header.add(controls);
        return header;
    }

    /** One always-visible master control: switch, name, ON/OFF pill, counter. */
    private JComponent masterSwitch(boolean isCapture) {
        JPanel group = Ui.row(7);
        SwitchButton toggle = new SwitchButton(isCapture ? Config.CAPTURE_ENABLED : Config.AUTO_REPLACE_ENABLED);
        toggle.addActionListener((ActionEvent e) -> {
            boolean on = toggle.isSelected();
            if (isCapture) {
                Config.CAPTURE_ENABLED = on;
                setEnabledDeep(captureBody, on);
                capturePill.set(on ? "ON" : "OFF", on ? Theme.success() : Theme.muted());
                say("Capture " + (on ? "enabled - responses are being read." : "paused - no tokens will be read."),
                        on ? Theme.success() : Theme.warning());
            } else {
                Config.AUTO_REPLACE_ENABLED = on;
                setEnabledDeep(injectBody, on);
                injectPill.set(on ? "ON" : "OFF", on ? Theme.success() : Theme.muted());
                say("Inject " + (on ? "enabled - outgoing requests are rewritten." : "paused - requests go out as-is."),
                        on ? Theme.success() : Theme.warning());
            }
        });

        JLabel title = new JLabel(isCapture ? "Capture" : "Inject");
        title.setFont(Theme.bold());
        title.setForeground(Theme.foreground());

        group.add(toggle);
        group.add(title);
        group.add(isCapture ? capturePill : injectPill);
        group.add(isCapture ? captureStat : injectStat);
        group.setToolTipText(isCapture ? "Read tokens out of responses" : "Rewrite outgoing requests with the tokens");
        return group;
    }

    // ----------------------------------------------------------- capture tab

    private JComponent buildCaptureTab() {
        Card filters = new Card("1", "Which responses to read",
                "A response is only inspected when every ticked check passes.");
        JPanel filterGrid = grid();
        int row = 0;
        addSetting(filterGrid, row++, "Request URL matches", Config.CHECK_REQ_URL_REGEX,
                v -> Config.CHECK_REQ_URL_REGEX = v, Config.VALUE_REQ_URL_REGEX,
                v -> Config.VALUE_REQ_URL_REGEX = v, true, "RegEx, tested against the whole URL");
        addSetting(filterGrid, row++, "Response header contains", Config.CHECK_RESP_CONTENT_TYPE,
                v -> Config.CHECK_RESP_CONTENT_TYPE = v, Config.VALUE_RESP_CONTENT_TYPE_HEADER,
                v -> Config.VALUE_RESP_CONTENT_TYPE_HEADER = v, false, "plain text, not a RegEx");
        filters.add(filterGrid);

        Card extract = new Card("2", "What to pull out",
                "Group 1 of each RegEx is stored as the token. Leave one unticked if your app has no such token.");
        JPanel extractGrid = grid();
        row = 0;
        addSetting(extractGrid, row++, "TOKEN-1", Config.LISTEN_4_ACCESS_TOKEN,
                v -> Config.LISTEN_4_ACCESS_TOKEN = v, Config.VALUE_READ_JWT_REGEX,
                v -> Config.VALUE_READ_JWT_REGEX = v, true, "JWT / access token");
        addSetting(extractGrid, row++, "TOKEN-2", Config.LISTEN_4_REFRESH_TOKEN,
                v -> Config.LISTEN_4_REFRESH_TOKEN = v, Config.VALUE_READ_REFRESH_TOKEN_REGEX,
                v -> Config.VALUE_READ_REFRESH_TOKEN_REGEX = v, true, "refresh / CSRF token");
        extract.add(extractGrid);

        Card scope = new Card("3", "Testing more than one app at once?",
                "Optional. Skip this if you are working on a single application.");
        scope.add(Ui.muted("<html><body style='width:640px'>"
                + "Two apps that both return <code>access_token</code> would otherwise share one slot and keep "
                + "overwriting each other. To keep them apart, add a header to each <b>login</b> request naming the "
                + "path that token belongs to - for example <code>Shp-Path: /appA/</code> - and the token is filed "
                + "under <code>/appA/</code> instead of <code>/</code>. Requests to <code>/appA/...</code> then get "
                + "that token back. You add the header yourself, with a Proxy match/replace rule or by hand."
                + "</body></html>"));
        scope.gap(8);
        JPanel scopeGrid = grid();
        addSetting(scopeGrid, 0, "Read the scope from this request header", Config.CHECK_REQ_PATH,
                v -> Config.CHECK_REQ_PATH = v, Config.VALUE_REQ_PATH_HEADER,
                v -> Config.VALUE_REQ_PATH_HEADER = v, true, "group 1 becomes the scope");
        scope.add(scopeGrid);

        Card debug = new Card("4", "Not seeing your token?", "Turn this on to find out which check is rejecting it.");
        JCheckBox rejectLogs = Ui.checkBox("Log every response that was skipped, and say why",
                Config.CHECK_PRINT_REJECT_LOGS_1A_1B);
        rejectLogs.addActionListener((ActionEvent e) -> {
            Config.CHECK_PRINT_REJECT_LOGS_1A_1B = rejectLogs.isSelected();
            say("Skipped-response logging " + (rejectLogs.isSelected()
                    ? "on - check the Extensions output tab."
                    : "off."), Theme.accent());
        });
        debug.add(rejectLogs);

        captureBody.add(filters);
        captureBody.add(Box.createVerticalStrut(12));
        captureBody.add(extract);
        captureBody.add(Box.createVerticalStrut(12));
        captureBody.add(scope);
        captureBody.add(Box.createVerticalStrut(12));
        captureBody.add(debug);
        setEnabledDeep(captureBody, Config.CAPTURE_ENABLED);
        return captureBody;
    }

    // ------------------------------------------------------------ inject tab

    private JComponent buildInjectTab() {
        Card tools = new Card("1", "Which Burp tools to rewrite",
                "Extender is off by default so your own token-fetch traffic is not rewritten with a stale value.");
        JPanel toolRow = Ui.row(10);
        final JCheckBox[] boxes = new JCheckBox[Config.TOOLS.length];
        for (int i = 0; i < Config.TOOLS.length; i++) {
            final Config.Tool tool = Config.TOOLS[i];
            JCheckBox box = Ui.checkBox(tool.label, tool.enabled);
            box.addActionListener((ActionEvent e) -> {
                tool.enabled = box.isSelected();
                say("Injection for " + tool.label + " " + (tool.enabled ? "on." : "off."), Theme.accent());
            });
            boxes[i] = box;
            toolRow.add(box);
        }
        tools.add(toolRow);
        tools.gap(4);
        JPanel quick = Ui.row(10);
        quick.add(clickable("Select all", () -> setAllTools(boxes, true)));
        quick.add(clickable("Select none", () -> setAllTools(boxes, false)));
        tools.add(quick);

        Card token1 = new Card("2", "TOKEN-1 goes here", "JWT / access token. Tick every position your app uses.");
        JPanel grid1 = grid();
        addReplaceHeader(grid1);
        addReplace(grid1, 1, "Request header", Config.CHECK_TOKEN1_HEADER_1, v -> Config.CHECK_TOKEN1_HEADER_1 = v,
                Config.VAL_JWT_HEADER_NAME_1, v -> Config.VAL_JWT_HEADER_NAME_1 = v, false, null, null,
                Config.REPLACE_STRUCTURE_TOKEN1_HEAD_1, v -> Config.REPLACE_STRUCTURE_TOKEN1_HEAD_1 = v,
                Config.CHECK_TOKEN1_HEADER_ADD_IF_NOT_EXIST_1,
                v -> Config.CHECK_TOKEN1_HEADER_ADD_IF_NOT_EXIST_1 = v, "TOKEN-1");
        addReplace(grid1, 3, "A second header", Config.CHECK_TOKEN1_HEADER_2, v -> Config.CHECK_TOKEN1_HEADER_2 = v,
                Config.VAL_JWT_HEADER_NAME_2, v -> Config.VAL_JWT_HEADER_NAME_2 = v, false, null, null,
                Config.REPLACE_STRUCTURE_TOKEN1_HEAD_2, v -> Config.REPLACE_STRUCTURE_TOKEN1_HEAD_2 = v,
                Config.CHECK_TOKEN1_HEADER_ADD_IF_NOT_EXIST_2,
                v -> Config.CHECK_TOKEN1_HEADER_ADD_IF_NOT_EXIST_2 = v, "TOKEN-1");
        addReplace(grid1, 5, "A body parameter", Config.CHECK_TOKEN1_BODY, v -> Config.CHECK_TOKEN1_BODY = v,
                Config.VAL_JWT_BODY_NAME_1, v -> Config.VAL_JWT_BODY_NAME_1 = v, true,
                Config.VAL_JWT_BODY_NAME_2, v -> Config.VAL_JWT_BODY_NAME_2 = v,
                Config.REPLACE_STRUCTURE_TOKEN1_BODY, v -> Config.REPLACE_STRUCTURE_TOKEN1_BODY = v, null, null,
                "TOKEN-1");
        token1.add(grid1);

        Card token2 = new Card("3", "TOKEN-2 goes here", "Refresh / CSRF token.");
        JPanel grid2 = grid();
        addReplaceHeader(grid2);
        addReplace(grid2, 1, "Request header", Config.CHECK_TOKEN2_HEADER, v -> Config.CHECK_TOKEN2_HEADER = v,
                Config.VAL_TOKEN2_HDR_PARAM_NAME_1, v -> Config.VAL_TOKEN2_HDR_PARAM_NAME_1 = v, false, null, null,
                Config.REPLACE_STRUCTURE_TOKEN2_HEAD, v -> Config.REPLACE_STRUCTURE_TOKEN2_HEAD = v,
                Config.CHECK_TOKEN2_HEADER_ADD_IF_NOT_EXIST, v -> Config.CHECK_TOKEN2_HEADER_ADD_IF_NOT_EXIST = v,
                "TOKEN-2");
        addReplace(grid2, 3, "A body parameter", Config.CHECK_TOKEN2_BODY, v -> Config.CHECK_TOKEN2_BODY = v,
                Config.VAL_TOKEN2_BODY_PARAM_NAME_1, v -> Config.VAL_TOKEN2_BODY_PARAM_NAME_1 = v, true,
                Config.VAL_TOKEN2_BODY_PARAM_NAME_2, v -> Config.VAL_TOKEN2_BODY_PARAM_NAME_2 = v,
                Config.REPLACE_STRUCTURE_TOKEN2_BODY, v -> Config.REPLACE_STRUCTURE_TOKEN2_BODY = v, null, null,
                "TOKEN-2");
        token2.add(grid2);

        injectBody.add(tools);
        injectBody.add(Box.createVerticalStrut(12));
        injectBody.add(token1);
        injectBody.add(Box.createVerticalStrut(12));
        injectBody.add(token2);
        setEnabledDeep(injectBody, Config.AUTO_REPLACE_ENABLED);
        return injectBody;
    }

    private void setAllTools(JCheckBox[] boxes, boolean on) {
        for (int i = 0; i < boxes.length; i++) {
            boxes[i].setSelected(on);
            Config.TOOLS[i].enabled = on;
        }
        say(on ? "Injecting into every Burp tool." : "Injection disabled for every tool.",
                on ? Theme.accent() : Theme.warning());
    }

    // ------------------------------------------------------------ tokens tab

    private JComponent buildTokensTab() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));

        styleTable();
        JScrollPane scroll = new JScrollPane(table);
        // JTable only installs its header on addNotify; set it up front so the
        // column titles are there from the first paint.
        scroll.setColumnHeaderView(table.getTableHeader());
        scroll.setBorder(BorderFactory.createLineBorder(Theme.line()));
        scroll.getViewport().setBackground(Theme.card());

        JPanel empty = new JPanel(new BorderLayout());
        empty.setBorder(BorderFactory.createLineBorder(Theme.line()));
        empty.setBackground(Theme.card());
        JLabel hint = new JLabel("<html><div style='text-align:center'>"
                + "<b>No tokens captured yet</b><br><br>"
                + "Log in to your application through Burp.<br>"
                + "If nothing shows up, check the RegEx on the <b>Capture</b> tab."
                + "</div></html>", SwingConstants.CENTER);
        hint.setFont(Theme.base());
        hint.setForeground(Theme.muted());
        empty.add(hint, BorderLayout.CENTER);

        tableArea.setOpaque(false);
        tableArea.add(empty, "empty");
        tableArea.add(scroll, "table");

        JPanel toolbar = Ui.row(8);
        toolbar.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));
        toolbar.add(button("Copy value", e -> copySelectedValue()));
        toolbar.add(button("Remove", e -> removeSelected()));
        toolbar.add(button("Clear all", e -> {
            int removed = TokenStore.clear();
            Log.print("[" + Log.now() + "] TOKENS CLEARED [" + removed + "]");
            say(removed + " token(s) cleared.", Theme.warning());
        }));
        toolbar.add(Ui.strut(8));
        toolbar.add(Ui.muted("Held in memory only - cleared when the extension unloads. Click a heading to sort."));

        panel.add(toolbar, BorderLayout.NORTH);
        panel.add(tableArea, BorderLayout.CENTER);
        return panel;
    }

    private void styleTable() {
        table.setFont(Theme.base());
        table.setForeground(Theme.foreground());
        table.setBackground(Theme.card());
        table.setRowHeight(Math.max(22, table.getFontMetrics(Theme.base()).getHeight() + 8));
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.setFillsViewportHeight(true);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        table.setAutoCreateRowSorter(true);
        table.setSelectionBackground(Theme.alpha(Theme.accent(), Theme.isDark() ? 70 : 40));
        table.setSelectionForeground(Theme.foreground());

        int[] widths = { 80, 140, 55, 520, 90, 80, 320 };
        for (int i = 0; i < widths.length; i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }

        DefaultTableCellRenderer renderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value, boolean isSelected, boolean hasFocus,
                                                           int row, int column) {
                Component c = super.getTableCellRendererComponent(t, value, isSelected, hasFocus, row, column);
                int modelColumn = t.convertColumnIndexToModel(column);
                setFont(modelColumn == 3 ? Theme.mono() : Theme.base());
                setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
                if (!isSelected) {
                    setBackground(row % 2 == 0 ? Theme.card() : Theme.surface());
                    setForeground(modelColumn == 0 ? Theme.accent() : Theme.foreground());
                }
                setToolTipText(value == null ? null : String.valueOf(value));
                return c;
            }
        };
        for (int i = 0; i < COLUMNS.length; i++) {
            table.getColumnModel().getColumn(i).setCellRenderer(renderer);
        }

        JTableHeader header = table.getTableHeader();
        header.setFont(Theme.small().deriveFont(Font.BOLD));
        header.setBackground(Theme.surface());
        header.setForeground(Theme.muted());
        header.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.line()));
        header.setReorderingAllowed(false);
        final TableCellRenderer defaultHeader = header.getDefaultRenderer();
        header.setDefaultRenderer((t, value, selected, focused, row, column) -> {
            Component c = defaultHeader.getTableCellRendererComponent(t, value, selected, focused, row, column);
            if (c instanceof JLabel) {
                ((JLabel) c).setHorizontalAlignment(SwingConstants.LEFT);
                ((JLabel) c).setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
            }
            c.setFont(Theme.small().deriveFont(Font.BOLD));
            c.setForeground(Theme.muted());
            c.setBackground(Theme.surface());
            return c;
        });
    }

    private void copySelectedValue() {
        int row = table.getSelectedRow();
        if (row < 0) {
            say("Select a row in the table first.", Theme.warning());
            return;
        }
        String value = String.valueOf(tableModel.getValueAt(table.convertRowIndexToModel(row), 3));
        java.awt.Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new java.awt.datatransfer.StringSelection(value), null);
        say("Value copied to clipboard (" + value.length() + " chars).", Theme.success());
    }

    private void removeSelected() {
        int row = table.getSelectedRow();
        if (row < 0) {
            say("Select a row in the table first.", Theme.warning());
            return;
        }
        int modelRow = table.convertRowIndexToModel(row);
        String name = String.valueOf(tableModel.getValueAt(modelRow, 0));
        String path = String.valueOf(tableModel.getValueAt(modelRow, 1));
        TokenStore.remove(name, path);
        say("Removed " + name + " for scope '" + path + "'.", Theme.warning());
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
            tabs.setTitleAt(TAB_TOKENS, entries.isEmpty() ? "  Tokens  " : "  Tokens (" + entries.size() + ")  ");
        }
        refreshStats();
    }

    private void refreshStats() {
        captureStat.setText(Stats.captured() + " captured");
        injectStat.setText(Stats.injected() + " injected");
    }

    // -------------------------------------------------------------- controls

    private static JPanel grid() {
        JPanel panel = new JPanel(new GridBagLayout()) {
            @Override
            public Dimension getMaximumSize() {
                return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
            }
        };
        panel.setOpaque(false);
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        return panel;
    }

    private void addSetting(JPanel grid, int row, String label, boolean checked, Consumer<Boolean> onToggle,
                            String value, Consumer<String> setter, boolean isRegex, String hint) {
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(3, 0, 3, 8);
        c.gridy = row;
        c.anchor = GridBagConstraints.WEST;

        JCheckBox box = Ui.checkBox(label, checked);
        UiTextField field = field(value, setter, isRegex, label);
        JLabel hintLabel = Ui.muted(hint);

        box.addActionListener((ActionEvent e) -> {
            onToggle.accept(box.isSelected());
            field.setEnabled(box.isSelected());
            say(label + " " + (box.isSelected() ? "on." : "off."), Theme.accent());
        });
        field.setEnabled(checked);

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
        grid.add(hintLabel, c);
    }

    private void addReplaceHeader(JPanel grid) {
        String[] titles = { "", "Match", "Replace with", "Format", "" };
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(0, 0, 2, 8);
        c.gridy = 0;
        c.anchor = GridBagConstraints.WEST;
        for (int i = 0; i < titles.length; i++) {
            c.gridx = i;
            c.weightx = (i == 1 || i == 2) ? 1 : 0;
            c.fill = GridBagConstraints.HORIZONTAL;
            grid.add(Ui.muted(titles[i]), c);
        }
    }

    /**
     * One replacement position: a control row plus a live preview row beneath
     * it showing what the request will actually end up carrying.
     */
    private void addReplace(JPanel grid, int row, String label, boolean checked, Consumer<Boolean> onToggle,
                            String matchValue, Consumer<String> matchSetter, boolean matchIsRegex,
                            String withValue, Consumer<String> withSetter,
                            String formatValue, Consumer<String> formatSetter,
                            Boolean addChecked, Consumer<Boolean> onAddToggle, String tokenName) {
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(3, 0, 1, 8);
        c.gridy = row;
        c.anchor = GridBagConstraints.WEST;

        JCheckBox box = Ui.checkBox(label, checked);
        UiTextField matchField = field(matchValue, matchSetter, matchIsRegex, label);
        matchField.hint(matchIsRegex ? "RegEx matched against the request body"
                : "the header prefix to look for, ex: Authorization: Bearer");
        UiTextField withField = withValue == null ? null : field(withValue, withSetter, false, label);
        UiTextField formatField = field(formatValue, formatSetter, false, label);
        formatField.setColumns(5);
        formatField.hint("First %s is the name, second %s is the token");
        JCheckBox addBox = addChecked == null ? null : Ui.checkBox("Add if missing", addChecked);
        if (addBox != null) {
            addBox.setToolTipText("Add this header when the request does not already carry it");
        }

        JLabel preview = Ui.muted("");
        Runnable updatePreview = () -> {
            String name = withField != null ? withField.getText() : matchField.getText();
            String token = sampleToken(tokenName);
            String composed;
            try {
                composed = String.format(formatField.getText(), name, token);
            } catch (Exception e) {
                composed = name + token;
            }
            preview.setText("sends   " + composed);
            preview.setToolTipText(composed);
        };
        updatePreview.run();
        matchField.onChange(t -> updatePreview.run());
        formatField.onChange(t -> updatePreview.run());
        if (withField != null) {
            withField.onChange(t -> updatePreview.run());
        }

        box.addActionListener((ActionEvent e) -> {
            boolean on = box.isSelected();
            onToggle.accept(on);
            matchField.setEnabled(on);
            if (withField != null) {
                withField.setEnabled(on);
            }
            formatField.setEnabled(on);
            if (addBox != null) {
                addBox.setEnabled(on);
            }
            preview.setEnabled(on);
            say(label + " " + (on ? "will be rewritten with " + tokenName + "." : "will be left alone."),
                    Theme.accent());
        });
        if (addBox != null) {
            addBox.addActionListener((ActionEvent e) -> {
                onAddToggle.accept(addBox.isSelected());
                say("'Add if missing' for " + label + " " + (addBox.isSelected() ? "on." : "off."), Theme.accent());
            });
            addBox.setEnabled(checked);
        }
        matchField.setEnabled(checked);
        if (withField != null) {
            withField.setEnabled(checked);
        }
        formatField.setEnabled(checked);
        preview.setEnabled(checked);

        c.gridx = 0;
        c.weightx = 0;
        c.fill = GridBagConstraints.NONE;
        grid.add(box, c);

        c.gridx = 1;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        grid.add(matchField, c);

        c.gridx = 2;
        if (withField != null) {
            grid.add(withField, c);
        } else {
            grid.add(Ui.muted("  -"), c);
        }

        c.gridx = 3;
        c.weightx = 0;
        grid.add(formatField, c);

        c.gridx = 4;
        c.fill = GridBagConstraints.NONE;
        grid.add(addBox == null ? Ui.muted("") : addBox, c);

        // preview line, indented under the match column
        GridBagConstraints p = new GridBagConstraints();
        p.gridy = row + 1;
        p.gridx = 1;
        p.gridwidth = 4;
        p.weightx = 1;
        p.anchor = GridBagConstraints.WEST;
        p.fill = GridBagConstraints.HORIZONTAL;
        p.insets = new Insets(0, 2, 7, 8);
        grid.add(preview, p);
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

    private UiTextField field(String value, Consumer<String> setter, boolean isRegex, String label) {
        UiTextField field = new UiTextField(value, 18);
        field.onChange(text -> {
            if (isRegex) {
                try {
                    Pattern.compile(text);
                } catch (Exception ex) {
                    field.setInvalidInput(true, ex.getMessage());
                    say(label + ": invalid RegEx, not applied - " + firstLine(ex.getMessage()), Theme.danger());
                    return;
                }
            }
            field.setInvalidInput(false, null);
            setter.accept(text);
            say(label + " set to  " + text, Theme.success());
        });
        return field;
    }

    private JButton button(String text, java.awt.event.ActionListener action) {
        JButton button = new JButton(text);
        button.setFont(Theme.base());
        button.setFocusPainted(false);
        button.addActionListener(action);
        return button;
    }

    /** A link-styled label that runs an action instead of opening a URL. */
    private JLabel clickable(String text, Runnable action) {
        JLabel label = new JLabel(text);
        label.setFont(Theme.small());
        label.setForeground(Theme.accent());
        label.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        label.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (label.isEnabled()) {
                    action.run();
                }
            }
        });
        return label;
    }

    // ------------------------------------------------------------ status bar

    /** The strip along the bottom: a coloured dot plus the last message. */
    private class StatusBar extends JPanel {
        private final JLabel message = new JLabel();
        private Color dot = Theme.success();

        StatusBar() {
            setLayout(new BorderLayout(8, 0));
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(7, 16, 7, 16));
            message.setFont(Theme.base());
            message.setForeground(Theme.foreground());

            JPanel left = Ui.row(8);
            left.add(new Dot());
            left.add(message);
            add(left, BorderLayout.WEST);
            add(Ui.link("XsessionHandler on GitHub", Config.PROJECT_LINK), BorderLayout.EAST);
        }

        void set(String text, Color color) {
            dot = color;
            message.setText(text);
            message.setForeground(Theme.foreground());
            repaint();
        }

        @Override
        public Dimension getMaximumSize() {
            return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setColor(Theme.surface());
            g2.fillRect(0, 0, getWidth(), getHeight());
            g2.setColor(Theme.line());
            g2.drawLine(0, 0, getWidth(), 0);
            g2.dispose();
        }

        /** The coloured state dot. */
        private class Dot extends JComponent {
            Dot() {
                int size = 10;
                setPreferredSize(new Dimension(size, size));
                setMaximumSize(new Dimension(size, size));
                setAlignmentY(CENTER_ALIGNMENT);
            }

            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(dot);
                g2.fillOval(0, 0, getWidth() - 1, getHeight() - 1);
                g2.dispose();
            }
        }
    }

    @Override
    public void status(String message) {
        SwingUtilities.invokeLater(() -> {
            say(message, Theme.accent());
            refreshStats();
        });
    }

    private void say(String text, Color color) {
        if (statusBar != null) {
            statusBar.set(text, color);
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
