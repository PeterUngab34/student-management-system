package com.peterungab.sms;

import com.peterungab.sms.db.Database;
import com.peterungab.sms.db.DatabaseConfig;
import com.peterungab.sms.db.DatabaseInitializer;
import com.peterungab.sms.model.Enrollment;
import com.peterungab.sms.model.Student;
import com.peterungab.sms.ui.AppSettings;
import com.peterungab.sms.ui.MainFrame;
import com.peterungab.sms.ui.Page;
import com.peterungab.sms.ui.Theme;
import com.peterungab.sms.ui.dialogs.CourseDialog;
import com.peterungab.sms.ui.dialogs.EnrollDialog;
import com.peterungab.sms.ui.dialogs.GradeDialog;
import com.peterungab.sms.ui.dialogs.StudentDialog;
import com.peterungab.sms.ui.dialogs.StudentRecordDialog;

import javax.swing.JOptionPane;
import javax.swing.RootPaneContainer;
import javax.swing.SwingUtilities;
import java.awt.Graphics2D;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Entry point.
 * <pre>
 *   java -jar student-management-system.jar               start the app
 *   java -jar student-management-system.jar --smoke-test  initialize the DB, build every page offscreen, exit 0/1
 * </pre>
 */
public final class App {

    private App() {
    }

    public static void main(String[] args) {
        List<String> options = Arrays.asList(args);
        boolean smokeTest = options.contains("--smoke-test");

        Theme.installFonts();
        AppContext ctx;
        try {
            ctx = bootstrap();
        } catch (Exception e) {
            e.printStackTrace();
            if (!smokeTest) {
                Theme.apply(Theme.Mode.DARK);
                JOptionPane.showMessageDialog(null, "Could not open the database:\n" + e.getMessage(),
                        MainFrame.APP_NAME, JOptionPane.ERROR_MESSAGE);
            }
            System.exit(1);
            return;
        }

        if (smokeTest) {
            System.exit(runSmokeTest(ctx) ? 0 : 1);
        }
        SwingUtilities.invokeLater(() -> {
            Theme.apply(AppSettings.loadTheme());
            new MainFrame(ctx).setVisible(true);
        });
    }

    /** Picks MySQL or H2, creates the schema + sample data on first run and wires the services. */
    public static AppContext bootstrap() throws Exception {
        Database db = Database.connect(DatabaseConfig.load());
        boolean created = DatabaseInitializer.initializeIfNeeded(db, true);
        System.out.println("Database: " + db.type().displayName() + " @ " + db.location()
                + (created ? " (schema + sample data created)" : ""));
        return AppContext.create(db);
    }

    /** Builds the real main window (without showing it), visits and paints every page in both themes. */
    private static boolean runSmokeTest(AppContext ctx) {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> failure.compareAndSet(null, e));
        try {
            SwingUtilities.invokeAndWait(() -> {
                Theme.apply(Theme.Mode.DARK);
                MainFrame frame = new MainFrame(ctx);
                frame.pack();
                frame.setSize(1360, 860);
                for (int pass = 0; pass < 2; pass++) {
                    if (pass == 1) {
                        Theme.toggle(); // live switch to the light theme, like the Ctrl+T shortcut
                    }
                    for (Page page : Page.values()) {
                        frame.showPage(page);
                        paintOffscreen(frame);
                    }
                    // construct every dialog once (not shown) to exercise data loading and layout
                    Student anyStudent = ctx.students().findAll().get(0);
                    Enrollment anyEnrollment = ctx.enrollments().search(null).get(0);
                    for (Window dialog : List.of(
                            new StudentDialog(frame, ctx, null),
                            new StudentDialog(frame, ctx, anyStudent),
                            new CourseDialog(frame, ctx, null),
                            new EnrollDialog(frame, ctx),
                            new GradeDialog(frame, ctx, anyEnrollment),
                            new StudentRecordDialog(frame, ctx, anyStudent.id()))) {
                        paintOffscreen(dialog);
                        dialog.dispose();
                    }
                }
                frame.dispose();
            });
        } catch (Exception e) {
            failure.compareAndSet(null, e.getCause() != null ? e.getCause() : e);
        }
        if (failure.get() != null) {
            System.err.println("SMOKE TEST FAILED");
            failure.get().printStackTrace();
            return false;
        }
        System.out.println("SMOKE TEST OK: " + ctx.database().type().displayName()
                + ", students=" + ctx.students().count()
                + ", courses=" + ctx.courses().count()
                + ", enrollments=" + ctx.enrollments().count()
                + ", pages=" + Page.values().length + " + 6 dialogs rendered in dark and light theme");
        return true;
    }

    private static void paintOffscreen(Window window) {
        window.validate();
        BufferedImage img = new BufferedImage(Math.max(1, window.getWidth()), Math.max(1, window.getHeight()),
                BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        ((RootPaneContainer) window).getRootPane().paint(g);
        g.dispose();
    }
}
