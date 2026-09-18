package com.peterungab.sms.ui.dialogs;

import com.peterungab.sms.AppContext;
import com.peterungab.sms.model.Course;
import com.peterungab.sms.service.CourseService;
import com.peterungab.sms.ui.Ui;

import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import java.awt.Window;

/** Add / edit a course. */
public final class CourseDialog extends FormDialog<Course> {

    private final AppContext ctx;
    private final Course original;
    private final JTextField code = Ui.textField("e.g. CPE 301");
    private final JSpinner units = new JSpinner(new SpinnerNumberModel(3, CourseService.MIN_UNITS, CourseService.MAX_UNITS, 1));
    private final JTextField title = Ui.textField("e.g. Microprocessors and Microcontrollers");
    private final JTextField description = Ui.textField("Short description (optional)");

    public CourseDialog(Window owner, AppContext ctx, Course course) {
        super(owner, course == null ? "Add Course" : "Edit Course",
                course == null ? "Add course" : "Edit course",
                course == null ? "Add a course to the catalogue. Fields marked * are required."
                        : course.code() + " · " + course.title(),
                course == null ? "Add Course" : "Save Changes");
        this.ctx = ctx;
        this.original = course;

        addField("Course code *", code, "code", false);
        addField("Units *", units, "units", false);
        addField("Title *", title, "title", true);
        addField("Description", description, "description", true);

        if (course != null) {
            code.setText(course.code());
            units.setValue(course.units());
            title.setText(course.title());
            description.setText(course.description() == null ? "" : course.description());
        }
        finishLayout(560);
    }

    @Override
    protected Course save() {
        Course c = new Course(original == null ? null : original.id(), code.getText(), title.getText(),
                (Integer) units.getValue(), description.getText());
        return original == null ? ctx.courses().create(c) : ctx.courses().update(c);
    }
}
