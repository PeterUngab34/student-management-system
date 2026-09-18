package com.peterungab.sms.ui;

import com.peterungab.sms.model.Course;
import com.peterungab.sms.model.Enrollment;
import com.peterungab.sms.model.EnrollmentFilter;
import com.peterungab.sms.model.EnrollmentStatus;
import com.peterungab.sms.model.Program;
import com.peterungab.sms.model.Student;
import com.peterungab.sms.model.StudentStatus;
import com.peterungab.sms.service.AcademicRecord;
import com.peterungab.sms.ui.components.Choice;
import com.peterungab.sms.ui.dialogs.EnrollDialog;
import com.peterungab.sms.ui.dialogs.GradeDialog;
import com.peterungab.sms.ui.dialogs.StudentRecordDialog;
import org.junit.jupiter.api.Test;

import javax.swing.JComboBox;
import javax.swing.JTable;
import javax.swing.JTextField;
import java.awt.Container;
import java.math.BigDecimal;
import java.util.List;

import static com.peterungab.sms.ui.Swing.button;
import static com.peterungab.sms.ui.Swing.cell;
import static com.peterungab.sms.ui.Swing.findAll;
import static com.peterungab.sms.ui.Swing.labelTexts;
import static com.peterungab.sms.ui.Swing.onEdt;
import static com.peterungab.sms.ui.Swing.pressKey;
import static com.peterungab.sms.ui.Swing.select;
import static com.peterungab.sms.ui.Swing.table;
import static com.peterungab.sms.ui.Swing.textField;
import static com.peterungab.sms.ui.Swing.waitUntil;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/** Enrolling, grading, dropping and the academic record, driven through the real dialogs. */
class EnrollmentsUiTest extends UiTestSupport {

    // column indexes of the enrollments table
    private static final int STUDENT_NO = 0;
    private static final int COURSE = 2;
    private static final int GRADE = 6;
    private static final int REMARKS = 8;

    @Test
    void enrollFromTheRecordDialogThenGradeItAndSeeTheGpaChange() {
        Program program = ctx.students().programs().get(0);
        Student liza = ctx.students().create(new Student(null, "2026-00999", "Liza", "Cruz",
                "liza.cruz@student.example.edu.ph", null, null, program, 1, StudentStatus.ACTIVE));
        Course course = ctx.courses().findAll().stream().filter(c -> c.code().equals("CPE 301")).findFirst().orElseThrow();

        // 1. open the record from the Students page; it is empty, so enroll her from there
        Container students = open(Page.STUDENTS);
        prompts.onDialog(d -> {
            if (d instanceof StudentRecordDialog record) {
                assertEquals("—", record.cumulativeGpaText());
                assertEquals("0", record.unitsEarnedText());
                assertTrue(labelTexts(record.getContentPane()).contains("No enrollments yet."));
                button(record.getContentPane(), "Enroll in Course").doClick(); // nested modal EnrollDialog
                // after enrolling, the dialog rebuilt itself with the new course in progress
                assertEquals("—", record.cumulativeGpaText());
                assertEquals("0", record.unitsEarnedText());
                assertTrue(labelTexts(record.getContentPane()).contains("1 enrollment(s)"));
                assertEquals(1, table(record.getContentPane()).getRowCount());
                assertTrue(labelTexts(record.getContentPane()).contains("in progress"));
            } else if (d instanceof EnrollDialog enroll) {
                @SuppressWarnings("unchecked")
                JComboBox<Choice<Student>> studentBox = (JComboBox<Choice<Student>>) enroll.field("student");
                assertEquals(liza.id(), ((Choice<Student>) studentBox.getSelectedItem()).value().id(),
                        "the record's student is preselected");
                @SuppressWarnings("unchecked")
                JComboBox<Choice<Course>> courseBox = (JComboBox<Choice<Course>>) enroll.field("course");
                select(courseBox, c -> course.equals(c.value()));
                assertEquals("2026-2027", ((JTextField) enroll.field("schoolYear")).getText(), "defaults to the current term");
                enroll.submit();
                assertTrue(enroll.result().isPresent(), "enrolled: " + enroll.errorMessage("course"));
            } else {
                fail("unexpected dialog " + d.getTitle());
            }
        });
        JTable studentTable = table(students);
        onEdt(() -> {
            int r = rowOf(studentTable, STUDENT_NO, "2026-00999");
            studentTable.setRowSelectionInterval(r, r);
            pressKey(studentTable, "ctrl R");
        });
        assertEquals(1, prompts.dialogs.stream().filter(d -> d instanceof StudentRecordDialog).count());
        assertEquals(1, prompts.dialogs.stream().filter(d -> d instanceof EnrollDialog).count());
        List<Enrollment> hers = ctx.enrollments().search(new EnrollmentFilter("2026-00999", null, null, null));
        assertEquals(1, hers.size());
        assertEquals(EnrollmentStatus.ENROLLED, hers.get(0).status());

        // 2. record a grade on the Enrollments page
        Container enrollments = open(Page.ENROLLMENTS);
        JTable table = table(enrollments);
        onEdt(() -> textField(enrollments).setText("2026-00999"));
        waitUntil("enrollment search", () -> table.getRowCount() == 1 && cell(table, 0, COURSE).equals("CPE 301"));
        onEdt(() -> {
            table.setRowSelectionInterval(0, 0);
            assertTrue(button(enrollments, "Grade").isEnabled());
            assertTrue(button(enrollments, "Drop").isEnabled());
            assertEquals("In progress", cell(table, 0, REMARKS));
        });
        prompts.onDialog(d -> {
            GradeDialog grade = (GradeDialog) d;
            @SuppressWarnings("unchecked")
            JComboBox<Choice<BigDecimal>> gradeBox = (JComboBox<Choice<BigDecimal>>) grade.field("grade");
            assertEquals(0, gradeBox.getSelectedIndex(), "no grade yet");
            select(gradeBox, c -> c.value() != null && c.value().compareTo(new BigDecimal("1.50")) == 0);
            grade.submit();
            assertTrue(grade.result().isPresent());
        });
        onEdt(() -> button(enrollments, "Grade").doClick());

        Enrollment graded = ctx.enrollments().findById(hers.get(0).id()).orElseThrow();
        assertEquals(0, new BigDecimal("1.50").compareTo(graded.grade()));
        assertEquals(EnrollmentStatus.COMPLETED, graded.status());
        onEdt(() -> {
            assertEquals(0, table.getSelectedRow(), "graded row stays selected");
            assertEquals("1.50", cell(table, 0, GRADE));
            assertEquals("Passed", cell(table, 0, REMARKS));
            assertFalse(button(enrollments, "Drop").isEnabled(), "a graded course cannot be dropped");
            assertTrue(labelTexts(frame.statusBar()).contains("Recorded 1.50 for Liza Cruz in CPE 301"));
        });

        // 3. the academic record now shows the GPA and the units, from the Enrollments page's Record button
        prompts.onDialog(d -> {
            StudentRecordDialog record = (StudentRecordDialog) d;
            assertEquals("1.50", record.cumulativeGpaText());
            assertEquals(String.valueOf(course.units()), record.unitsEarnedText());
            String texts = labelTexts(record.getContentPane());
            assertTrue(texts.contains("Very good"), texts);
            assertTrue(texts.contains("1st Sem 2026-2027"), texts);
            assertFalse(texts.contains("in progress"), texts);
        });
        onEdt(() -> button(enrollments, "Record").doClick());
        assertEquals(2, prompts.dialogs.stream().filter(d -> d instanceof StudentRecordDialog).count());

        AcademicRecord record = ctx.enrollments().academicRecord(liza.id());
        assertEquals(0, new BigDecimal("1.50").compareTo(record.gpa().orElseThrow()));
        assertEquals(course.units(), record.unitsEarned());

        // 4. the Students page shows the GPA column for her too
        Container studentsAgain = open(Page.STUDENTS);
        JTable again = table(studentsAgain);
        onEdt(() -> assertEquals("1.50", cell(again, rowOf(again, STUDENT_NO, "2026-00999"), 6)));
    }

