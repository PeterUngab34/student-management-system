package com.peterungab.sms.ui;

import com.peterungab.sms.model.Program;
import com.peterungab.sms.model.Student;
import com.peterungab.sms.model.StudentFilter;
import com.peterungab.sms.model.StudentStatus;
import com.peterungab.sms.ui.components.Choice;
import com.peterungab.sms.ui.dialogs.StudentDialog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.JComboBox;
import javax.swing.JTable;
import javax.swing.JTextField;
import java.awt.Container;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Collator;
import java.util.List;
import java.util.Locale;

import static com.peterungab.sms.ui.Swing.button;
import static com.peterungab.sms.ui.Swing.cell;
import static com.peterungab.sms.ui.Swing.clickHeader;
import static com.peterungab.sms.ui.Swing.combo;
import static com.peterungab.sms.ui.Swing.doubleClickRow;
import static com.peterungab.sms.ui.Swing.labelTexts;
import static com.peterungab.sms.ui.Swing.menuItem;
import static com.peterungab.sms.ui.Swing.onEdt;
import static com.peterungab.sms.ui.Swing.pressKey;
import static com.peterungab.sms.ui.Swing.table;
import static com.peterungab.sms.ui.Swing.textField;
import static com.peterungab.sms.ui.Swing.waitUntil;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Drives the Students page end to end: dialogs, validation, search, filters, sorting, export. */
class StudentsViewUiTest extends UiTestSupport {

    // column indexes of the students table
    private static final int NUMBER = 0;
    private static final int NAME = 1;
    private static final int PROGRAM = 2;
    private static final int EMAIL = 4;
    private static final int STATUS = 5;

    private static final int SEEDED_STUDENTS = 25;

    @TempDir
    Path exportDir;

    @Test
    void addStudentDialogSavesToDatabaseAndSelectsTheNewRow() {
        Container view = open(Page.STUDENTS);
        JTable table = table(view);
        String[] number = new String[1];

        prompts.onDialog(d -> {
            StudentDialog dialog = (StudentDialog) d;
            number[0] = ((JTextField) dialog.field("studentNumber")).getText(); // suggested by the app
            assertTrue(number[0].matches("\\d{4}-\\d{5}"), "suggested number " + number[0]);
            ((JTextField) dialog.field("firstName")).setText("Juan");
            ((JTextField) dialog.field("lastName")).setText("Dela Cruz");
            ((JTextField) dialog.field("email")).setText("Juan.DelaCruz@student.example.edu.ph");
            ((JTextField) dialog.field("phone")).setText("0917 123 4567");
            ((JTextField) dialog.field("birthDate")).setText("2005-06-18");
            dialog.field("program"); // exists; keep the first program
            ((JComboBox<?>) dialog.field("yearLevel")).setSelectedIndex(1); // 2nd year
            dialog.submit();
            assertTrue(dialog.result().isPresent(), "dialog should have saved");
            assertFalse(dialog.isDisplayable(), "dialog disposes itself after saving");
        });
        onEdt(() -> button(view, "Add Student").doClick());

        // the database has the normalized record
        List<Student> saved = ctx.students().search(StudentFilter.all().withKeyword(number[0]));
        assertEquals(1, saved.size());
        Student s = saved.get(0);
        assertEquals("Juan Dela Cruz", s.fullName());
        assertEquals("juan.delacruz@student.example.edu.ph", s.email());
        assertEquals("09171234567", s.phone());
        assertEquals(2, s.yearLevel());
        assertEquals(SEEDED_STUDENTS + 1, ctx.students().count());

        // the table shows it, it is selected, and the status bar says so
        onEdt(() -> {
            assertEquals(SEEDED_STUDENTS + 1, table.getRowCount());
            int row = table.getSelectedRow();
            assertTrue(row >= 0, "new row selected");
            assertEquals(number[0], cell(table, row, NUMBER));
            assertEquals("Dela Cruz, Juan", cell(table, row, NAME));
            assertTrue(labelTexts(frame.statusBar()).contains("Added Juan Dela Cruz (" + number[0] + ")"));
            assertTrue(labelTexts(view).contains("Showing all " + (SEEDED_STUDENTS + 1) + " students"));
        });
    }

