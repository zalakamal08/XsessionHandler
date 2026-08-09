package burp;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;

import burp.ui.Block;
import burp.ui.Meter;
import burp.ui.Rail;
import burp.ui.RuleGrid;
import burp.ui.Theme;
import burp.ui.Ui;
import burp.ui.UiTextField;

/**
 * The XsessionHandler tab.
 *
 * The tab is a console, not a settings dialog. A permanent header carries the
 * two master switches and their live counters, a rail down the left names the
 * three places you can be, and the content pane shows one of them at a time.
 * A status line along the bottom narrates whatever just happened.
 */
public class MyTab implements ITab, IExtensionStateListener, MyHttpListener.StatusSink, TokenStore.Listener {

    private static final String[] COLUMNS = { "Token", "Scope", "Len", "Value", "Captured", "Tool", "Source URL" };
    private static final String NAV_CAPTURE = "capture";
    private static final String NAV_INJECT = "inject";
    private static final String NAV_TOKENS = "tokens";

    private final IBurpExtenderCallbacks callbacks;
    private final MyHttpListener httpListener;

    private JPanel root;
    private Rail rail;
    private StatusBar statusBar;

    private final CardLayout screens = new CardLayout();
    private final JPanel screenArea = new JPanel(screens);

    private final JPanel captureBody = Ui.column();
    private final JPanel injectBody = Ui.column();

    /**
     * Re-applies each rule's own checkbox state to its fields. A master switch
     * enables the whole tab in one sweep, which would otherwise wake up fields
     * belonging to rules the user had deliberately left off.
     */
    private final List<Runnable> captureSync = new ArrayList<Runnable>();
    private final List<Runnable> injectSync = new ArrayList<Runnable>();

