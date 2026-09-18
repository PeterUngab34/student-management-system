package com.peterungab.sms.ui.views;

import com.peterungab.sms.AppContext;
import com.peterungab.sms.model.Course;
import com.peterungab.sms.service.BusinessRuleException;
import com.peterungab.sms.ui.Icons;
import com.peterungab.sms.ui.MainFrame;
import com.peterungab.sms.ui.Ui;
import com.peterungab.sms.ui.View;
import com.peterungab.sms.ui.components.Card;
import com.peterungab.sms.ui.dialogs.CourseDialog;
import net.miginfocom.swing.MigLayout;

import javax.swing.AbstractAction;
import javax.swing.JButton;
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
import javax.swing.table.TableRowSorter;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Course catalogue CRUD. */
public final class CoursesView extends JPanel implements View {

    private final AppContext ctx;
    private final MainFrame frame;
    private final CourseTableModel model = new CourseTableModel();
    private final JTable table = new JTable(model);
    private final JTextField search = Ui.searchField("Search code or title  (Ctrl+F)");
    private final JLabel subtitle = Ui.muted(" ", 1);
    private final JLabel countLabel = Ui.muted(" ", -1);
    private final JButton editButton = Ui.ghostButton("Edit", Icons.edit(16));
    private final JButton deleteButton = Ui.ghostButton("Delete", Icons.trash(16));
    private final Timer debounce = new Timer(200, e -> reload());

    public CoursesView(AppContext ctx, MainFrame frame) {
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
        JButton add = Ui.primaryButton("Add Course", Icons.plus(16));
        add.setMnemonic('A');
        add.setToolTipText("Add a new course (Ctrl+N)");
        add.addActionListener(e -> createNew());
        p.add(Ui.pageTitle("Courses"));
        p.add(export, "spany 2, bottom");
        p.add(add, "spany 2, bottom, wrap");
        p.add(subtitle);
        return p;
    }

    private JComponent toolbar() {
        JPanel p = new JPanel(new MigLayout("insets 0, fillx, gap 10", "[]push", "[]"));
        p.setOpaque(false);
        p.add(search, "w 420!, h 34!");
        return p;
    }

    private JComponent footer() {
        JPanel p = new JPanel(new MigLayout("insets 0, fillx, gap 6", "[grow][][]", "[]"));
        p.setOpaque(false);
        p.add(countLabel);
        p.add(editButton);
        p.add(deleteButton);
        return p;
    }

    private JComponent tableCard() {
        Ui.setupTable(table);
        table.setRowSorter(new TableRowSorter<>(model));
        table.getColumnModel().getColumn(2).setCellRenderer(Ui.alignedRenderer(SwingConstants.CENTER));
        table.getColumnModel().getColumn(3).setCellRenderer(Ui.alignedRenderer(SwingConstants.CENTER));
        Ui.columnWidth(table, 0, 100, 120);
        Ui.columnWidth(table, 1, 280, 0);
        Ui.columnWidth(table, 2, 60, 70);
        Ui.columnWidth(table, 3, 100, 110);
        Ui.columnWidth(table, 4, 420, 0);
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
        table.getSelectionModel().addListSelectionListener(e -> updateActions());
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && table.rowAtPoint(e.getPoint()) >= 0) {
                    editSelected();
                }
            }
        });
        editButton.addActionListener(e -> editSelected());
        deleteButton.addActionListener(e -> deleteSelected());
        bind("ENTER", this::editSelected);
        bind("DELETE", this::deleteSelected);
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

    @Override
    public void refresh() {
        reload();
    }

    private void reload() {
        Integer selectedId = selected().map(Course::id).orElse(null);
        List<Course> courses = ctx.courses().search(search.getText());
        model.setData(courses, ctx.courses().enrollmentCounts());
        int total = ctx.courses().count();
        subtitle.setText(total + " courses in the catalogue");
        countLabel.setText(courses.size() == total ? "Showing all " + total + " courses"
                : "Showing " + courses.size() + " of " + total + " courses");
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

    private Optional<Course> selected() {
        int v = table.getSelectedRow();
        return v < 0 ? Optional.empty() : Optional.of(model.get(table.convertRowIndexToModel(v)));
    }

    private void updateActions() {
        boolean has = table.getSelectedRow() >= 0;
        editButton.setEnabled(has);
        deleteButton.setEnabled(has);
    }

    @Override
    public void createNew() {
        CourseDialog dialog = new CourseDialog(frame, ctx, null);
        dialog.setVisible(true);
        dialog.result().ifPresent(saved -> {
            reload();
            selectById(saved.id());
            frame.statusBar().flash("Added course " + saved.code());
        });
    }

    private void editSelected() {
        selected().ifPresent(course -> {
            CourseDialog dialog = new CourseDialog(frame, ctx, course);
            dialog.setVisible(true);
            dialog.result().ifPresent(saved -> {
                reload();
                selectById(saved.id());
                frame.statusBar().flash("Saved changes to " + saved.code());
            });
        });
    }

    private void deleteSelected() {
        selected().ifPresent(course -> {
            if (!Ui.confirm(frame, "Delete course", "<html>Delete <b>" + course.code() + " – " + course.title()
                    + "</b>?<br><br>This cannot be undone.</html>", "Delete")) {
                return;
            }
            try {
                ctx.courses().delete(course.id());
                reload();
                frame.statusBar().flash("Deleted course " + course.code());
            } catch (BusinessRuleException ex) {
                Ui.showError(frame, "Cannot delete course", ex.getMessage());
            }
        });
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
        Optional<Path> target = Exports.chooseCsvFile(frame, "courses");
        if (target.isEmpty()) {
            return;
        }
        List<List<String>> rows = new ArrayList<>();
        for (int v = 0; v < table.getRowCount(); v++) {
            int m = table.convertRowIndexToModel(v);
            Course c = model.get(m);
            rows.add(List.of(c.code(), c.title(), String.valueOf(c.units()),
                    String.valueOf(model.getValueAt(m, 3)), c.description() == null ? "" : c.description()));
        }
        Exports.write(frame, target.get(), List.of("Code", "Title", "Units", "Enrollments", "Description"), rows);
    }

    static final class CourseTableModel extends AbstractTableModel {
        private static final String[] COLUMNS = {"Code", "Title", "Units", "Enrollments", "Description"};
        private List<Course> rows = List.of();
        private Map<Integer, Integer> counts = Map.of();

        void setData(List<Course> rows, Map<Integer, Integer> counts) {
            this.rows = rows;
            this.counts = counts;
            fireTableDataChanged();
        }

        Course get(int row) {
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
            return column == 2 || column == 3 ? Integer.class : String.class;
        }

        @Override
        public Object getValueAt(int row, int column) {
            Course c = rows.get(row);
            return switch (column) {
                case 0 -> c.code();
                case 1 -> c.title();
                case 2 -> c.units();
                case 3 -> counts.getOrDefault(c.id(), 0);
                case 4 -> c.description() == null ? "" : c.description();
                default -> throw new IllegalArgumentException("column " + column);
            };
        }
    }
}
