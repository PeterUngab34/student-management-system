package com.peterungab.sms.ui;

import com.peterungab.sms.AppContext;
import com.peterungab.sms.TestDatabase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.JDialog;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static com.peterungab.sms.ui.Swing.onEdt;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * Base class for the in-process UI tests. Every test gets a fresh seeded in-memory H2 database,
 * the real {@link MainFrame} (laid out but never shown, so it neither steals focus nor needs it)
 * and a {@link RecordingPrompts} installed in place of the modal dialogs.
 * <p>
 * The tests need a graphics environment to create windows; on a headless CI runner they are
 * skipped with an assumption rather than failing.
 */
abstract class UiTestSupport {

    @TempDir
    static Path tempHome;

    private static boolean fontsInstalled;

    protected AppContext ctx;
    protected MainFrame frame;
    protected RecordingPrompts prompts;

    private final List<Throwable> edtFailures = new CopyOnWriteArrayList<>();
    private Thread.UncaughtExceptionHandler previousHandler;

    @BeforeAll
    static void requireDisplayAndIsolateHome() {
        assumeFalse(GraphicsEnvironment.isHeadless(), "UI tests need a display (skipped on headless CI)");
        // settings written by the app (theme preference) must not touch the developer's real home folder
        System.setProperty("sms.home", tempHome.toString());
        onEdt(() -> {
            if (!fontsInstalled) {
                Theme.installFonts();
                fontsInstalled = true;
            }
            Theme.apply(Theme.Mode.DARK);
        });
    }

    @AfterAll
    static void restoreHome() {
        System.clearProperty("sms.home");
    }

    @BeforeEach
    void openMainWindow() {
        previousHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> edtFailures.add(e));
        prompts = new RecordingPrompts();
        Prompts.install(prompts);
        ctx = TestDatabase.context(true);
        frame = onEdt(() -> {
            MainFrame f = new MainFrame(ctx);
            f.pack();
            f.setSize(1360, 860);
            f.validate();
            return f;
        });
    }

    @AfterEach
    void closeEverything() {
        Prompts.reset();
        onEdt(() -> {
            for (JDialog dialog : prompts.dialogs) {
                dialog.dispose();
            }
            if (!Theme.isDark()) {
                Theme.toggle();
            }
            frame.dispose();
        });
        Thread.setDefaultUncaughtExceptionHandler(previousHandler);
        if (!edtFailures.isEmpty()) {
            Throwable first = edtFailures.get(0);
            fail("Uncaught exception on the event dispatch thread: " + first, first);
        }
    }

    /** The page's panel, after switching to it. */
    protected Container open(Page page) {
        return onEdt(() -> {
            frame.showPage(page);
            frame.validate();
            return (Container) frame.view(page);
        });
    }
}