    @Test
    void enrollingTwiceInTheSameTermIsRejectedOnTheCourseField() {
        Container view = open(Page.ENROLLMENTS);
        Student student = ctx.students().search(com.peterungab.sms.model.StudentFilter.all()
                .withStatus(StudentStatus.ACTIVE)).get(0);
        Course course = ctx.courses().findAll().get(0);
        // make sure the pair is free this term so the first attempt succeeds
        ctx.enrollments().search(new EnrollmentFilter(student.studentNumber(), null, null, null)).stream()
                .filter(e -> e.courseId() == course.id() && e.schoolYear().equals("2026-2027"))
                .forEach(e -> ctx.enrollments().delete(e.id()));
        int before = ctx.enrollments().count();

        prompts.onDialog(d -> {
            EnrollDialog enroll = (EnrollDialog) d;
            @SuppressWarnings("unchecked")
            JComboBox<Choice<Student>> studentBox = (JComboBox<Choice<Student>>) enroll.field("student");
            select(studentBox, c -> student.equals(c.value()));
            @SuppressWarnings("unchecked")
            JComboBox<Choice<Course>> courseBox = (JComboBox<Choice<Course>>) enroll.field("course");
            select(courseBox, c -> course.equals(c.value()));
            enroll.submit();
        });
        onEdt(() -> button(view, "Enroll Student").doClick());
        assertTrue(prompts.lastDialog(EnrollDialog.class).result().isPresent());
        assertEquals(before + 1, ctx.enrollments().count());
        onEdt(() -> assertTrue(labelTexts(frame.statusBar()).contains("Enrolled " + student.fullName() + " in " + course.code())));

        onEdt(() -> pressKey(frame.getRootPane(), "ctrl N"));
        EnrollDialog second = prompts.lastDialog(EnrollDialog.class);
        assertTrue(second.result().isEmpty());
        assertTrue(second.isDisplayable(), "stays open to be corrected");
        assertEquals(student.fullName() + " is already enrolled in " + course.code() + " for 1st Sem 2026-2027.",
                second.errorMessage("course"));
        assertEquals(before + 1, ctx.enrollments().count());

        // a malformed school year is reported on its own field
        onEdt(() -> {
            ((JTextField) second.field("schoolYear")).setText("2026");
            second.submit();
        });
        assertEquals("Use the format YYYY-YYYY, e.g. 2026-2027.", second.errorMessage("schoolYear"));
        assertEquals("", second.errorMessage("course"));
    }