    private Meter captureMeter;
    private Meter injectMeter;

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
        say("Ready. Log in through Burp once and every following request carries the token.", Theme.ok());
    }

    // ----------------------------------------------------------------- build

    private void build() {
        screenArea.setOpaque(false);
        screenArea.add(scrollable(buildCaptureScreen()), NAV_CAPTURE);
        screenArea.add(scrollable(buildInjectScreen()), NAV_INJECT);
        screenArea.add(buildTokensScreen(), NAV_TOKENS);

        rail = new Rail(key -> screens.show(screenArea, key));
        rail.item(NAV_CAPTURE, "Capture", Rail.CAPTURE);
        rail.item(NAV_INJECT, "Inject", Rail.INJECT);
        rail.item(NAV_TOKENS, "Tokens", Rail.TOKENS);
        rail.select(NAV_CAPTURE);

        JPanel middle = new JPanel(new BorderLayout());
        middle.setOpaque(false);
        middle.add(rail, BorderLayout.WEST);
        middle.add(screenArea, BorderLayout.CENTER);

        statusBar = new StatusBar();

        root = new JPanel(new BorderLayout());
        root.setBackground(Theme.canvas());
        root.add(buildHeader(), BorderLayout.NORTH);
        root.add(middle, BorderLayout.CENTER);
        root.add(statusBar, BorderLayout.SOUTH);
    }

    private JScrollPane scrollable(JComponent content) {
        JPanel padded = new JPanel(new BorderLayout());
        padded.setOpaque(false);
        padded.setBorder(BorderFactory.createEmptyBorder(Theme.SPACE_XL, Theme.SPACE_XL, Theme.SPACE_XL,
                Theme.SPACE_XL));
        padded.add(content, BorderLayout.NORTH);

        JScrollPane scroll = new JScrollPane(padded);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setOpaque(false);
        scroll.getViewport().setBackground(Theme.canvas());
        scroll.getVerticalScrollBar().setUnitIncrement(18);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        return scroll;
    }

    /** The title of whichever screen the rail is pointing at. */
    private JComponent screenTitle(String title, String description) {
        JPanel column = Ui.column();
        column.setBorder(BorderFactory.createEmptyBorder(0, 0, Theme.SPACE_XL, 0));
        column.add(Ui.text(title, Theme.h1(), Theme.text()));
        column.add(Box.createVerticalStrut(Theme.SPACE_XS));
        column.add(Ui.text(description, Theme.body(), Theme.dim()));
        return column;
    }

    // ---------------------------------------------------------------- header

    private JComponent buildHeader() {
        JPanel header = new JPanel(new BorderLayout(Theme.SPACE_XL, 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setColor(Theme.surface());
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.setColor(Theme.lineStrong());
                g2.drawLine(0, getHeight() - 1, getWidth(), getHeight() - 1);
                g2.dispose();
            }
        };
        header.setBorder(BorderFactory.createEmptyBorder(Theme.SPACE_MD, Theme.SPACE_XL, Theme.SPACE_MD,
                Theme.SPACE_XL));

        JPanel brand = Ui.column();
        JPanel wordmarkRow = Ui.row(Theme.SPACE_SM);
        wordmarkRow.add(Ui.text("XsessionHandler", Theme.display(), Theme.text()));
        wordmarkRow.add(new Ui.Chip(Config.EXT_VERSION, Theme.accent()));
        brand.add(wordmarkRow);

        JPanel taglineRow = Ui.row(Theme.SPACE_SM);
        taglineRow.add(Ui.dim("Token capture and injection, straight from the HTTP listener"));
        taglineRow.add(Ui.dim("·"));
        taglineRow.add(Ui.dim("Built by"));
        taglineRow.add(Ui.link("@" + Config.AUTHOR_HANDLE, Config.AUTHOR_LINK));
        brand.add(Box.createVerticalStrut(Theme.SPACE_XS));
        brand.add(taglineRow);

        JPanel meters = new JPanel();
        meters.setOpaque(false);
        meters.setLayout(new BoxLayout(meters, BoxLayout.X_AXIS));

        captureMeter = new Meter("Capture", "captured", Config.CAPTURE_ENABLED, e -> {
            boolean on = captureMeter.isOn();
            Config.CAPTURE_ENABLED = on;
            captureMeter.setState(on);
            setEnabledDeep(captureBody, on);
            if (on) {
                for (Runnable sync : captureSync) {
                    sync.run();
                }
            }
            say("Capture " + (on ? "enabled - responses are being read." : "paused - no tokens will be read."),
                    on ? Theme.ok() : Theme.warn());
        });
        captureMeter.toggle().setToolTipText("Read tokens out of responses");

        injectMeter = new Meter("Inject", "injected", Config.AUTO_REPLACE_ENABLED, e -> {
            boolean on = injectMeter.isOn();
            Config.AUTO_REPLACE_ENABLED = on;
            injectMeter.setState(on);
            setEnabledDeep(injectBody, on);
            if (on) {
                for (Runnable sync : injectSync) {
                    sync.run();
                }
            }
            say("Inject " + (on ? "enabled - outgoing requests are rewritten." : "paused - requests go out as-is."),
                    on ? Theme.ok() : Theme.warn());
        });
        injectMeter.toggle().setToolTipText("Rewrite outgoing requests with the tokens");

        meters.add(captureMeter);
        meters.add(Box.createHorizontalStrut(Theme.SPACE_MD));
        meters.add(injectMeter);

        JPanel metersHolder = new JPanel(new BorderLayout());
        metersHolder.setOpaque(false);
        metersHolder.add(meters, BorderLayout.SOUTH);

        header.add(brand, BorderLayout.WEST);
        header.add(metersHolder, BorderLayout.EAST);
        return header;
    }

    // -------------------------------------------------------- capture screen

    private JComponent buildCaptureScreen() {
        captureBody.add(screenTitle("Capture", "Where tokens come from - which responses to read, "
                + "and what to pull out of them."));

        Block filters = new Block("01", "Which responses to read",
                "A response is only inspected when every ticked check passes.");
        RuleGrid filterGrid = new RuleGrid(new String[] { "", "Pattern", "Notes" }, new float[] { 0, 1f, 0 });
        filterGrid.row(setting(captureSync, filterGrid, "Request URL matches", Config.CHECK_REQ_URL_REGEX,
                v -> Config.CHECK_REQ_URL_REGEX = v, Config.VALUE_REQ_URL_REGEX,
                v -> Config.VALUE_REQ_URL_REGEX = v, true, "RegEx, tested against the whole URL"));
        filterGrid.row(setting(captureSync, filterGrid, "Response header contains", Config.CHECK_RESP_CONTENT_TYPE,
                v -> Config.CHECK_RESP_CONTENT_TYPE = v, Config.VALUE_RESP_CONTENT_TYPE_HEADER,
                v -> Config.VALUE_RESP_CONTENT_TYPE_HEADER = v, false, "plain text, not a RegEx"));
        filters.add(filterGrid);

        Block extract = new Block("02", "What to pull out",
                "Group 1 of each RegEx is stored as the token. Leave one unticked if your app has no such token.");
        RuleGrid extractGrid = new RuleGrid(new String[] { "", "Pattern", "Notes" }, new float[] { 0, 1f, 0 });
        extractGrid.row(setting(captureSync, extractGrid, "TOKEN-1", Config.LISTEN_4_ACCESS_TOKEN,
                v -> Config.LISTEN_4_ACCESS_TOKEN = v, Config.VALUE_READ_JWT_REGEX,
                v -> Config.VALUE_READ_JWT_REGEX = v, true, "JWT / access token"));
        extractGrid.row(setting(captureSync, extractGrid, "TOKEN-2", Config.LISTEN_4_REFRESH_TOKEN,
                v -> Config.LISTEN_4_REFRESH_TOKEN = v, Config.VALUE_READ_REFRESH_TOKEN_REGEX,
                v -> Config.VALUE_READ_REFRESH_TOKEN_REGEX = v, true, "refresh / CSRF token"));
        extract.add(extractGrid);

        Block scope = new Block("03", "Testing more than one app at once?",
                "Optional. Skip this if you are working on a single application.");
        scope.headerControl(new Ui.Chip("Optional", Theme.info()));
        scope.add(note("Two apps that both return <code>access_token</code> would otherwise share one slot and keep "
                + "overwriting each other. To keep them apart, add a header to each <b>login</b> request naming the "
                + "path that token belongs to - for example <code>Shp-Path: /appA/</code> - and the token is filed "
                + "under <code>/appA/</code> instead of <code>/</code>. Requests to <code>/appA/...</code> then get "
                + "that token back. You add the header yourself, with a Proxy match/replace rule or by hand."));
        scope.gap(Theme.SPACE_MD);
        RuleGrid scopeGrid = new RuleGrid(new String[] { "", "Pattern", "Notes" }, new float[] { 0, 1f, 0 });
        scopeGrid.row(setting(captureSync, scopeGrid, "Read the scope from this request header", Config.CHECK_REQ_PATH,
                v -> Config.CHECK_REQ_PATH = v, Config.VALUE_REQ_PATH_HEADER,
                v -> Config.VALUE_REQ_PATH_HEADER = v, true, "group 1 becomes the scope"));
        scope.add(scopeGrid);

        Block debug = new Block("04", "Not seeing your token?",
                "Turn this on to find out which check is rejecting it.");
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
        captureBody.add(extract);
        captureBody.add(scope);
        captureBody.add(debug);
        setEnabledDeep(captureBody, Config.CAPTURE_ENABLED);
        return captureBody;
    }

    // --------------------------------------------------------- inject screen

    private JComponent buildInjectScreen() {
        injectBody.add(screenTitle("Inject", "Where tokens go - which requests get rewritten, "
                + "and in which position."));

        Block tools = new Block("01", "Which Burp tools to rewrite",
                "Extender is off by default so your own token-fetch traffic is not rewritten with a stale value.");
        final JCheckBox[] boxes = new JCheckBox[Config.TOOLS.length];
        JPanel toolRow = Ui.row(Theme.SPACE_LG);
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
        tools.headerControl(Ui.clickable("Select all", () -> setAllTools(boxes, true), null));
        tools.headerControl(Ui.dim("/"));
        tools.headerControl(Ui.clickable("Select none", () -> setAllTools(boxes, false), null));
        tools.add(toolRow);

        String[] captions = { "Position", "Match", "Replace with", "Format", "", "Sends" };
        float[] weights = { 0, 1f, 0.8f, 0, 0, 1.3f };

        Block token1 = new Block("02", "TOKEN-1 goes here",
                "JWT / access token. Tick every position your app uses.");
        RuleGrid grid1 = new RuleGrid(captions, weights);
        grid1.row(replace("Request header", Config.CHECK_TOKEN1_HEADER_1, v -> Config.CHECK_TOKEN1_HEADER_1 = v,
                Config.VAL_JWT_HEADER_NAME_1, v -> Config.VAL_JWT_HEADER_NAME_1 = v, false, null, null,
                Config.REPLACE_STRUCTURE_TOKEN1_HEAD_1, v -> Config.REPLACE_STRUCTURE_TOKEN1_HEAD_1 = v,
                Config.CHECK_TOKEN1_HEADER_ADD_IF_NOT_EXIST_1,
                v -> Config.CHECK_TOKEN1_HEADER_ADD_IF_NOT_EXIST_1 = v, "TOKEN-1"));
        grid1.row(replace("A second header", Config.CHECK_TOKEN1_HEADER_2, v -> Config.CHECK_TOKEN1_HEADER_2 = v,
                Config.VAL_JWT_HEADER_NAME_2, v -> Config.VAL_JWT_HEADER_NAME_2 = v, false, null, null,
                Config.REPLACE_STRUCTURE_TOKEN1_HEAD_2, v -> Config.REPLACE_STRUCTURE_TOKEN1_HEAD_2 = v,
                Config.CHECK_TOKEN1_HEADER_ADD_IF_NOT_EXIST_2,
                v -> Config.CHECK_TOKEN1_HEADER_ADD_IF_NOT_EXIST_2 = v, "TOKEN-1"));
        grid1.row(replace("A body parameter", Config.CHECK_TOKEN1_BODY, v -> Config.CHECK_TOKEN1_BODY = v,
                Config.VAL_JWT_BODY_NAME_1, v -> Config.VAL_JWT_BODY_NAME_1 = v, true,
                Config.VAL_JWT_BODY_NAME_2, v -> Config.VAL_JWT_BODY_NAME_2 = v,
                Config.REPLACE_STRUCTURE_TOKEN1_BODY, v -> Config.REPLACE_STRUCTURE_TOKEN1_BODY = v, null, null,
                "TOKEN-1"));
        token1.add(grid1);

        Block token2 = new Block("03", "TOKEN-2 goes here", "Refresh / CSRF token.");
        RuleGrid grid2 = new RuleGrid(captions, weights);
        grid2.row(replace("Request header", Config.CHECK_TOKEN2_HEADER, v -> Config.CHECK_TOKEN2_HEADER = v,
                Config.VAL_TOKEN2_HDR_PARAM_NAME_1, v -> Config.VAL_TOKEN2_HDR_PARAM_NAME_1 = v, false, null, null,
                Config.REPLACE_STRUCTURE_TOKEN2_HEAD, v -> Config.REPLACE_STRUCTURE_TOKEN2_HEAD = v,
                Config.CHECK_TOKEN2_HEADER_ADD_IF_NOT_EXIST, v -> Config.CHECK_TOKEN2_HEADER_ADD_IF_NOT_EXIST = v,
                "TOKEN-2"));
        grid2.row(replace("A body parameter", Config.CHECK_TOKEN2_BODY, v -> Config.CHECK_TOKEN2_BODY = v,
                Config.VAL_TOKEN2_BODY_PARAM_NAME_1, v -> Config.VAL_TOKEN2_BODY_PARAM_NAME_1 = v, true,
                Config.VAL_TOKEN2_BODY_PARAM_NAME_2, v -> Config.VAL_TOKEN2_BODY_PARAM_NAME_2 = v,
                Config.REPLACE_STRUCTURE_TOKEN2_BODY, v -> Config.REPLACE_STRUCTURE_TOKEN2_BODY = v, null, null,
                "TOKEN-2"));
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
        say(on ? "Injecting into every Burp tool." : "Injection disabled for every tool.",
                on ? Theme.accent() : Theme.warn());
    }

    // --------------------------------------------------------- tokens screen

    private JComponent buildTokensScreen() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createEmptyBorder(Theme.SPACE_XL, Theme.SPACE_XL, Theme.SPACE_XL,
                Theme.SPACE_XL));

        JPanel head = new JPanel(new BorderLayout());
        head.setOpaque(false);
        head.add(screenTitle("Tokens", "Everything captured so far."), BorderLayout.NORTH);

        JPanel toolbar = Ui.row(Theme.SPACE_SM);
        toolbar.setBorder(BorderFactory.createEmptyBorder(0, 0, Theme.SPACE_MD, 0));
        toolbar.add(Ui.primaryButton("Copy value", e -> copySelectedValue()));
        toolbar.add(Ui.ghostButton("Remove", e -> removeSelected()));
        toolbar.add(Ui.ghostButton("Clear all", e -> {
            int removed = TokenStore.clear();
            Log.print("[" + Log.now() + "] TOKENS CLEARED [" + removed + "]");
            say(removed + " token(s) cleared.", Theme.warn());
        }));
        toolbar.add(Ui.strut(Theme.SPACE_SM));
        toolbar.add(Ui.dim("Held in memory only - cleared when the extension unloads. Click a heading to sort."));
        head.add(toolbar, BorderLayout.SOUTH);

        styleTable();
        JScrollPane scroll = new JScrollPane(table);
        // JTable only installs its header on addNotify; set it up front so the
        // column titles are there from the first paint.
        scroll.setColumnHeaderView(table.getTableHeader());
        scroll.setBorder(BorderFactory.createLineBorder(Theme.line()));
        scroll.getViewport().setBackground(Theme.surface());

        tableArea.setOpaque(false);
        tableArea.add(new EmptyState(), "empty");
        tableArea.add(scroll, "table");

        panel.add(head, BorderLayout.NORTH);
        panel.add(tableArea, BorderLayout.CENTER);
        return panel;
    }

    /** What the Tokens screen shows before anything has been captured. */
    private static class EmptyState extends JPanel {
        EmptyState() {
            setLayout(new BorderLayout());
            setOpaque(false);
            setBorder(BorderFactory.createLineBorder(Theme.line()));

            JPanel middle = Ui.column();
            middle.setBorder(BorderFactory.createEmptyBorder(Theme.SPACE_XXL, 0, Theme.SPACE_XXL, 0));

            JComponent icon = new JComponent() {
                @Override
                public Dimension getPreferredSize() {
                    return new Dimension(56, 56);
                }

                @Override
                public Dimension getMaximumSize() {
                    return getPreferredSize();
                }

                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(Theme.accentSoft());
                    g2.fillRoundRect(0, 0, 56, 56, Theme.RADIUS_LG, Theme.RADIUS_LG);
                    Graphics2D inner = (Graphics2D) g2.create(16, 16, 28, 28);
                    inner.setColor(Theme.accent());
                    inner.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    Rail.TOKENS.paint(inner, 24);
                    inner.dispose();
                    g2.dispose();
                }
            };
            icon.setAlignmentX(Component.CENTER_ALIGNMENT);

            JLabel heading = Ui.text("No tokens captured yet", Theme.h2(), Theme.text());
            heading.setAlignmentX(Component.CENTER_ALIGNMENT);

            // Two plain labels rather than one HTML block: Swing mislays an
            // emphasised run inside a centred <div>, and this needs no measure.
            JLabel line1 = Ui.text("Log in to your application through Burp.", Theme.body(), Theme.dim());
            JLabel line2 = Ui.text("If nothing shows up, check the RegEx on the Capture screen.",
                    Theme.body(), Theme.dim());
            line1.setAlignmentX(Component.CENTER_ALIGNMENT);
            line2.setAlignmentX(Component.CENTER_ALIGNMENT);

            middle.add(icon);
            middle.add(Box.createVerticalStrut(Theme.SPACE_LG));
            middle.add(heading);
            middle.add(Box.createVerticalStrut(Theme.SPACE_SM));
            middle.add(line1);
            middle.add(Box.createVerticalStrut(Theme.SPACE_XS));
            middle.add(line2);

            JPanel centre = new JPanel();
            centre.setOpaque(false);
            centre.setLayout(new BoxLayout(centre, BoxLayout.X_AXIS));
            centre.add(Box.createHorizontalGlue());
            centre.add(middle);
            centre.add(Box.createHorizontalGlue());
            add(centre, BorderLayout.CENTER);
        }

        @Override
        protected void paintComponent(Graphics g) {
            g.setColor(Theme.surface());
            g.fillRect(0, 0, getWidth(), getHeight());
        }
    }

    private void styleTable() {
        table.setFont(Theme.body());
        table.setForeground(Theme.text());
        table.setBackground(Theme.surface());
        table.setRowHeight(Math.max(24, table.getFontMetrics(Theme.body()).getHeight() + 10));
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.setFillsViewportHeight(true);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        table.setAutoCreateRowSorter(true);
        table.setSelectionBackground(Theme.accentSoft());
        table.setSelectionForeground(Theme.text());

        int[] widths = { 96, 140, 60, 520, 110, 100, 340 };
        for (int i = 0; i < widths.length; i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }

        DefaultTableCellRenderer renderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value, boolean isSelected, boolean hasFocus,
                                                           int row, int column) {
                Component c = super.getTableCellRendererComponent(t, value, isSelected, hasFocus, row, column);
                int modelColumn = t.convertColumnIndexToModel(column);
                boolean machineText = modelColumn == 3 || modelColumn == 4 || modelColumn == 6;
                setFont(modelColumn == 0 ? Theme.strong() : machineText ? Theme.mono() : Theme.body());
                setHorizontalAlignment(modelColumn == 2 ? SwingConstants.RIGHT : SwingConstants.LEFT);
                setBorder(BorderFactory.createEmptyBorder(0, Theme.SPACE_MD, 0, Theme.SPACE_MD));
                if (!isSelected) {
                    setBackground(row % 2 == 0 ? Theme.surface() : Theme.raised());
                    setForeground(modelColumn == 0 ? Theme.accent()
                            : modelColumn == 2 ? Theme.faint() : Theme.text());
                }
                setToolTipText(value == null ? null : String.valueOf(value));
                return c;
            }
        };
        for (int i = 0; i < COLUMNS.length; i++) {
            table.getColumnModel().getColumn(i).setCellRenderer(renderer);
        }

        JTableHeader header = table.getTableHeader();
        header.setBackground(Theme.raised());
        header.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.lineStrong()));
        header.setReorderingAllowed(false);
        header.setDefaultRenderer((t, value, selected, focused, row, column) -> {
            JLabel cell = new JLabel(String.valueOf(value).toUpperCase());
            cell.setOpaque(true);
            cell.setFont(Theme.caption());
            cell.setForeground(Theme.faint());
            cell.setBackground(Theme.raised());
            cell.setHorizontalAlignment(t.convertColumnIndexToModel(column) == 2
                    ? SwingConstants.RIGHT : SwingConstants.LEFT);
            cell.setBorder(BorderFactory.createEmptyBorder(7, Theme.SPACE_MD, 7, Theme.SPACE_MD));
            return cell;
        });
    }

    private void copySelectedValue() {
        int row = table.getSelectedRow();
        if (row < 0) {
            say("Select a row in the table first.", Theme.warn());
            return;
        }
        String value = String.valueOf(tableModel.getValueAt(table.convertRowIndexToModel(row), 3));
        java.awt.Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new java.awt.datatransfer.StringSelection(value), null);
        say("Value copied to clipboard (" + value.length() + " chars).", Theme.ok());
    }

    private void removeSelected() {
        int row = table.getSelectedRow();
        if (row < 0) {
            say("Select a row in the table first.", Theme.warn());
            return;
        }
        int modelRow = table.convertRowIndexToModel(row);
        String name = String.valueOf(tableModel.getValueAt(modelRow, 0));
        String path = String.valueOf(tableModel.getValueAt(modelRow, 1));
        TokenStore.remove(name, path);
        say("Removed " + name + " for scope '" + path + "'.", Theme.warn());
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
        if (rail != null) {
            rail.badge(NAV_TOKENS, entries.size());
        }
        refreshStats();
    }

    private void refreshStats() {
        if (captureMeter != null) {
            captureMeter.setCount(Stats.captured());
        }
        if (injectMeter != null) {
            injectMeter.setCount(Stats.injected());
        }
    }

    // -------------------------------------------------------------- controls

    /** A paragraph of explanation inside a block, wrapped to a readable measure. */
    private static JComponent note(String html) {
        JLabel label = new JLabel("<html><body style='width:660px'>" + html + "</body></html>");
        label.setFont(Theme.small());
        label.setForeground(Theme.dim());
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    /** One checkbox-plus-pattern rule, as the cells of a RuleGrid line. */
    private Component[] setting(List<Runnable> syncs, JComponent owner, String label, boolean checked,
                                Consumer<Boolean> onToggle, String value, Consumer<String> setter, boolean isRegex,
                                String hint) {
        JCheckBox box = Ui.checkBox(label, checked);
        UiTextField field = field(value, setter, isRegex, label);
        field.hint(isRegex ? "RegEx - group 1 is what gets stored" : "Plain text, matched anywhere in the headers");

        box.addActionListener((ActionEvent e) -> {
            onToggle.accept(box.isSelected());
            field.setEnabled(box.isSelected());
            owner.repaint();
            say(label + " " + (box.isSelected() ? "on." : "off."), Theme.accent());
        });
        field.setEnabled(checked);
        syncs.add(() -> field.setEnabled(box.isSelected()));

        return new Component[] { box, field, Ui.dim(hint) };
    }

    /**
     * One injection position, as the cells of a RuleGrid line. The last cell is
     * a live preview of exactly what will be put on the wire, composed from the
     * row's own inputs and a real captured token when one exists - so the
     * format string can be checked without sending a request.
     */
    private Component[] replace(String label, boolean checked, Consumer<Boolean> onToggle,
                                String matchValue, Consumer<String> matchSetter, boolean matchIsRegex,
                                String withValue, Consumer<String> withSetter,
                                String formatValue, Consumer<String> formatSetter,
                                Boolean addChecked, Consumer<Boolean> onAddToggle, String tokenName) {
        JCheckBox box = Ui.checkBox(label, checked);
        UiTextField matchField = field(matchValue, matchSetter, matchIsRegex, label);
        matchField.hint(matchIsRegex ? "RegEx matched against the request body"
                : "The header prefix to look for, ex: Authorization: Bearer");
        UiTextField withField = withValue == null ? null : field(withValue, withSetter, false, label);
        if (withField != null) {
            withField.hint("The literal text put back in front of the token");
        }
        UiTextField formatField = field(formatValue, formatSetter, false, label);
        formatField.setColumns(5);
        formatField.hint("First %s is the name, second %s is the token");

        JCheckBox addBox = addChecked == null ? null : Ui.checkBox("Add if missing", addChecked);
        if (addBox != null) {
            addBox.setToolTipText("Add this header when the request does not already carry it");
        }

        Ui.Ellipsis preview = new Ui.Ellipsis(Theme.mono(), Theme.accent());
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
        matchField.onChange(t -> updatePreview.run());
        formatField.onChange(t -> updatePreview.run());
        if (withField != null) {
            withField.onChange(t -> updatePreview.run());
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
            say(label + " " + (on ? "will be rewritten with " + tokenName + "." : "will be left alone."),
                    Theme.accent());
        });
        if (addBox != null) {
            addBox.addActionListener((ActionEvent e) -> {
                onAddToggle.accept(addBox.isSelected());
                say("'Add if missing' for " + label + " " + (addBox.isSelected() ? "on." : "off."), Theme.accent());
            });
        }
        sync.run();

        return new Component[] { box, matchField, withField, formatField, addBox, preview };
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
            say(label + " set to  " + text, Theme.ok());
        });
        return field;
    }

    // ------------------------------------------------------------ status bar

    /** The strip along the bottom: a state dot, the last message, the project link. */
    private class StatusBar extends JPanel {
        private final JLabel message = new JLabel();
        private Color dot = Theme.ok();

        StatusBar() {
            setLayout(new BorderLayout(Theme.SPACE_SM, 0));
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(Theme.SPACE_SM, Theme.SPACE_XL, Theme.SPACE_SM,
                    Theme.SPACE_XL));
            message.setFont(Theme.small());
            message.setForeground(Theme.dim());

            JPanel left = Ui.row(Theme.SPACE_SM);
            left.add(new Dot());
            left.add(message);
            add(left, BorderLayout.WEST);
            add(Ui.link("XsessionHandler on GitHub", Config.PROJECT_LINK), BorderLayout.EAST);
        }

        void set(String text, Color color) {
            dot = color;
            message.setText(text);
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
            g2.setColor(Theme.lineStrong());
            g2.drawLine(0, 0, getWidth(), 0);
            g2.dispose();
        }

        /** The state dot: one glance tells you informational from error. */
        private class Dot extends JComponent {
            Dot() {
                setPreferredSize(new Dimension(8, 8));
                setMaximumSize(new Dimension(8, 8));
                setAlignmentY(CENTER_ALIGNMENT);
            }

            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Theme.alpha(dot, 70));
                g2.fillOval(-2, -2, getWidth() + 4, getHeight() + 4);
                g2.setColor(dot);
                g2.fillOval(0, 0, getWidth(), getHeight());
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