    @Test
    void invalidInputShowsMessagesNextToTheFieldsAndKeepsTheDialogOpen() {
        Container view = open(Page.STUDENTS);
        prompts.onDialog(d -> {
            StudentDialog dialog = (StudentDialog) d;
            ((JTextField) dialog.field("studentNumber")).setText("2023-00123"); // Maria Santos already has it
            ((JTextField) dialog.field("firstName")).setText("");
            ((JTextField) dialog.field("lastName")).setText("R2-D2");
            ((JTextField) dialog.field("email")).setText("juan.delacruz@");
            ((JTextField) dialog.field("phone")).setText("0917-555");
            dialog.submit();

            assertTrue(dialog.result().isEmpty(), "nothing saved");
            assertTrue(dialog.isDisplayable(), "dialog stays open");
            assertEquals("Student number 2023-00123 is already in use.", dialog.errorMessage("studentNumber"));
            assertEquals("First name is required.", dialog.errorMessage("firstName"));
            assertEquals("Last name may only contain letters, spaces, . ' and -.", dialog.errorMessage("lastName"));
            assertEquals("Enter a valid e-mail address.", dialog.errorMessage("email"));
            assertEquals("Use a PH mobile number, e.g. 09171234567.", dialog.errorMessage("phone"));
            assertEquals("", dialog.errorMessage("birthDate"));

            // a malformed date is caught before the service is even called
            ((JTextField) dialog.field("birthDate")).setText("18/06/2005");
            dialog.submit();
            assertEquals("Use the format YYYY-MM-DD, e.g. 2005-06-18.", dialog.errorMessage("birthDate"));
            assertEquals("", dialog.errorMessage("firstName"), "earlier messages are cleared on every attempt");

            // fixing everything lets it through, and the old messages are gone
            ((JTextField) dialog.field("studentNumber")).setText("2026-00777");
            ((JTextField) dialog.field("firstName")).setText("Ana");
            ((JTextField) dialog.field("lastName")).setText("Ibañez-Reyes");
            ((JTextField) dialog.field("email")).setText("ana.ibanez@student.example.edu.ph");
            ((JTextField) dialog.field("phone")).setText("");
            ((JTextField) dialog.field("birthDate")).setText("2005-06-18");
            dialog.submit();
            assertTrue(dialog.result().isPresent());
            assertEquals("", dialog.errorMessage("studentNumber"));
            assertEquals("", dialog.errorMessage("email"));
        });
        onEdt(() -> pressKey(frame.getRootPane(), "ctrl N"));

        assertEquals(SEEDED_STUDENTS + 1, ctx.students().count());
        assertEquals("Ibañez-Reyes", ctx.students().search(StudentFilter.all().withKeyword("2026-00777")).get(0).lastName());
        onEdt(() -> assertEquals(SEEDED_STUDENTS + 1, table(view).getRowCount()));
    }