    @Test
    void dropAndRemoveGoThroughConfirmationAndUpdateTheRow() {
        Container view = open(Page.ENROLLMENTS);
        JTable table = table(view);
        int row = onEdt(() -> {
            for (int r = 0; r < table.getRowCount(); r++) {
                if (cell(table, r, REMARKS).equals("In progress")) {
                    table.setRowSelectionInterval(r, r);
                    return r;
                }
            }
            return -1;
        });
        assertTrue(row >= 0, "seed data has an enrollment in progress");
        String studentNo = onEdt(() -> cell(table, row, STUDENT_NO));
        String courseCode = onEdt(() -> cell(table, row, COURSE));
        Enrollment target = ctx.enrollments().search(new EnrollmentFilter(studentNo, null, null, null)).stream()
                .filter(e -> e.courseCode().equals(courseCode) && e.status() == EnrollmentStatus.ENROLLED)
                .findFirst().orElseThrow();
        int total = ctx.enrollments().count();

        prompts.answerConfirmations(false);
        onEdt(() -> button(view, "Drop").doClick());
        assertEquals(EnrollmentStatus.ENROLLED, ctx.enrollments().findById(target.id()).orElseThrow().status());
        assertTrue(prompts.confirmations.get(0).startsWith("Drop course: "));

        prompts.answerConfirmations(true);
        onEdt(() -> button(view, "Drop").doClick());
        assertEquals(EnrollmentStatus.DROPPED, ctx.enrollments().findById(target.id()).orElseThrow().status());
        onEdt(() -> {
            int r = table.getSelectedRow();
            assertTrue(r >= 0, "dropped row stays selected");
            assertEquals("Dropped", cell(table, r, REMARKS));
            assertFalse(button(view, "Drop").isEnabled());
            assertFalse(button(view, "Grade").isEnabled(), "dropped courses cannot be graded");
            assertTrue(button(view, "Remove").isEnabled());
        });

        prompts.answerConfirmations(false);
        onEdt(() -> pressKey(table, "DELETE"));
        assertEquals(total, ctx.enrollments().count());

        prompts.answerConfirmations(true);
        onEdt(() -> pressKey(table, "DELETE"));
        assertEquals(total - 1, ctx.enrollments().count());
        assertTrue(ctx.enrollments().findById(target.id()).isEmpty());
        onEdt(() -> {
            assertEquals(total - 1, table.getRowCount());
            assertTrue(labelTexts(frame.statusBar()).contains("Removed enrollment"));
        });
        assertTrue(prompts.confirmations.get(prompts.confirmations.size() - 1).startsWith("Remove enrollment: "));
    }

    @Test
    void termAndCourseFiltersWorkTogether() {
        Container view = open(Page.ENROLLMENTS);
        JTable table = table(view);
        int total = ctx.enrollments().count();
        onEdt(() -> assertEquals(total, table.getRowCount()));

        @SuppressWarnings("unchecked")
        JComboBox<Choice<String>> yearFilter = (JComboBox<Choice<String>>) findAll(view, JComboBox.class).get(0);
        @SuppressWarnings("unchecked")
        JComboBox<Choice<Course>> courseFilter = (JComboBox<Choice<Course>>) findAll(view, JComboBox.class).get(2);
        Course cpe201 = ctx.courses().findAll().stream().filter(c -> c.code().equals("CPE 201")).findFirst().orElseThrow();
        int expectedCourse = ctx.enrollments().search(new EnrollmentFilter(null, cpe201.id(), null, null)).size();
        assertTrue(expectedCourse > 0 && expectedCourse < total);

        onEdt(() -> select(courseFilter, c -> cpe201.equals(c.value())));
        onEdt(() -> {
            assertEquals(expectedCourse, table.getRowCount());
            for (int r = 0; r < table.getRowCount(); r++) {
                assertEquals("CPE 201", cell(table, r, COURSE));
            }
            assertTrue(labelTexts(view).contains("Showing " + expectedCourse + " of " + total + " enrollments"));
        });

        String year = ctx.enrollments().schoolYears().get(ctx.enrollments().schoolYears().size() - 1); // oldest
        int expectedBoth = ctx.enrollments().search(new EnrollmentFilter(null, cpe201.id(), year, null)).size();
        onEdt(() -> select(yearFilter, c -> year.equals(c.value())));
        onEdt(() -> assertEquals(expectedBoth, table.getRowCount()));
        assertTrue(prompts.dialogs.isEmpty(), "filtering never opens a dialog");
    }

    private static int rowOf(JTable table, int column, String value) {
        for (int r = 0; r < table.getRowCount(); r++) {
            if (cell(table, r, column).equals(value)) {
                return r;
            }
        }
        fail("no row with " + value + " in column " + column);
        return -1;
    }
}
