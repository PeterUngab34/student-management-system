package com.peterungab.sms.ui.views;

import com.peterungab.sms.AppContext;
import com.peterungab.sms.model.Course;
import com.peterungab.sms.model.Enrollment;
import com.peterungab.sms.model.EnrollmentFilter;
import com.peterungab.sms.model.EnrollmentStatus;
import com.peterungab.sms.model.GradeScale;
import com.peterungab.sms.model.Semester;
import com.peterungab.sms.service.BusinessRuleException;
import com.peterungab.sms.ui.Icons;
import com.peterungab.sms.ui.MainFrame;
import com.peterungab.sms.ui.Prompts;
import com.peterungab.sms.ui.Theme;
import com.peterungab.sms.ui.Ui;
import com.peterungab.sms.ui.View;
import com.peterungab.sms.ui.components.Card;
import com.peterungab.sms.ui.components.Choice;
import com.peterungab.sms.ui.components.PillRenderer;
import com.peterungab.sms.ui.dialogs.EnrollDialog;
import com.peterungab.sms.ui.dialogs.GradeDialog;
import net.miginfocom.swing.MigLayout;

import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableRowSorter;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Enrollment list with term/course filters, grade entry and drop/remove actions. */
public final class EnrollmentsView extends JPanel implements View {

    private final AppContext ctx;
    private final MainFrame frame;
    private final EnrollmentTableModel model = new EnrollmentTableModel();
    private final JTable table = new JTable(model);
    private final JTextField search = Ui.searchField("Search student or course  (Ctrl+F)");
    private final JComboBox<Choice<String>> yearFilter = new JComboBox<>();
    private final JComboBox<Choice<Semester>> semesterFilter = new JComboBox<>();
    private final JComboBox<Choice<Course>> courseFilter = new JComboBox<>();
    private final JLabel subtitle = Ui.muted(" ", 1);
    private final JLabel countLabel = Ui.muted(" ", -1);
    private final JButton recordButton = Ui.ghostButton("Record", Icons.record(16));
    private final JButton gradeButton = Ui.ghostButton("Grade", Icons.grade(16));
    private final JButton dropButton = Ui.ghostButton("Drop", Icons.drop(16));
    private final JButton removeButton = Ui.ghostButton("Remove", Icons.trash(16));
    private final Timer debounce = new Timer(220, e -> reload());
    private boolean loadingFilters;

    public EnrollmentsView(AppContext ctx, MainFrame frame) {
        super(new MigLayout("fill, insets 26 30 22 30", "[grow,fill]", "[]18[]12[grow,fill]8[]"));
        this.ctx = ctx;
        this.frame = frame;
        debounce.setRepeats(false);

        add(header(), "wrap");
        add(toolbar(), "wrap");
        add(tableCard(), "wrap");
        add(footer());
        installBehaviour();
        updateActions();
    }

    private JComponent header() {
        JPanel p = new JPanel(new MigLayout("insets 0, fillx", "[grow][][]", "[]2[]"));
        p.setOpaque(false);
        JButton export = Ui.button("Export CSV", Icons.export(16));
        export.addActionListener(e -> exportCsv());
        JButton enroll = Ui.primaryButton("Enroll Student", Icons.plus(16));
        enroll.setMnemonic('N');
        enroll.setToolTipText("Enroll a student in a course (Ctrl+N)");
        enroll.addActionListener(e -> createNew());
        p.add(Ui.pageTitle("Enrollments & Grades"));
        p.add(export, "spany 2, bottom");
        p.add(enroll, "spany 2, bottom, wrap");
        p.add(subtitle);
        return p;
    }

    private JComponent toolbar() {
        JPanel p = new JPanel(new MigLayout("insets 0, fillx, gap 10", "[grow,fill][][][]", "[]"));
        p.setOpaque(false);
        p.add(search, "w 220:420:, h 34!");
        p.add(yearFilter, "w 130:160:, h 34!");
        p.add(semesterFilter, "w 130:150:, h 34!");
        p.add(courseFilter, "w 120:150:, h 34!");
        return p;
    }