    @Test
    void doubleClickingARowOpensEditDialogPrefilledAndSavesChanges() {
        Container view = open(Page.STUDENTS);
        JTable table = table(view);
        Student maria = ctx.students().search(StudentFilter.all().withKeyword("2023-00123")).get(0);
        int row = onEdt(() -> {
            int r = rowOf(table, maria.studentNumber());
            table.setRowSelectionInterval(r, r);
            return r;
        });

        prompts.onDialog(d -> {
            StudentDialog dialog = (StudentDialog) d;
            assertEquals("Edit Student", dialog.getTitle());
            assertEquals("2023-00123", ((JTextField) dialog.field("studentNumber")).getText());
            assertEquals("Maria", ((JTextField) dialog.field("firstName")).getText());
            assertEquals("Santos", ((JTextField) dialog.field("lastName")).getText());
            assertEquals(maria.email(), ((JTextField) dialog.field("email")).getText());
            assertEquals(maria.program(), ((JComboBox<?>) dialog.field("program")).getSelectedItem());
            assertEquals(maria.status(), ((JComboBox<?>) dialog.field("status")).getSelectedItem());
            @SuppressWarnings("unchecked")
            Choice<Integer> year = (Choice<Integer>) ((JComboBox<?>) dialog.field("yearLevel")).getSelectedItem();
            assertEquals(maria.yearLevel(), year.value());

            ((JTextField) dialog.field("lastName")).setText("Santos-Cruz");
            ((JComboBox<?>) dialog.field("status")).setSelectedItem(StudentStatus.ON_LEAVE);
            dialog.submit();
            assertTrue(dialog.result().isPresent());
        });
        onEdt(() -> doubleClickRow(table, row));

        Student updated = ctx.students().findById(maria.id()).orElseThrow();
        assertEquals("Santos-Cruz", updated.lastName());
        assertEquals(StudentStatus.ON_LEAVE, updated.status());
        onEdt(() -> {
            int r = table.getSelectedRow();
            assertEquals("Santos-Cruz, Maria", cell(table, r, NAME), "row updated in place and still selected");
            assertEquals(StudentStatus.ON_LEAVE, table.getValueAt(r, STATUS));
            assertTrue(labelTexts(frame.statusBar()).contains("Saved changes to Maria Santos-Cruz"));
        });
    }

    @Test
    void deleteAsksForConfirmationAndRemovesTheStudentWithTheirEnrollments() {
        Container view = open(Page.STUDENTS);
        JTable table = table(view);
        Student maria = ctx.students().search(StudentFilter.all().withKeyword("2023-00123")).get(0);
        int enrollmentsBefore = ctx.enrollments().academicRecord(maria.id()).enrollments().size();
        assertTrue(enrollmentsBefore > 0, "seed data gives Maria enrollments");
        onEdt(() -> {
            int r = rowOf(table, maria.studentNumber());
            table.setRowSelectionInterval(r, r);
            assertTrue(button(view, "Delete").isEnabled());
        });

        // Cancel keeps everything
        prompts.answerConfirmations(false);
        onEdt(() -> pressKey(table, "DELETE"));
        assertEquals(1, prompts.confirmations.size());
        assertTrue(prompts.confirmations.get(0).contains("Delete student: "));
        assertTrue(prompts.confirmations.get(0).contains("Their " + enrollmentsBefore + " enrollment record(s)"));
        assertTrue(ctx.students().findById(maria.id()).isPresent());

        // Confirm deletes the student and, through ON DELETE CASCADE, the enrollments
        int totalEnrollments = ctx.enrollments().count();
        prompts.answerConfirmations(true);
        onEdt(() -> button(view, "Delete").doClick());
        assertTrue(ctx.students().findById(maria.id()).isEmpty());
        assertEquals(totalEnrollments - enrollmentsBefore, ctx.enrollments().count());
        onEdt(() -> {
            assertEquals(SEEDED_STUDENTS - 1, table.getRowCount());
            assertEquals(-1, rowOf(table, maria.studentNumber()));
            assertEquals(-1, table.getSelectedRow());
            assertFalse(button(view, "Delete").isEnabled(), "nothing selected any more");
            assertTrue(labelTexts(frame.statusBar()).contains("Deleted Maria Santos"));
        });
    }

