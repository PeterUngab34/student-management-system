package com.peterungab.sms.ui;

import com.peterungab.sms.AppContext;
import com.peterungab.sms.ui.dialogs.StudentRecordDialog;
import com.peterungab.sms.ui.views.CoursesView;
import com.peterungab.sms.ui.views.DashboardView;
import com.peterungab.sms.ui.views.EnrollmentsView;
import com.peterungab.sms.ui.views.StudentsView;

import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.event.KeyEvent;
import java.util.EnumMap;
import java.util.Map;

/** The application window: sidebar navigation, page area and status bar. */
public final class MainFrame extends JFrame {

    public static final String APP_NAME = "Student Management System";
    public static final String VERSION = "1.0.0";

    private final AppContext ctx;
    private final StatusBar statusBar;
    private final Sidebar sidebar;
    private final CardLayout cards = new CardLayout();
    private final JPanel content = new JPanel(cards);
    private final Map<Page, View> views = new EnumMap<>(Page.class);
    private Page current = Page.DASHBOARD;

    public MainFrame(AppContext ctx) {
        super(APP_NAME);
        this.ctx = ctx;
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        // default to 1360x860, but never larger than the usable screen area (minus the taskbar)
        java.awt.Rectangle screen = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        setMinimumSize(new Dimension(Math.min(1100, screen.width), Math.min(680, screen.height)));
        setSize(Math.min(1360, screen.width - 40), Math.min(860, screen.height - 40));
        setLocationRelativeTo(null);
        setIconImages(AppIcon.images());

        statusBar = new StatusBar(ctx.database());
        views.put(Page.DASHBOARD, new DashboardView(ctx, this));
        views.put(Page.STUDENTS, new StudentsView(ctx, this));
        views.put(Page.COURSES, new CoursesView(ctx, this));
        views.put(Page.ENROLLMENTS, new EnrollmentsView(ctx, this));
        views.forEach((page, view) -> content.add((java.awt.Component) view, page.name()));

        sidebar = new Sidebar(this::showPage, this::toggleTheme);

        JPanel root = new JPanel(new BorderLayout());
        root.add(sidebar, BorderLayout.WEST);
        root.add(content, BorderLayout.CENTER);
        root.add(statusBar, BorderLayout.SOUTH);
        setContentPane(root);
        setJMenuBar(createMenuBar());

        showPage(Page.DASHBOARD);
    }

    public void showPage(Page page) {
        current = page;
        cards.show(content, page.name());
        sidebar.select(page);
        views.get(page).refresh();
    }

    public Page currentPage() {
        return current;
    }

    public View view(Page page) {
        return views.get(page);
    }

    public StatusBar statusBar() {
        return statusBar;
    }

    public void openStudentRecord(int studentId) {
        new StudentRecordDialog(this, ctx, studentId).setVisible(true);
    }

    public void toggleTheme() {
        Theme.toggle();
        sidebar.updateThemeButton();
        AppSettings.saveTheme(Theme.mode());
        statusBar.flash((Theme.isDark() ? "Dark" : "Light") + " theme enabled");
    }

    private JMenuBar createMenuBar() {
        JMenuBar bar = new JMenuBar();

        JMenu file = menu("File", KeyEvent.VK_F);
        file.add(item("New Record…", KeyEvent.VK_N, "ctrl N", () -> views.get(current).createNew()));
        file.add(item("Export to CSV…", KeyEvent.VK_E, "ctrl E", this::exportCurrent));
        file.add(item("Refresh", KeyEvent.VK_R, "F5", () -> views.get(current).refresh()));
        file.addSeparator();
        file.add(item("Exit", KeyEvent.VK_X, "ctrl Q", this::dispose));
        bar.add(file);

        JMenu view = menu("View", KeyEvent.VK_V);
        for (Page page : Page.values()) {
            view.add(item(page.title(), KeyEvent.VK_1 + page.ordinal(), page.shortcut(), () -> showPage(page)));
        }
        view.addSeparator();
        view.add(item("Find", KeyEvent.VK_F, "ctrl F", () -> views.get(current).focusSearch()));
        view.add(item("Toggle Dark Mode", KeyEvent.VK_T, "ctrl T", this::toggleTheme));
        bar.add(view);

        JMenu help = menu("Help", KeyEvent.VK_H);
        help.add(item("Keyboard Shortcuts", KeyEvent.VK_K, "F1", this::showShortcuts));
        help.add(item("About", KeyEvent.VK_A, null, this::showAbout));
        bar.add(help);
        return bar;
    }

    private void exportCurrent() {
        View v = views.get(current);
        if (v.supportsExport()) {
            v.exportCsv();
        } else {
            showPage(Page.STUDENTS);
            views.get(Page.STUDENTS).exportCsv();
        }
    }

    private void showShortcuts() {
        String rows = String.join("", new String[]{
                row("Ctrl+1 … Ctrl+4", "Switch page"),
                row("Ctrl+N", "Add a record on the current page"),
                row("Ctrl+F", "Focus the search box"),
                row("Enter / double-click", "Edit the selected row"),
                row("Delete", "Delete the selected row"),
                row("Ctrl+R", "Open the student's academic record"),
                row("Ctrl+G", "Record a grade (Enrollments page)"),
                row("Ctrl+E", "Export the table to CSV"),
                row("F5", "Refresh"),
                row("Ctrl+T", "Toggle dark / light theme"),
                row("Esc", "Close a dialog")});
        JOptionPane.showMessageDialog(this,
                "<html><table cellpadding='3'>" + rows + "</table></html>",
                "Keyboard Shortcuts", JOptionPane.PLAIN_MESSAGE);
    }

    private static String row(String keys, String action) {
        return "<tr><td><b>" + keys + "</b></td><td>&nbsp;&nbsp;" + action + "</td></tr>";
    }

    private void showAbout() {
        JOptionPane.showMessageDialog(this, "<html><b style='font-size:120%'>" + APP_NAME + "</b> v" + VERSION
                        + "<br><br>Desktop app for student records, courses, enrollments and grades."
                        + "<br>Java 17 · Swing + FlatLaf · JDBC · MySQL / H2"
                        + "<br><br>Database: " + ctx.database().type().displayName() + " — " + ctx.database().location()
                        + "<br><br>Built by Peter Paul Ungab · github.com/PeterUngab34</html>",
                "About", JOptionPane.INFORMATION_MESSAGE);
    }

    private static JMenu menu(String text, int mnemonic) {
        JMenu menu = new JMenu(text);
        menu.setMnemonic(mnemonic);
        return menu;
    }

    private static JMenuItem item(String text, int mnemonic, String accelerator, Runnable action) {
        JMenuItem item = new JMenuItem(text);
        item.setMnemonic(mnemonic);
        if (accelerator != null) {
            item.setAccelerator(KeyStroke.getKeyStroke(accelerator));
        }
        item.addActionListener(e -> action.run());
        return item;
    }
}
