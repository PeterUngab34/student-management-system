package com.peterungab.sms.ui.dialogs;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.ui.FlatUIUtils;
import com.peterungab.sms.AppContext;
import com.peterungab.sms.model.Enrollment;
import com.peterungab.sms.model.GradeScale;
import com.peterungab.sms.model.Student;
import com.peterungab.sms.model.StudentStatus;
import com.peterungab.sms.service.AcademicRecord;
import com.peterungab.sms.ui.Icons;
import com.peterungab.sms.ui.Theme;
import com.peterungab.sms.ui.Ui;
import com.peterungab.sms.ui.components.Card;
import com.peterungab.sms.ui.components.PillRenderer;
import net.miginfocom.swing.MigLayout;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableModel;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.event.KeyEvent;

/** A student's transcript: profile, cumulative GPA, per-term GPA and every enrollment. */
public final class StudentRecordDialog extends JDialog {

    private final AppContext ctx;
    private final int studentId;
    private final JPanel body = new JPanel(new MigLayout("fill, insets 24 26 20 26, gap 16 16",
            "[grow,fill][290!,fill]", "[][grow,fill][]"));

    public StudentRecordDialog(Window owner, AppContext ctx, int studentId) {
        super(owner, "Academic Record", ModalityType.APPLICATION_MODAL);
        this.ctx = ctx;
        this.studentId = studentId;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setContentPane(body);
        getRootPane().registerKeyboardAction(e -> dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        build();
        setSize(1080, 660);
        setMinimumSize(new Dimension(820, 520));
        setLocationRelativeTo(owner);
    }

    private void build() {
        body.removeAll();
        AcademicRecord record = ctx.enrollments().academicRecord(studentId);
        Student s = record.student();

        body.add(profileCard(record), "span 2, wrap");
        body.add(enrollmentsCard(record));
        body.add(termsCard(record), "wrap");

        JButton enroll = Ui.button("Enroll in Course…", Icons.plus(16));
        enroll.setEnabled(s.status() == StudentStatus.ACTIVE);
        enroll.setToolTipText(s.status() == StudentStatus.ACTIVE ? "Enroll " + s.firstName() + " in a course"
                : "Only active students can be enrolled");
        enroll.addActionListener(e -> {
            EnrollDialog dialog = new EnrollDialog(this, ctx, s.id());
            dialog.setVisible(true);
            if (dialog.result().isPresent()) {
                build();
            }
        });
        JButton close = Ui.primaryButton("Close", null);
        close.addActionListener(e -> dispose());
        getRootPane().setDefaultButton(close);
        JPanel buttons = new JPanel(new MigLayout("insets 0, fillx", "[]push[]", "[]"));
        buttons.setOpaque(false);
        buttons.add(enroll);
        buttons.add(close, "w 100!");
        body.add(buttons, "span 2, growx");
        body.revalidate();
        body.repaint();
    }

    private JComponent profileCard(AcademicRecord record) {
        Student s = record.student();
        Card card = new Card(new MigLayout("fill, insets 0", "[]16[grow,fill]24[]28[]", "[center]"));
        card.setBorder(javax.swing.BorderFactory.createEmptyBorder(18, 20, 18, 24));

        JLabel name = new JLabel(s.fullName());
        name.putClientProperty(FlatClientProperties.STYLE, "font: bold +8");
        JPanel info = new JPanel(new MigLayout("insets 0, gap 0", "[grow,fill]", "[]4[]4[]"));
        info.setOpaque(false);
        info.add(name, "wrap");
        info.add(Ui.muted(s.studentNumber() + "  ·  " + s.program().name() + "  ·  "
                + Student.yearLevelLabel(s.yearLevel()) + "  ·  " + s.status().label()), "wrap");
        info.add(Ui.muted(s.email() + (s.phone() == null ? "" : "  ·  " + s.phone()), -1));

        card.add(new Avatar(s.initials(), PillRenderer.studentStatusColor(s.status())));
        card.add(info, "growx");
        card.add(metric(record.gpa().map(GradeScale::format).orElse("—"), "Cumulative GPA",
                record.gpa().map(g -> GradeScale.describe(g)).orElse("No grades yet")));
        card.add(metric(String.valueOf(record.unitsEarned()), "Units earned",
                record.enrollments().size() + " enrollment(s)"));
        return card;
    }

    private static JComponent metric(String value, String caption, String hint) {
        JPanel p = new JPanel(new MigLayout("insets 0 6 0 6, gap 0", "[grow,fill]", "[]0[]0[]"));
        p.setOpaque(false);
        JLabel v = new JLabel(value);
        v.putClientProperty(FlatClientProperties.STYLE, "font: bold +14");
        JLabel c = new JLabel(caption);
        JLabel h = Ui.muted(hint, -2);
        for (JLabel label : new JLabel[]{v, c, h}) {
            label.setHorizontalAlignment(SwingConstants.RIGHT);
            Ui.textSafetyMargin(label);
            p.add(label, "wrap");
        }
        return p;
    }

    private JComponent enrollmentsCard(AcademicRecord record) {
        DefaultTableModel model = new DefaultTableModel(new Object[]{"Term", "Course", "Title", "Units", "Grade", "Remarks"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        for (Enrollment e : record.enrollments()) {
            model.addRow(new Object[]{e.term(), e.courseCode(), e.courseTitle(), e.units(),
                    GradeScale.format(e.grade()), e.remarks()});
        }
        JTable table = new JTable(model);
        Ui.setupTable(table);
        table.getColumnModel().getColumn(3).setCellRenderer(Ui.alignedRenderer(SwingConstants.CENTER));
        table.getColumnModel().getColumn(4).setCellRenderer(Ui.alignedRenderer(SwingConstants.RIGHT));
        Ui.columnWidth(table, 0, 165, 175);
        Ui.columnWidth(table, 1, 90, 100);
        Ui.columnWidth(table, 2, 230, 0);
        Ui.columnWidth(table, 3, 68, 72);
        Ui.columnWidth(table, 4, 72, 80);
        Ui.columnWidth(table, 5, 115, 125);

        Card card = new Card(new MigLayout("fill, insets 0", "[grow,fill]", "[]8[grow,fill]"));
        card.setBorder(javax.swing.BorderFactory.createEmptyBorder(14, 8, 6, 8));
        card.add(Ui.sectionTitle("Courses taken"), "gapleft 10, wrap");
        if (record.enrollments().isEmpty()) {
            JLabel empty = Ui.muted("No enrollments yet.");
            empty.setHorizontalAlignment(SwingConstants.CENTER);
            card.add(empty);
        } else {
            card.add(Ui.scroll(table));
        }
        return card;
    }

    private JComponent termsCard(AcademicRecord record) {
        Card card = new Card(new MigLayout("fillx, insets 0, wrap", "[grow,fill]", "[]12[]"));
        card.add(Ui.sectionTitle("GPA per term"));
        if (record.terms().isEmpty()) {
            card.add(Ui.muted("No terms yet."));
        }
        for (int i = record.terms().size() - 1; i >= 0; i--) {
            AcademicRecord.TermSummary t = record.terms().get(i);
            JPanel row = new JPanel(new MigLayout("insets 0, fillx, gap 0", "[grow][]", "[]0[]"));
            row.setOpaque(false);
            JLabel term = new JLabel(t.term());
            term.putClientProperty(FlatClientProperties.STYLE, "font: bold");
            JLabel gpa = new JLabel(t.gpa().map(GradeScale::format).orElse("—"));
            gpa.putClientProperty(FlatClientProperties.STYLE, "font: bold +2");
            JLabel details = Ui.muted(t.courses() + " course(s) · " + t.units() + " units"
                    + (t.gpa().isEmpty() ? " · in progress" : ""), -2);
            Ui.textSafetyMargin(term);
            Ui.textSafetyMargin(gpa);
            Ui.textSafetyMargin(details);
            row.add(term);
            row.add(gpa, "spany 2, right, wrap");
            row.add(details);
            card.add(row, "gapbottom 6");
        }
        return card;
    }

    /** Circle with the student's initials, tinted with the status colour. */
    private static final class Avatar extends JComponent {
        private final String initials;
        private final Color color;

        Avatar(String initials, Color color) {
            this.initials = initials.toUpperCase();
            this.color = color;
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(64, 64);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setPaint(new java.awt.GradientPaint(0, 0, Theme.accent(), getWidth(), getHeight(), Theme.purple()));
                g2.fillOval(0, 0, getWidth() - 1, getHeight() - 1);
                g2.setColor(color);
                g2.setStroke(new java.awt.BasicStroke(3f));
                g2.fillOval(getWidth() - 16, getHeight() - 16, 14, 14);
                g2.setColor(Theme.card());
                g2.drawOval(getWidth() - 16, getHeight() - 16, 14, 14);
                g2.setColor(Color.WHITE);
                Font base = javax.swing.UIManager.getFont("defaultFont");
                g2.setFont(base.deriveFont(Font.BOLD, base.getSize2D() + 8f));
                FontMetrics fm = g2.getFontMetrics();
                FlatUIUtils.drawString(this, g2, initials, (getWidth() - fm.stringWidth(initials)) / 2,
                        (getHeight() - fm.getHeight()) / 2 + fm.getAscent());
            } finally {
                g2.dispose();
            }
        }
    }
}