    private JComponent footer() {
        JPanel p = new JPanel(new MigLayout("insets 0, fillx, gap 6", "[grow][][][][]", "[]"));
        p.setOpaque(false);
        p.add(countLabel);
        p.add(recordButton);
        p.add(gradeButton);
        p.add(dropButton);
        p.add(removeButton);
        return p;
    }

    private JComponent tableCard() {
        Ui.setupTable(table);
        table.setRowSorter(new TableRowSorter<>(model));
        table.getColumnModel().getColumn(EnrollmentTableModel.UNITS).setCellRenderer(Ui.alignedRenderer(SwingConstants.CENTER));
        table.getColumnModel().getColumn(EnrollmentTableModel.GRADE).setCellRenderer(new GradeRenderer());
        table.getColumnModel().getColumn(EnrollmentTableModel.STATUS).setCellRenderer(PillRenderer.forEnrollmentStatus());
        Ui.columnWidth(table, 0, 118, 128);
        Ui.columnWidth(table, 1, 170, 0);
        Ui.columnWidth(table, 2, 90, 100);
        Ui.columnWidth(table, 3, 240, 0);
        Ui.columnWidth(table, 4, 70, 76);
        Ui.columnWidth(table, 5, 140, 160);
        Ui.columnWidth(table, 6, 65, 80);
        Ui.columnWidth(table, 7, 110, 130);
        Ui.columnWidth(table, 8, 100, 130);
        Card card = new Card(new MigLayout("fill, insets 0", "[grow,fill]", "[grow,fill]"));
        card.setBorder(javax.swing.BorderFactory.createEmptyBorder(6, 6, 6, 6));
        card.add(Ui.scroll(table));
        return card;
    }

