package com.peterungab.sms.ui.views;

import com.peterungab.sms.AppContext;
import com.peterungab.sms.model.GradeScale;
import com.peterungab.sms.model.Program;
import com.peterungab.sms.model.Student;
import com.peterungab.sms.model.StudentFilter;
import com.peterungab.sms.model.StudentStatus;
import com.peterungab.sms.service.BusinessRuleException;
import com.peterungab.sms.ui.Icons;
import com.peterungab.sms.ui.MainFrame;
import com.peterungab.sms.ui.Prompts;
import com.peterungab.sms.ui.Ui;
import com.peterungab.sms.ui.View;
import com.peterungab.sms.ui.components.Card;
import com.peterungab.sms.ui.components.Choice;
import com.peterungab.sms.ui.components.PillRenderer;
import com.peterungab.sms.ui.dialogs.StudentDialog;
import net.miginfocom.swing.MigLayout;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Student list with live search, filters, sortable columns and CRUD actions. */
public final class StudentsView extends JPanel implements View {

    private final AppContext ctx;
    private final MainFrame frame;

    private final StudentTableModel model = new StudentTableModel();
    private final JTable table = new JTable(model);
    private final JTextField search = Ui.searchField("Search name, student no. or e-mail  (Ctrl+F)");
    private final JComboBox<Choice<Program>> programFilter = new JComboBox<>();
    private final JComboBox<Choice<Integer>> yearFilter = new JComboBox<>();
    private final JComboBox<Choice<StudentStatus>> statusFilter = new JComboBox<>();
    private final JLabel countLabel = Ui.muted(" ", -1);
    private final JLabel subtitle = Ui.muted(" ", 1);
    private final JButton recordButton = Ui.ghostButton("Record", Icons.record(16));
    private final JButton editButton = Ui.ghostButton("Edit", Icons.edit(16));
    private final JButton deleteButton = Ui.ghostButton("Delete", Icons.trash(16));
    private final Timer searchDebounce = new Timer(220, e -> reload());
    private boolean loadingFilters;

    public StudentsView(AppContext ctx, MainFrame frame) {
        super(new MigLayout("fill, insets 26 30 22 30", "[grow,fill]", "[]18[]12[grow,fill]8[]"));
        this.ctx = ctx;
        this.frame = frame;
        searchDebounce.setRepeats(false);

        add(header(), "wrap");
        add(toolbar(), "wrap");
        add(tableCard(), "wrap");
        add(footer());

        installBehaviour();
        populateFilterChoices();
        updateActions();
    }

    // ---- layout --------------------------------------------------------------------------

    private JComponent header() {
        JPanel p = new JPanel(new MigLayout("insets 0, fillx", "[grow][][]", "[]2[]"));
        p.setOpaque(false);
        JButton export = Ui.button("Export CSV", Icons.export(16));
        export.setMnemonic('X');
        export.setToolTipText("Export the rows currently shown (Ctrl+E)");
        export.addActionListener(e -> exportCsv());
        JButton add = Ui.primaryButton("Add Student", Icons.plus(16));
        add.setMnemonic('A');
        add.setToolTipText("Add a new student (Ctrl+N)");
        add.addActionListener(e -> createNew());

        p.add(Ui.pageTitle("Students"), "");
        p.add(export, "spany 2, bottom");
        p.add(add, "spany 2, bottom, wrap");
        p.add(subtitle);
        return p;
    }

    private JComponent toolbar() {
        JPanel p = new JPanel(new MigLayout("insets 0, fillx, gap 10", "[grow,fill][][][][]", "[]"));
        p.setOpaque(false);
        JButton clear = Ui.ghostButton("Clear", Icons.close(14));
        clear.setToolTipText("Clear search and filters");
        clear.addActionListener(e -> clearFilters());
        p.add(search, "w 220:420:, h 34!");
        p.add(programFilter, "w 150:210:, h 34!");
        p.add(yearFilter, "w 100:120:, h 34!");
        p.add(statusFilter, "w 110:130:, h 34!");
        p.add(clear, "h 34!");
        return p;
    }

