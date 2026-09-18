package com.peterungab.sms.ui.dialogs;

import com.peterungab.sms.AppContext;
import com.peterungab.sms.model.Program;
import com.peterungab.sms.model.Student;
import com.peterungab.sms.model.StudentStatus;
import com.peterungab.sms.ui.Ui;
import com.peterungab.sms.ui.components.Choice;

import javax.swing.JComboBox;
import javax.swing.JTextField;
import java.awt.Window;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Map;

/** Add / edit a student. All business validation happens in {@code StudentService}. */
public final class StudentDialog extends FormDialog<Student> {

    private final AppContext ctx;
    private final Student original;

    private final JTextField studentNumber = Ui.textField("2026-00001");
    private final JComboBox<StudentStatus> status = new JComboBox<>(StudentStatus.values());
    private final JTextField firstName = Ui.textField(null);
    private final JTextField lastName = Ui.textField(null);
    private final JTextField email = Ui.textField("name@student.example.edu.ph");
    private final JTextField phone = Ui.textField("09171234567 (optional)");
    private final JTextField birthDate = Ui.textField("YYYY-MM-DD (optional)");
    private final JComboBox<Program> program = new JComboBox<>();
    private final JComboBox<Choice<Integer>> yearLevel = new JComboBox<>();

    public StudentDialog(Window owner, AppContext ctx, Student student) {
        super(owner, student == null ? "Add Student" : "Edit Student",
                student == null ? "Add student" : "Edit student",
                student == null ? "Create a new student record. Fields marked * are required."
                        : student.fullName() + " · " + student.studentNumber(),
                student == null ? "Add Student" : "Save Changes");
        this.ctx = ctx;
        this.original = student;

        ctx.students().programs().forEach(program::addItem);
        for (int y = 1; y <= 5; y++) {
            yearLevel.addItem(new Choice<>(Student.yearLevelLabel(y), y));
        }

        addField("Student number *", studentNumber, "studentNumber", false);
        addField("Status *", status, "status", false);
        addField("First name *", firstName, "firstName", false);
        addField("Last name *", lastName, "lastName", false);
        addField("E-mail *", email, "email", true);
        addField("Mobile number", phone, "phone", false);
        addField("Birth date", birthDate, "birthDate", false);
        addField("Program *", program, "program", false);
        addField("Year level *", yearLevel, "yearLevel", false);

        if (student == null) {
            studentNumber.setText(ctx.students().suggestNextStudentNumber());
            status.setSelectedItem(StudentStatus.ACTIVE);
        } else {
            studentNumber.setText(student.studentNumber());
            status.setSelectedItem(student.status());
            firstName.setText(student.firstName());
            lastName.setText(student.lastName());
            email.setText(student.email());
            phone.setText(student.phone() == null ? "" : student.phone());
            birthDate.setText(student.birthDate() == null ? "" : student.birthDate().toString());
            for (int i = 0; i < program.getItemCount(); i++) {
                if (program.getItemAt(i).id() == student.program().id()) {
                    program.setSelectedIndex(i);
                }
            }
            yearLevel.setSelectedIndex(Math.max(0, Math.min(4, student.yearLevel() - 1)));
        }
        finishLayout(600);
        (student == null ? firstName : studentNumber).requestFocusInWindow();
    }

    @Override
    protected void validateInput(Map<String, String> errors) {
        String text = birthDate.getText().trim();
        if (!text.isEmpty()) {
            try {
                LocalDate.parse(text);
            } catch (DateTimeParseException e) {
                errors.put("birthDate", "Use the format YYYY-MM-DD, e.g. 2005-06-18.");
            }
        }
    }

    @Override
    protected Student save() {
        String bd = birthDate.getText().trim();
        @SuppressWarnings("unchecked")
        Choice<Integer> year = (Choice<Integer>) yearLevel.getSelectedItem();
        Student s = new Student(
                original == null ? null : original.id(),
                studentNumber.getText(),
                firstName.getText(),
                lastName.getText(),
                email.getText(),
                phone.getText(),
                bd.isEmpty() ? null : LocalDate.parse(bd),
                (Program) program.getSelectedItem(),
                year == null ? 0 : year.value(),
                (StudentStatus) status.getSelectedItem());
        return original == null ? ctx.students().create(s) : ctx.students().update(s);
    }
}