    private void installBehaviour() {
        search.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                debounce.restart();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                debounce.restart();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                debounce.restart();
            }
        });
        yearFilter.addActionListener(e -> filtersChanged());
        semesterFilter.addActionListener(e -> filtersChanged());
        courseFilter.addActionListener(e -> filtersChanged());
        table.getSelectionModel().addListSelectionListener(e -> updateActions());
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && table.rowAtPoint(e.getPoint()) >= 0) {
                    gradeSelected();
                }
            }
        });
        recordButton.addActionListener(e -> openRecord());
        gradeButton.addActionListener(e -> gradeSelected());
        dropButton.addActionListener(e -> dropSelected());
        removeButton.addActionListener(e -> removeSelected());
        recordButton.setToolTipText("Student's academic record (Ctrl+R)");
        gradeButton.setToolTipText("Record or change the final grade (Ctrl+G / Enter)");
        dropButton.setToolTipText("Mark as dropped");
        removeButton.setToolTipText("Delete this enrollment (Delete)");
        bind("ENTER", this::gradeSelected);
        bind("ctrl G", this::gradeSelected);
        bind("ctrl R", this::openRecord);
        bind("DELETE", this::removeSelected);
    }

    private void bind(String key, Runnable action) {
        table.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(key), key);
        table.getActionMap().put(key, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                action.run();
            }
        });
    }

    private void populateFilterChoices() {
        loadingFilters = true;
        Object year = yearFilter.getSelectedItem();
        Object course = courseFilter.getSelectedItem();
        yearFilter.removeAllItems();
        yearFilter.addItem(Choice.all("All school years"));
        for (String sy : ctx.enrollments().schoolYears()) {
            yearFilter.addItem(new Choice<>("SY " + sy, sy));
        }
        if (year != null) {
            yearFilter.setSelectedItem(year);
        }
        if (semesterFilter.getItemCount() == 0) {
            semesterFilter.addItem(Choice.all("All semesters"));
            for (Semester s : Semester.values()) {
                semesterFilter.addItem(Choice.of(s));
            }
        }
        courseFilter.removeAllItems();
        courseFilter.addItem(Choice.all("All courses"));
        for (Course c : ctx.courses().findAll()) {
            courseFilter.addItem(new Choice<>(c.code(), c));
        }
        if (course != null) {
            courseFilter.setSelectedItem(course);
        }
        loadingFilters = false;
    }

    private void filtersChanged() {
        if (!loadingFilters) {
            reload();
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T selected(JComboBox<Choice<T>> combo) {
        Choice<T> c = (Choice<T>) combo.getSelectedItem();
        return c == null ? null : c.value();
    }

    @Override
    public void refresh() {
        populateFilterChoices();
        reload();
    }

    private void reload() {
        Integer selectedId = selected().map(Enrollment::id).orElse(null);
        Course course = selected(courseFilter);
        EnrollmentFilter filter = new EnrollmentFilter(search.getText(), course == null ? null : course.id(),
                selected(yearFilter), selected(semesterFilter));
        List<Enrollment> rows = ctx.enrollments().search(filter);
        model.setData(rows);
        int total = ctx.enrollments().count();
        subtitle.setText("Current term: " + ctx.enrollments().currentSemester().label() + ", SY "
                + ctx.enrollments().currentSchoolYear() + " · grades on the 1.00 – 5.00 scale");
        countLabel.setText(rows.size() == total ? "Showing all " + total + " enrollments · double-click a row to record a grade"
                : "Showing " + rows.size() + " of " + total + " enrollments");
        if (selectedId != null) {
            selectById(selectedId);
        }
        updateActions();
    }

    private void selectById(int id) {
        for (int i = 0; i < model.getRowCount(); i++) {
            if (model.get(i).id() == id) {
                int v = table.convertRowIndexToView(i);
                table.setRowSelectionInterval(v, v);
                table.scrollRectToVisible(table.getCellRect(v, 0, true));
                return;
            }
        }
    }

    private Optional<Enrollment> selected() {
        int v = table.getSelectedRow();
        return v < 0 ? Optional.empty() : Optional.of(model.get(table.convertRowIndexToModel(v)));
    }

    private void updateActions() {
        Optional<Enrollment> e = selected();
        recordButton.setEnabled(e.isPresent());
        gradeButton.setEnabled(e.isPresent() && e.get().status() != EnrollmentStatus.DROPPED);
        dropButton.setEnabled(e.isPresent() && e.get().status() == EnrollmentStatus.ENROLLED);
        removeButton.setEnabled(e.isPresent());
    }

    @Override
    public void createNew() {
        EnrollDialog dialog = new EnrollDialog(frame, ctx);
        Prompts.showModal(dialog);
        dialog.result().ifPresent(saved -> {
            populateFilterChoices();
            reload();
            selectById(saved.id());
            frame.statusBar().flash("Enrolled " + saved.studentName() + " in " + saved.courseCode());
        });
    }

    private void gradeSelected() {
        selected().ifPresent(enrollment -> {
            if (enrollment.status() == EnrollmentStatus.DROPPED) {
                Ui.showInfo(frame, "Dropped course", "A dropped course cannot be graded.");
                return;
            }
            GradeDialog dialog = new GradeDialog(frame, ctx, enrollment);
            Prompts.showModal(dialog);
            dialog.result().ifPresent(saved -> {
                reload();
                selectById(saved.id());
                frame.statusBar().flash(saved.grade() == null
                        ? "Cleared grade of " + saved.studentName() + " in " + saved.courseCode()
                        : "Recorded " + GradeScale.format(saved.grade()) + " for " + saved.studentName() + " in " + saved.courseCode());
            });
        });
    }

    private void dropSelected() {
        selected().ifPresent(e -> {
            if (!Ui.confirm(frame, "Drop course", "<html>Mark <b>" + e.courseCode() + "</b> as dropped for <b>"
                    + e.studentName() + "</b> (" + e.term() + ")?</html>", "Drop")) {
                return;
            }
            try {
                ctx.enrollments().drop(e.id());
                reload();
                frame.statusBar().flash(e.studentName() + " dropped " + e.courseCode());
            } catch (BusinessRuleException ex) {
                Ui.showError(frame, "Cannot drop course", ex.getMessage());
            }
        });
    }

    private void removeSelected() {
        selected().ifPresent(e -> {
            if (!Ui.confirm(frame, "Remove enrollment", "<html>Permanently delete the enrollment of <b>"
                    + e.studentName() + "</b> in <b>" + e.courseCode() + "</b> (" + e.term() + ")"
                    + (e.grade() != null ? " including the grade " + GradeScale.format(e.grade()) : "")
                    + "?<br><br>This cannot be undone.</html>", "Delete")) {
                return;
            }
            try {
                ctx.enrollments().delete(e.id());
                reload();
                frame.statusBar().flash("Removed enrollment");
            } catch (BusinessRuleException ex) {
                Ui.showError(frame, "Cannot remove enrollment", ex.getMessage());
                reload();
            }
        });
    }

    private void openRecord() {
        selected().ifPresent(e -> frame.openStudentRecord(e.studentId()));
    }

    @Override
    public void focusSearch() {
        search.requestFocusInWindow();
        search.selectAll();
    }

    @Override
    public boolean supportsExport() {
        return true;
    }

    @Override
    public void exportCsv() {
        Optional<Path> target = Exports.chooseCsvFile(frame, "enrollments");
        if (target.isEmpty()) {
            return;
        }
        List<List<String>> rows = new ArrayList<>();
        for (int v = 0; v < table.getRowCount(); v++) {
            Enrollment e = model.get(table.convertRowIndexToModel(v));
            rows.add(List.of(e.studentNumber(), e.studentName(), e.courseCode(), e.courseTitle(),
                    String.valueOf(e.units()), e.schoolYear(), e.semester().label(),
                    e.grade() == null ? "" : GradeScale.format(e.grade()), e.status().label(), e.remarks()));
        }
        Exports.write(frame, target.get(), List.of("Student No.", "Student", "Course", "Title", "Units",
                "School Year", "Semester", "Grade", "Status", "Remarks"), rows);
    }

    /** Right-aligned grade; failing grades in red. */
    private static final class GradeRenderer extends DefaultTableCellRenderer {
        GradeRenderer() {
            setHorizontalAlignment(SwingConstants.RIGHT);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                       boolean hasFocus, int row, int column) {
            GradeValue g = (GradeValue) value;
            setForeground(null); // reset the colour left over from the previous cell
            super.getTableCellRendererComponent(table, g, isSelected, hasFocus, row, column);
            if (!isSelected && g.grade() != null && !GradeScale.isPassing(g.grade())) {
                setForeground(Theme.danger());
            } else if (!isSelected && g.grade() == null) {
                setForeground(Theme.mutedText());
            }
            return this;
        }
    }

    record GradeValue(BigDecimal grade) implements Comparable<GradeValue> {
        @Override
        public int compareTo(GradeValue o) {
            if (grade == null || o.grade == null) {
                return grade == null && o.grade == null ? 0 : grade == null ? 1 : -1;
            }
            return grade.compareTo(o.grade);
        }

        @Override
        public String toString() {
            return GradeScale.format(grade);
        }
    }

    static final class EnrollmentTableModel extends AbstractTableModel {
        static final int UNITS = 4;
        static final int GRADE = 6;
        static final int STATUS = 7;
        private static final String[] COLUMNS =
                {"Student No.", "Student", "Course", "Title", "Units", "Term", "Grade", "Status", "Remarks"};
        private List<Enrollment> rows = List.of();

        void setData(List<Enrollment> rows) {
            this.rows = rows;
            fireTableDataChanged();
        }

        Enrollment get(int row) {
            return rows.get(row);
        }

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return COLUMNS.length;
        }

        @Override
        public String getColumnName(int column) {
            return COLUMNS[column];
        }

        @Override
        public Class<?> getColumnClass(int column) {
            return switch (column) {
                case UNITS -> Integer.class;
                case GRADE -> GradeValue.class;
                case STATUS -> EnrollmentStatus.class;
                case 5 -> TermValue.class;
                default -> String.class;
            };
        }

        @Override
        public Object getValueAt(int row, int column) {
            Enrollment e = rows.get(row);
            return switch (column) {
                case 0 -> e.studentNumber();
                case 1 -> e.studentName();
                case 2 -> e.courseCode();
                case 3 -> e.courseTitle();
                case UNITS -> e.units();
                case 5 -> new TermValue(e);
                case GRADE -> new GradeValue(e.grade());
                case STATUS -> e.status();
                case 8 -> e.remarks();
                default -> throw new IllegalArgumentException("column " + column);
            };
        }
    }

    /** Displays "1st Sem 2025-2026" but sorts chronologically. */
    record TermValue(Enrollment e) implements Comparable<TermValue> {
        @Override
        public int compareTo(TermValue o) {
            return e.termKey().compareTo(o.e.termKey());
        }

        @Override
        public String toString() {
            return e.term();
        }
    }
}
