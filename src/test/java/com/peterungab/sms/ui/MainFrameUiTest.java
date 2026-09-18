package com.peterungab.sms.ui;

import com.formdev.flatlaf.themes.FlatMacDarkLaf;
import com.formdev.flatlaf.themes.FlatMacLightLaf;
import com.peterungab.sms.ui.dialogs.CourseDialog;
import com.peterungab.sms.ui.dialogs.StudentDialog;
import org.junit.jupiter.api.Test;

import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.UIManager;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Rectangle;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static com.peterungab.sms.ui.Swing.button;
import static com.peterungab.sms.ui.Swing.findAll;
import static com.peterungab.sms.ui.Swing.labelTexts;
import static com.peterungab.sms.ui.Swing.menuItem;
import static com.peterungab.sms.ui.Swing.onEdt;
import static com.peterungab.sms.ui.Swing.pressKey;
import static com.peterungab.sms.ui.Swing.table;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The main window itself: geometry, icon, navigation, menus and the theme switch. */
class MainFrameUiTest extends UiTestSupport {

    @Test
    void windowOpensCenteredOnScreenWithASensibleSizeTitleAndIcon() {
        onEdt(() -> {
            MainFrame fresh = new MainFrame(ctx); // exactly what App.main builds, before any resizing
            try {
                Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
                Dimension size = fresh.getSize();
                assertTrue(size.width <= screen.width && size.height <= screen.height, "fits the screen: " + size);
                assertTrue(size.width >= Math.min(1100, screen.width) && size.height >= Math.min(680, screen.height),
                        "not tiny: " + size);
                Point expected = new Point(screen.x + (screen.width - size.width) / 2,
                        screen.y + (screen.height - size.height) / 2);
                Point actual = fresh.getLocation();
                assertTrue(Math.abs(actual.x - expected.x) <= 2 && Math.abs(actual.y - expected.y) <= 2,
                        "centered: expected " + expected + " but was " + actual);
                assertEquals("Student Management System", fresh.getTitle());
                assertEquals(5, fresh.getIconImages().size(), "icon in 16..128 px for the title bar, taskbar and Alt+Tab");
                assertEquals(Page.DASHBOARD, fresh.currentPage());
            } finally {
                fresh.dispose();
            }
        });
    }

    @Test
    void sidebarAndMenuSwitchPagesAndRefreshThem() {
        onEdt(() -> {
            for (JToggleButton b : findAll(frame.getContentPane(), JToggleButton.class)) {
                if ("Courses".equals(b.getText())) {
                    b.doClick();
                }
            }
        });
        assertEquals(Page.COURSES, frame.currentPage());
        onEdt(() -> assertEquals(ctx.courses().count(), table((Container) frame.view(Page.COURSES)).getRowCount()));

        onEdt(() -> menuItem(frame.getJMenuBar(), "View", "Enrollments & Grades").doClick());
        assertEquals(Page.ENROLLMENTS, frame.currentPage());

        onEdt(() -> pressKey(frame.getRootPane(), "ctrl 2"));
        assertEquals(Page.STUDENTS, frame.currentPage());
        onEdt(() -> {
            JToggleButton selected = findAll(frame.getContentPane(), JToggleButton.class).stream()
                    .filter(JToggleButton::isSelected).findFirst().orElseThrow();
            assertEquals("Students", selected.getText(), "sidebar follows the shortcut");
        });
    }

    @Test
    void themeToggleSwitchesLookAndFeelLiveAndRemembersTheChoice() throws Exception {
        assertTrue(Theme.isDark());
        assertInstanceOf(FlatMacDarkLaf.class, UIManager.getLookAndFeel());
        Path settings = tempHome.resolve("settings.properties");

        onEdt(() -> button(frame.getContentPane(), "Light mode").doClick()); // the sidebar switch
        assertFalse(Theme.isDark());
        assertInstanceOf(FlatMacLightLaf.class, UIManager.getLookAndFeel());
        assertEquals(Theme.Mode.LIGHT, AppSettings.loadTheme());
        assertTrue(Files.readString(settings, StandardCharsets.UTF_8).contains("ui.theme=LIGHT"));
        onEdt(() -> {
            assertTrue(labelTexts(frame.statusBar()).contains("Light theme enabled"));
            assertEquals(Theme.background(), ((Container) frame.view(Page.STUDENTS)).getBackground(),
                    "pages pick up the new palette");
        });

        onEdt(() -> pressKey(frame.getRootPane(), "ctrl T"));
        assertTrue(Theme.isDark());
        assertEquals(Theme.Mode.DARK, AppSettings.loadTheme());
        onEdt(() -> assertEquals("Light mode", button(frame.getContentPane(), "Light mode").getText()));
    }

    @Test
    void newRecordOpensTheDialogOfTheCurrentPageAndCancellingChangesNothing() {
        int courses = ctx.courses().count();
        open(Page.COURSES);
        prompts.onDialog(d -> {
            CourseDialog dialog = (CourseDialog) d;
            assertEquals("Add Course", dialog.getTitle());
            dialog.dispose(); // Cancel / Esc
        });
        onEdt(() -> menuItem(frame.getJMenuBar(), "File", "New Record").doClick());
        assertEquals(1, prompts.dialogs.size());
        assertEquals(courses, ctx.courses().count());

        // and on the Courses page the dialog really creates a course (code is normalized)
        prompts.onDialog(d -> {
            CourseDialog dialog = (CourseDialog) d;
            ((JTextField) dialog.field("code")).setText("cpe499");
            ((JTextField) dialog.field("title")).setText("Embedded Systems Design");
            ((JSpinner) dialog.field("units")).setValue(4);
            dialog.submit();
            assertTrue(dialog.result().isPresent(), dialog.errorMessage("code"));
        });
        onEdt(() -> button((Container) frame.view(Page.COURSES), "Add Course").doClick());
        assertEquals(courses + 1, ctx.courses().count());
        assertTrue(ctx.courses().findAll().stream().anyMatch(c -> c.code().equals("CPE 499") && c.units() == 4));
        onEdt(() -> {
            assertEquals(courses + 1, table((Container) frame.view(Page.COURSES)).getRowCount());
            assertTrue(labelTexts(frame.statusBar()).contains("Added course CPE 499"));
        });

        open(Page.STUDENTS);
        prompts.onDialog(d -> assertInstanceOf(StudentDialog.class, d));
        onEdt(() -> pressKey(frame.getRootPane(), "ctrl N"));
        assertEquals(3, prompts.dialogs.size());
    }
}