    private JComponent footer() {
        JPanel p = new JPanel(new MigLayout("insets 0, fillx, gap 6", "[grow][][][]", "[]"));
        p.setOpaque(false);
        p.add(countLabel);
        p.add(recordButton);
        p.add(editButton);
        p.add(deleteButton);
        return p;
    }

    private JComponent tableCard() {
        Ui.setupTable(table);
        TableRowSorter<StudentTableModel> sorter = new TableRowSorter<>(model);
        table.setRowSorter(sorter);
        table.getColumnModel().getColumn(StudentTableModel.STATUS).setCellRenderer(PillRenderer.forStudentStatus());
        table.getColumnModel().getColumn(StudentTableModel.YEAR).setCellRenderer(Ui.alignedRenderer(SwingConstants.CENTER));
        table.getColumnModel().getColumn(StudentTableModel.GPA).setCellRenderer(Ui.alignedRenderer(SwingConstants.RIGHT));
        Ui.columnWidth(table, StudentTableModel.NUMBER, 110, 130);
        Ui.columnWidth(table, StudentTableModel.NAME, 210, 0);
        Ui.columnWidth(table, StudentTableModel.PROGRAM, 90, 110);
        Ui.columnWidth(table, StudentTableModel.YEAR, 60, 70);
        Ui.columnWidth(table, StudentTableModel.EMAIL, 280, 0);
        Ui.columnWidth(table, StudentTableModel.STATUS, 110, 130);
        Ui.columnWidth(table, StudentTableModel.GPA, 70, 90);

        Card card = new Card(new MigLayout("fill, insets 0", "[grow,fill]", "[grow,fill]"));
        card.setBorder(javax.swing.BorderFactory.createEmptyBorder(6, 6, 6, 6));
        card.add(Ui.scroll(table));
        return card;
    }

    // ---- behaviour -----------------------------------------------------------------------

