package com.peterungab.sms.tools;

import com.peterungab.sms.App;
import com.peterungab.sms.AppContext;
import com.peterungab.sms.model.Student;
import com.peterungab.sms.model.StudentFilter;
import com.peterungab.sms.ui.MainFrame;
import com.peterungab.sms.ui.Page;
import com.peterungab.sms.ui.Theme;
import com.peterungab.sms.ui.dialogs.StudentDialog;
import com.peterungab.sms.ui.dialogs.StudentRecordDialog;

import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.RepaintManager;
import javax.swing.RootPaneContainer;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Developer utility that renders the real UI offscreen (no visible window needed) to PNG files
 * for the README. Usage:
 * <pre>java -cp student-management-system.jar com.peterungab.sms.tools.ScreenshotTool docs</pre>
 */
public final class ScreenshotTool {

    private static final int WIDTH = 1440;
    private static final int HEIGHT = 900;
    private static final double SCALE = 4.0 / 3.0; // 1920 x 1200 output (16:10)

    private ScreenshotTool() {
    }

    public static void main(String[] args) throws Exception {
        System.setProperty("awt.useSystemAAFontSettings", "on"); // grayscale text AA looks best in PNGs
        Path out = Path.of(args.length > 0 ? args[0] : "docs");
        Files.createDirectories(out);

        Theme.installFonts();
        AppContext ctx = App.bootstrap();

        SwingUtilities.invokeAndWait(() -> {
            Theme.apply(Theme.Mode.DARK);
            MainFrame frame = new MainFrame(ctx);
            frame.pack();
            Insets in = frame.getInsets();
            frame.setSize(WIDTH + in.left + in.right, HEIGHT + in.top + in.bottom);
            frame.validate();

            frame.showPage(Page.STUDENTS);
            selectRow(frame, 2);
            render(frame, out.resolve("screenshot.png"));

            frame.showPage(Page.DASHBOARD);
            render(frame, out.resolve("dashboard.png"));

            frame.showPage(Page.ENROLLMENTS);
            selectRow(frame, 1);
            render(frame, out.resolve("enrollments.png"));

            frame.showPage(Page.COURSES);
            render(frame, out.resolve("courses.png"));

            Student top = ctx.students().search(StudentFilter.all().withKeyword("2025-00312")).get(0);
            StudentRecordDialog record = new StudentRecordDialog(frame, ctx, top.id());
            record.pack();
            Insets ri = record.getInsets();
            record.setSize(1080 + ri.left + ri.right, 660 + ri.top + ri.bottom);
            render(record, out.resolve("student-record.png"));
            record.dispose();

            // "Add student" dialog after pressing Save with invalid input: inline validation messages
            StudentDialog add = new StudentDialog(frame, ctx, null);
            List<JTextField> fields = findAll(add.getContentPane(), JTextField.class);
            String[] values = {"2023-00123", "Juan", "Dela Cruz", "juan.delacruz@", "0917-555", "2031-02-14"};
            for (int i = 0; i < values.length && i < fields.size(); i++) {
                fields.get(i).setText(values[i]);
            }
            findAll(add.getContentPane(), JButton.class).stream()
                    .filter(b -> "Add Student".equals(b.getText())).findFirst().orElseThrow().doClick();
            render(add, out.resolve("validation.png"));
            add.dispose();

            // same window after a live theme switch
            frame.toggleTheme();
            frame.showPage(Page.DASHBOARD);
            render(frame, out.resolve("dashboard-light.png"));
            frame.showPage(Page.STUDENTS);
            selectRow(frame, 2);
            render(frame, out.resolve("students-light.png"));
            frame.toggleTheme();
            frame.dispose();
        });
        System.out.println("Screenshots written to " + out.toAbsolutePath());
        System.exit(0);
    }

    private static void selectRow(Container root, int row) {
        JTable table = findVisibleTable(root);
        if (table != null && table.getRowCount() > row) {
            table.setRowSelectionInterval(row, row);
        }
    }

    private static <T extends Component> List<T> findAll(Container root, Class<T> type) {
        List<T> result = new ArrayList<>();
        for (Component child : root.getComponents()) {
            if (type.isInstance(child)) {
                result.add(type.cast(child));
            }
            if (child instanceof Container sub) {
                result.addAll(findAll(sub, type));
            }
        }
        return result;
    }

    private static JTable findVisibleTable(Container c) {
        for (Component child : c.getComponents()) {
            if (!child.isVisible()) {
                continue;
            }
            if (child instanceof JTable t) {
                return t;
            }
            if (child instanceof Container sub) {
                JTable t = findVisibleTable(sub);
                if (t != null) {
                    return t;
                }
            }
        }
        return null;
    }

    private static void render(Window window, Path file) {
        window.validate();
        JComponent root = ((RootPaneContainer) window).getRootPane();
        layoutTree(root);
        // Render at an integer 2x scale (glyph advances scale exactly, so no clipped text),
        // then downsample to the target size - effectively supersampled anti-aliasing.
        BufferedImage hiRes = new BufferedImage(root.getWidth() * 2, root.getHeight() * 2, BufferedImage.TYPE_INT_RGB);
        Graphics2D hg = hiRes.createGraphics();
        hg.scale(2, 2);
        // paint() rather than printAll(): JTable hides the selection when printing.
        // Double buffering is switched off so everything is drawn straight at 2x.
        RepaintManager rm = RepaintManager.currentManager(root);
        rm.setDoubleBufferingEnabled(false);
        root.paint(hg);
        rm.setDoubleBufferingEnabled(true);
        hg.dispose();

        BufferedImage img = new BufferedImage((int) Math.round(root.getWidth() * SCALE),
                (int) Math.round(root.getHeight() * SCALE), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(hiRes, 0, 0, img.getWidth(), img.getHeight(), null);
        g.dispose();
        try {
            ImageIO.write(img, "png", file.toFile());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        System.out.println("  " + file + " (" + img.getWidth() + "x" + img.getHeight() + ")");
    }

    private static void layoutTree(Container c) {
        c.doLayout();
        for (Component child : c.getComponents()) {
            if (child instanceof Container sub) {
                layoutTree(sub);
            }
        }
    }
}