    @Test
    void typingInTheSearchBoxFiltersTheTableLive() {
        Container view = open(Page.STUDENTS);
        JTable table = table(view);
        JTextField search = textField(view);
        onEdt(() -> assertEquals(SEEDED_STUDENTS, table.getRowCount()));

        onEdt(() -> search.setText("santos"));
        waitUntil("search to filter the table", () -> table.getRowCount() < SEEDED_STUDENTS);
        onEdt(() -> {
            assertTrue(table.getRowCount() >= 1);
            for (int r = 0; r < table.getRowCount(); r++) {
                String haystack = (cell(table, r, NAME) + " " + cell(table, r, EMAIL)).toLowerCase(Locale.ROOT);
                assertTrue(haystack.contains("santos"), "row " + r + ": " + haystack);
            }
            assertTrue(labelTexts(view).contains("Showing " + table.getRowCount() + " of " + SEEDED_STUDENTS + " students"));
        });

        // a student number works too
        onEdt(() -> search.setText("2024-00218"));
        waitUntil("search by student number", () -> table.getRowCount() == 1 && cell(table, 0, NAME).equals("Bautista, Miguel"));

        // Clear resets search and shows everyone again
        onEdt(() -> button(view, "Clear").doClick());
        onEdt(() -> {
            assertEquals("", search.getText());
            assertEquals(SEEDED_STUDENTS, table.getRowCount());
        });
    }

    @Test
    void filterCombosNarrowTheListAndCombineWithEachOther() {
        Container view = open(Page.STUDENTS);
        JTable table = table(view);
        JComboBox<Choice<Program>> programFilter = combo(view, 0);
        JComboBox<Choice<Integer>> yearFilter = combo(view, 1);
        JComboBox<Choice<StudentStatus>> statusFilter = combo(view, 2);

        Program bscpe = ctx.students().programs().stream().filter(p -> p.code().equals("BSCpE")).findFirst().orElseThrow();
        int expected = ctx.students().search(StudentFilter.all().withProgramId(bscpe.id())).size();
        assertTrue(expected > 0 && expected < SEEDED_STUDENTS);

        onEdt(() -> Swing.select(programFilter, c -> bscpe.equals(c.value())));
        onEdt(() -> {
            assertEquals(expected, table.getRowCount());
            for (int r = 0; r < table.getRowCount(); r++) {
                assertEquals("BSCpE", cell(table, r, PROGRAM));
            }
        });

        int expectedWithStatus = ctx.students().search(StudentFilter.all().withProgramId(bscpe.id())
                .withStatus(StudentStatus.ACTIVE)).size();
        onEdt(() -> Swing.select(statusFilter, c -> c.value() == StudentStatus.ACTIVE));
        onEdt(() -> {
            assertEquals(expectedWithStatus, table.getRowCount());
            for (int r = 0; r < table.getRowCount(); r++) {
                assertEquals(StudentStatus.ACTIVE, table.getValueAt(r, STATUS));
            }
        });

        int expectedAll = ctx.students().search(StudentFilter.all().withProgramId(bscpe.id())
                .withStatus(StudentStatus.ACTIVE).withYearLevel(4)).size();
        onEdt(() -> Swing.select(yearFilter, c -> Integer.valueOf(4).equals(c.value())));
        onEdt(() -> assertEquals(expectedAll, table.getRowCount()));

        onEdt(() -> button(view, "Clear").doClick());
        onEdt(() -> {
            assertEquals(0, programFilter.getSelectedIndex());
            assertEquals(0, yearFilter.getSelectedIndex());
            assertEquals(0, statusFilter.getSelectedIndex());
            assertEquals(SEEDED_STUDENTS, table.getRowCount());
        });
    }

    @Test
    void clickingAColumnHeaderSortsAscendingThenDescending() {
        Container view = open(Page.STUDENTS);
        JTable table = table(view);
        Collator collator = Collator.getInstance();

        onEdt(() -> clickHeader(table, NAME));
        onEdt(() -> {
            assertEquals(SEEDED_STUDENTS, table.getRowCount());
            for (int r = 1; r < table.getRowCount(); r++) {
                assertTrue(collator.compare(cell(table, r - 1, NAME), cell(table, r, NAME)) <= 0,
                        "ascending at row " + r + ": " + cell(table, r - 1, NAME) + " / " + cell(table, r, NAME));
            }
        });

        onEdt(() -> clickHeader(table, NAME));
        onEdt(() -> {
            for (int r = 1; r < table.getRowCount(); r++) {
                assertTrue(collator.compare(cell(table, r - 1, NAME), cell(table, r, NAME)) >= 0,
                        "descending at row " + r);
            }
        });

        // sorting survives a reload (e.g. after a search) because it lives in the row sorter
        onEdt(() -> textField(view).setText("2023-")); // every student enrolled in 2023
        waitUntil("filtered reload", () -> table.getRowCount() < SEEDED_STUDENTS && table.getRowCount() > 1);
        onEdt(() -> {
            for (int r = 1; r < table.getRowCount(); r++) {
                assertTrue(collator.compare(cell(table, r - 1, NAME), cell(table, r, NAME)) >= 0,
                        "still descending after reload at row " + r);
            }
        });
    }