    private void installBehaviour() {
        search.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                searchDebounce.restart();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                searchDebounce.restart();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                searchDebounce.restart();
            }
        });
        programFilter.addActionListener(e -> filtersChanged());
        yearFilter.addActionListener(e -> filtersChanged());
        statusFilter.addActionListener(e -> filtersChanged());

        table.getSelectionModel().addListSelectionListener(e -> updateActions());
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && table.rowAtPoint(e.getPoint()) >= 0) {
                    editSelected();
                }
            }
        });
        recordButton.addActionListener(e -> openRecord());
        editButton.addActionListener(e -> editSelected());
        deleteButton.addActionListener(e -> deleteSelected());

        recordButton.setToolTipText("Academic record & GPA (Ctrl+R)");
        editButton.setToolTipText("Edit student (Enter)");
        deleteButton.setToolTipText("Delete student (Delete)");

        bindTableKey("ENTER", this::editSelected);
        bindTableKey("DELETE", this::deleteSelected);
        bindTableKey("ctrl R", this::openRecord);
    }

    private void bindTableKey(String key, Runnable action) {
        table.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                .put(javax.swing.KeyStroke.getKeyStroke(key), key);
        table.getActionMap().put(key, new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                action.run();
            }
        });
    }

    private void populateFilterChoices() {
        loadingFilters = true;
        Object selectedProgram = programFilter.getSelectedItem();
        programFilter.removeAllItems();
        programFilter.addItem(Choice.all("All programs"));
        for (Program p : ctx.students().programs()) {
            programFilter.addItem(new Choice<>(p.code() + " – " + p.name().replace("BS ", ""), p));
        }
        if (selectedProgram != null) {
            programFilter.setSelectedItem(selectedProgram);
        }
        if (yearFilter.getItemCount() == 0) {
            yearFilter.addItem(Choice.all("All years"));
            for (int y = 1; y <= 5; y++) {
                yearFilter.addItem(new Choice<>(Student.yearLevelLabel(y), y));
            }
            statusFilter.addItem(Choice.all("Any status"));
            for (StudentStatus s : StudentStatus.values()) {
                statusFilter.addItem(Choice.of(s));
            }
        }
        loadingFilters = false;
    }

    private void filtersChanged() {
        if (!loadingFilters) {
            reload();
        }
    }

    private void clearFilters() {
        loadingFilters = true;
        search.setText("");
        programFilter.setSelectedIndex(0);
        yearFilter.setSelectedIndex(0);
        statusFilter.setSelectedIndex(0);
        loadingFilters = false;
        searchDebounce.stop();
        reload();
    }

    private StudentFilter currentFilter() {
        return StudentFilter.all()
                .withKeyword(search.getText())
                .withProgramId(Optional.ofNullable(selected(programFilter)).map(Program::id).orElse(null))
                .withYearLevel(selected(yearFilter))
                .withStatus(selected(statusFilter));
    }

    private static <T> T selected(JComboBox<Choice<T>> combo) {
        @SuppressWarnings("unchecked")
        Choice<T> choice = (Choice<T>) combo.getSelectedItem();
        return choice == null ? null : choice.value();
    }

    @Override
    public void refresh() {
        populateFilterChoices();
        reload();
    }

    private void reload() {
        Integer selectedId = selectedStudent().map(Student::id).orElse(null);
        List<Student> students = ctx.students().search(currentFilter());
        Map<Integer, BigDecimal> gpas = ctx.enrollments().gpaByStudent();
        model.setData(students, gpas);
        int total = ctx.students().count();
        subtitle.setText(total + " students enrolled across " + (programFilter.getItemCount() - 1) + " programs");
        countLabel.setText(students.size() == total
                ? "Showing all " + total + " students · double-click a row to edit · click a header to sort"
                : "Showing " + students.size() + " of " + total + " students");
        if (selectedId != null) {
            selectById(selectedId);
        }
        updateActions();
    }

    private void selectById(int id) {
        for (int i = 0; i < model.getRowCount(); i++) {
            if (model.get(i).id() == id) {
                int viewRow = table.convertRowIndexToView(i);
                if (viewRow >= 0) {
                    table.setRowSelectionInterval(viewRow, viewRow);
                    table.scrollRectToVisible(table.getCellRect(viewRow, 0, true));
                }
                return;
            }
        }
    }

    private Optional<Student> selectedStudent() {
        int viewRow = table.getSelectedRow();
        if (viewRow < 0) {
            return Optional.empty();
        }
        return Optional.of(model.get(table.convertRowIndexToModel(viewRow)));
    }

    private void updateActions() {
        boolean hasSelection = table.getSelectedRow() >= 0;
        recordButton.setEnabled(hasSelection);
        editButton.setEnabled(hasSelection);
        deleteButton.setEnabled(hasSelection);
    }

    // ---- actions -------------------------------------------------------------------------

    @Override
    public void createNew() {
        StudentDialog dialog = new StudentDialog(frame, ctx, null);
        Prompts.showModal(dialog);
        dialog.result().ifPresent(saved -> {
            reload();
            selectById(saved.id());
            frame.statusBar().flash("Added " + saved.fullName() + " (" + saved.studentNumber() + ")");
        });
    }

    private void editSelected() {
        selectedStudent().ifPresent(student -> {
            StudentDialog dialog = new StudentDialog(frame, ctx, student);
            Prompts.showModal(dialog);
            dialog.result().ifPresent(saved -> {
                reload();
                selectById(saved.id());
                frame.statusBar().flash("Saved changes to " + saved.fullName());
            });
        });
    }

    private void deleteSelected() {
        selectedStudent().ifPresent(student -> {
            int enrollments = ctx.enrollments().academicRecord(student.id()).enrollments().size();
            String message = "<html>Delete <b>" + student.fullName() + "</b> (" + student.studentNumber() + ")?"
                    + (enrollments > 0 ? "<br>Their " + enrollments + " enrollment record(s) and grades will also be removed." : "")
                    + "<br><br>This cannot be undone.</html>";
            if (!Ui.confirm(frame, "Delete student", message, "Delete")) {
                return;
            }
            try {
                ctx.students().delete(student.id());
                reload();
                frame.statusBar().flash("Deleted " + student.fullName());
            } catch (BusinessRuleException ex) {
                Ui.showError(frame, "Cannot delete student", ex.getMessage());
                reload();
            }
        });
    }

    private void openRecord() {
        selectedStudent().ifPresent(s -> frame.openStudentRecord(s.id()));
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
        Optional<Path> target = Exports.chooseCsvFile(frame, "students");
        if (target.isEmpty()) {
            return;
        }
        List<List<String>> rows = new ArrayList<>();
        for (int viewRow = 0; viewRow < table.getRowCount(); viewRow++) {
            int m = table.convertRowIndexToModel(viewRow);
            Student s = model.get(m);
            BigDecimal gpa = model.gpa(m);
            rows.add(List.of(s.studentNumber(), s.lastName(), s.firstName(), s.program().code(),
                    String.valueOf(s.yearLevel()), s.email(), s.phone() == null ? "" : s.phone(),
                    s.birthDate() == null ? "" : s.birthDate().toString(), s.status().label(),
                    gpa == null ? "" : GradeScale.format(gpa)));
        }
        Exports.write(frame, target.get(), List.of("Student No.", "Last Name", "First Name", "Program", "Year Level",
                "Email", "Phone", "Birth Date", "Status", "GPA"), rows);
    }

    // ---- table model ---------------------------------------------------------------------

    static final class StudentTableModel extends AbstractTableModel {
        static final int NUMBER = 0;
        static final int NAME = 1;
        static final int PROGRAM = 2;
        static final int YEAR = 3;
        static final int EMAIL = 4;
        static final int STATUS = 5;
        static final int GPA = 6;
        private static final String[] COLUMNS = {"Student No.", "Name", "Program", "Year", "E-mail", "Status", "GPA"};

        private List<Student> rows = List.of();
        private Map<Integer, BigDecimal> gpas = Map.of();

        void setData(List<Student> rows, Map<Integer, BigDecimal> gpas) {
            this.rows = rows;
            this.gpas = gpas;
            fireTableDataChanged();
        }

        Student get(int row) {
            return rows.get(row);
        }

        BigDecimal gpa(int row) {
            return gpas.get(rows.get(row).id());
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
                case YEAR -> Integer.class;
                case STATUS -> StudentStatus.class;
                case GPA -> GpaValue.class;
                default -> String.class;
            };
        }

        @Override
        public Object getValueAt(int row, int column) {
            Student s = rows.get(row);
            return switch (column) {
                case NUMBER -> s.studentNumber();
                case NAME -> s.sortableName();
                case PROGRAM -> s.program().code();
                case YEAR -> s.yearLevel();
                case EMAIL -> s.email();
                case STATUS -> s.status();
                case GPA -> new GpaValue(gpas.get(s.id()));
                default -> throw new IllegalArgumentException("column " + column);
            };
        }
    }

    /** Sortable GPA cell; students without grades sort last and show a dash. */
    record GpaValue(BigDecimal gpa) implements Comparable<GpaValue> {
        @Override
        public int compareTo(GpaValue o) {
            if (gpa == null || o.gpa == null) {
                return gpa == o.gpa ? 0 : gpa == null ? 1 : -1;
            }
            return gpa.compareTo(o.gpa);
        }

        @Override
        public String toString() {
            return GradeScale.format(gpa);
        }
    }
}
