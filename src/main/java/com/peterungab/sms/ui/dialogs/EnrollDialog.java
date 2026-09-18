package com.peterungab.sms.ui.dialogs;

import com.peterungab.sms.AppContext;
import com.peterungab.sms.model.Course;
import com.peterungab.sms.model.Enrollment;
import com.peterungab.sms.model.Semester;
import com.peterungab.sms.model.Student;
import com.peterungab.sms.model.StudentFilter;
import com.peterungab.sms.model.StudentStatus;
import com.peterungab.sms.ui.Ui;
import com.peterungab.sms.ui.components.Choice;

import javax.swing.JComboBox;
import javax.swing.JTextField;
import java.awt.Window;

/** Enrolls an active student in a course for a school year and semester. */
public final class EnrollDialog extends FormDialog<Enrollment> {

    private final AppContext ctx;
    private final JComboBox<Choice<Student>> student = new JComboBox<>();
    private final JComboBox<Choice<Course>> course = new JComboBox<>();
    private final JTextField schoolYear = Ui.textField("YYYY-YYYY");
    private final JComboBox<Semester> semester = new JComboBox<>(Semester.values());

    public EnrollDialog(Window owner, AppContext ctx) {
        this(owner, ctx, null);
    }

    public EnrollDialog(Window owner, AppContext ctx, Integer preselectedStudentId) {
        super(owner, "Enroll Student", "Enroll student",
                "Only active students can be enrolled. A student can take a course once per term.", "Enroll");
        this.ctx = ctx;

        for (Student s : ctx.students().search(StudentFilter.all().withStatus(StudentStatus.ACTIVE))) {
            Choice<Student> choice = new Choice<>(s.sortableName() + "  ·  " + s.studentNumber()
                    + "  ·  " + s.program().code(), s);
            student.addItem(choice);
            if (preselectedStudentId != null && s.id().equals(preselectedStudentId)) {
                student.setSelectedItem(choice);
            }
        }
        for (Course c : ctx.courses().findAll()) {
            course.addItem(new Choice<>(c.code() + "  –  " + c.title() + "  (" + c.units() + " units)", c));
        }
        student.setMaximumRowCount(14);
        course.setMaximumRowCount(14);
        schoolYear.setText(ctx.enrollments().currentSchoolYear());
        semester.setSelectedItem(ctx.enrollments().currentSemester());

        addField("Student *", student, "student", true);
        addField("Course *", course, "course", true);
        addField("School year *", schoolYear, "schoolYear", false);
        addField("Semester *", semester, "semester", false);
        finishLayout(620);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Enrollment save() {
        Choice<Student> s = (Choice<Student>) student.getSelectedItem();
        Choice<Course> c = (Choice<Course>) course.getSelectedItem();
        return ctx.enrollments().enroll(
                s == null ? -1 : s.value().id(),
                c == null ? -1 : c.value().id(),
                schoolYear.getText(),
                (Semester) semester.getSelectedItem());
    }
}
