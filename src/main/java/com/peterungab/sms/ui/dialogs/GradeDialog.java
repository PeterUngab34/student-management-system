package com.peterungab.sms.ui.dialogs;

import com.peterungab.sms.AppContext;
import com.peterungab.sms.model.Enrollment;
import com.peterungab.sms.model.GradeScale;
import com.peterungab.sms.ui.Ui;
import com.peterungab.sms.ui.components.Choice;

import javax.swing.JComboBox;
import javax.swing.JTextField;
import java.awt.Window;
import java.math.BigDecimal;

/** Records, changes or clears the final grade of one enrollment. */
public final class GradeDialog extends FormDialog<Enrollment> {

    private final AppContext ctx;
    private final Enrollment enrollment;
    private final JComboBox<Choice<BigDecimal>> grade = new JComboBox<>();

    public GradeDialog(Window owner, AppContext ctx, Enrollment enrollment) {
        super(owner, "Record Grade", "Record grade",
                enrollment.studentName() + " · " + enrollment.courseCode() + " · " + enrollment.term(),
                "Save Grade");
        this.ctx = ctx;
        this.enrollment = enrollment;

        grade.addItem(Choice.all("No grade yet (in progress)"));
        for (BigDecimal g : GradeScale.VALID_GRADES) {
            Choice<BigDecimal> choice = new Choice<>(GradeScale.format(g) + "   –   " + GradeScale.describe(g), g);
            grade.addItem(choice);
            if (enrollment.grade() != null && enrollment.grade().compareTo(g) == 0) {
                grade.setSelectedItem(choice);
            }
        }
        grade.setMaximumRowCount(12);

        JTextField course = readOnly(enrollment.courseCode() + " – " + enrollment.courseTitle());
        JTextField units = readOnly(enrollment.units() + " units");
        addField("Course", course, "course", true);
        addField("Units", units, "units", false);
        addField("Final grade", grade, "grade", false);
        finishLayout(520);
        grade.requestFocusInWindow();
    }

    private static JTextField readOnly(String text) {
        JTextField field = Ui.textField(null);
        field.setText(text);
        field.setEditable(false);
        field.setFocusable(false);
        return field;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Enrollment save() {
        Choice<BigDecimal> choice = (Choice<BigDecimal>) grade.getSelectedItem();
        return ctx.enrollments().recordGrade(enrollment.id(), choice == null ? null : choice.value());
    }
}