    @Test
    void exportCsvWritesTheRowsCurrentlyShownInTableOrder() throws Exception {
        Container view = open(Page.STUDENTS);
        JTable table = table(view);

        onEdt(() -> textField(view).setText("reyes"));
        waitUntil("filter before export", () -> table.getRowCount() < SEEDED_STUDENTS && table.getRowCount() > 0);
        onEdt(() -> clickHeader(table, NUMBER));

        File target = exportDir.resolve("my-students").toFile(); // no extension: the app adds .csv
        prompts.saveTo(target);
        onEdt(() -> button(view, "Export CSV").doClick());

        Path written = exportDir.resolve("my-students.csv");
        assertTrue(Files.exists(written), "export written with .csv appended");
        assertTrue(prompts.suggestedFiles.get(0).getName().matches("students-\\d{4}-\\d{2}-\\d{2}\\.csv"),
                "suggested file name: " + prompts.suggestedFiles.get(0));

        byte[] bytes = Files.readAllBytes(written);
        assertEquals((byte) 0xEF, bytes[0]);
        assertEquals((byte) 0xBB, bytes[1]);
        assertEquals((byte) 0xBF, bytes[2]);
        String content = new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8);
        String[] lines = content.split("\r\n");
        assertEquals("Student No.,Last Name,First Name,Program,Year Level,Email,Phone,Birth Date,Status,GPA", lines[0]);

        int rows = onEdt(table::getRowCount);
        assertEquals(rows + 1, lines.length);
        for (int r = 0; r < rows; r++) {
            int row = r;
            String number = onEdt(() -> cell(table, row, NUMBER));
            assertTrue(lines[r + 1].startsWith(number + ","), "line " + (r + 1) + " is row " + r + ": " + lines[r + 1]);
            assertTrue(lines[r + 1].toLowerCase(Locale.ROOT).contains("reyes"));
        }
        onEdt(() -> assertTrue(labelTexts(frame.statusBar()).contains("Exported " + rows + " row(s) to my-students.csv")));

        // exporting onto an existing file asks first; declining leaves the file untouched
        prompts.answerConfirmations(false);
        onEdt(() -> button(view, "Export CSV").doClick());
        assertEquals(1, prompts.confirmations.size());
        assertTrue(prompts.confirmations.get(0).startsWith("Replace file: my-students.csv already exists"));
        assertEquals(bytes.length, Files.size(written));

        // cancelling the file chooser exports nothing
        prompts.saveTo(null);
        onEdt(() -> menuItem(frame.getJMenuBar(), "File", "Export to CSV").doClick());
        assertEquals(1, prompts.confirmations.size());
    }

    @Test
    void ctrlEOnTheDashboardSwitchesToStudentsBeforeExporting() throws Exception {
        open(Page.DASHBOARD);
        File target = exportDir.resolve("all.csv").toFile();
        prompts.saveTo(target);

        onEdt(() -> pressKey(frame.getRootPane(), "ctrl E"));

        assertEquals(Page.STUDENTS, frame.currentPage());
        List<String> lines = Files.readAllLines(target.toPath(), StandardCharsets.UTF_8);
        assertEquals(SEEDED_STUDENTS + 1, lines.size());
    }

    private static int rowOf(JTable table, String studentNumber) {
        for (int r = 0; r < table.getRowCount(); r++) {
            if (cell(table, r, NUMBER).equals(studentNumber)) {
                return r;
            }
        }
        return -1;
    }
}
