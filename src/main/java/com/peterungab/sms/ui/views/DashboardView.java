package com.peterungab.sms.ui.views;

import com.formdev.flatlaf.FlatClientProperties;
import com.peterungab.sms.AppContext;
import com.peterungab.sms.model.GradeScale;
import com.peterungab.sms.model.Student;
import com.peterungab.sms.service.DashboardService;
import com.peterungab.sms.ui.Icons;
import com.peterungab.sms.ui.MainFrame;
import com.peterungab.sms.ui.Page;
import com.peterungab.sms.ui.Theme;
import com.peterungab.sms.ui.Ui;
import com.peterungab.sms.ui.View;
import com.peterungab.sms.ui.components.BarChart;
import com.peterungab.sms.ui.components.Card;
import com.peterungab.sms.ui.components.ScrollablePanel;
import com.peterungab.sms.ui.components.StatCard;
import net.miginfocom.swing.MigLayout;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/** Overview page: KPIs, grade distribution, enrollment by program, top students and course load. */
public final class DashboardView extends JPanel implements View {

    private final AppContext ctx;
    private final MainFrame frame;

    private final JLabel subtitle = Ui.muted(" ", 1);
    private final StatCard studentsCard = new StatCard("Total students", Icons.students(20), Theme::accent);
    private final StatCard coursesCard = new StatCard("Courses offered", Icons.courses(20), Theme::teal);
    private final StatCard enrollmentsCard = new StatCard("Enrollments", Icons.enrollments(20), Theme::purple);
    private final StatCard gpaCard = new StatCard("Average GPA", Icons.trophy(20), Theme::warning);

    private final BarChart gradeChart = new BarChart(BarChart.Orientation.VERTICAL);
    private final BarChart programChart = new BarChart(BarChart.Orientation.HORIZONTAL);
    private final BarChart loadChart = new BarChart(BarChart.Orientation.HORIZONTAL);
    private final JLabel loadSubtitle = Ui.muted(" ", -1);
    private final JPanel topList = new JPanel(new MigLayout("insets 0, fillx, wrap, gap 0 4", "[grow,fill]"));

    public DashboardView(AppContext ctx, MainFrame frame) {
        super(new java.awt.BorderLayout());
        this.ctx = ctx;
        this.frame = frame;

        // columns have a small minimum so the grid always fits the window width;
        // if the window is short, the whole page scrolls vertically
        ScrollablePanel page = new ScrollablePanel(new MigLayout("fill, insets 26 30 24 30, gap 18 18",
                "[0:120:,grow,fill,sg c][0:120:,grow,fill,sg c][0:120:,grow,fill,sg c][0:120:,grow,fill,sg c]",
                "[]6[][220:n:,grow,fill][250:n:,grow,fill]"));
        javax.swing.JScrollPane scroll = Ui.scroll(page);
        scroll.setHorizontalScrollBarPolicy(javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(24);
        scroll.setOpaque(false);
        add(scroll);

        page.add(header(), "span 4, wrap, gapbottom 8");
        page.add(studentsCard);
        page.add(coursesCard);
        page.add(enrollmentsCard);
        page.add(gpaCard, "wrap");
        addCharts(page);
    }

    private void addCharts(JPanel page) {

        gradeChart.setColorFunction(key -> key.equals("5.00") ? Theme.danger()
                : key.equals("3.00") ? Theme.warning() : Theme.accent());
        gradeChart.setEmptyMessage("No grades recorded yet");
        page.add(chartCard("Grade distribution", "All graded courses · 1.00 is the highest grade", gradeChart),
                "span 2");
        programChart.setColorFunction(key -> switch (key) {
            case "BSCpE" -> Theme.accent();
            case "BSCS" -> Theme.purple();
            case "BSIT" -> Theme.teal();
            default -> Theme.warning();
        });
        page.add(chartCard("Students by program", "Headcount per degree program", programChart), "span 2, wrap");

        topList.setOpaque(false);
        page.add(listCard(), "span 2");
        loadChart.setColorFunction(key -> Theme.purple());
        loadChart.setEmptyMessage("No enrollments this term yet");
        page.add(chartCard("Current term course load", loadSubtitle, loadChart), "span 2");
    }

    private JComponent header() {
        JPanel p = new JPanel(new MigLayout("insets 0, fillx", "[grow][]", "[]2[]"));
        p.setOpaque(false);
        p.add(Ui.pageTitle("Dashboard"), "wrap");
        p.add(subtitle);
        return p;
    }

    private static Card chartCard(String title, String subtitle, JComponent chart) {
        return chartCard(title, Ui.muted(subtitle, -1), chart);
    }

    private static Card chartCard(String title, JLabel subtitle, JComponent chart) {
        Card card = new Card(new MigLayout("fill, insets 0", "[grow,fill]", "[]2[]16[grow,fill]"));
        card.setBorder(javax.swing.BorderFactory.createEmptyBorder(18, 20, 18, 20));
        card.add(Ui.sectionTitle(title), "wrap");
        card.add(subtitle, "wrap");
        card.add(chart, "grow, w 100:300:, h 80:160:");
        return card;
    }

    private Card listCard() {
        Card card = new Card(new MigLayout("fill, insets 0", "[grow,fill]", "[]2[]12[grow,top]"));
        card.setBorder(javax.swing.BorderFactory.createEmptyBorder(18, 20, 14, 20));
        card.add(Ui.sectionTitle("Top performers"), "wrap");
        card.add(Ui.muted("Best cumulative GPA · Dean's List at "
                + DashboardService.DEANS_LIST_GPA + " or better", -1), "wrap");
        card.add(topList, "grow");
        return card;
    }

    @Override
    public void refresh() {
        DashboardService.Stats s = ctx.dashboard().load();
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.ENGLISH));
        subtitle.setText("Overview for " + s.currentTerm() + " · " + today);

        studentsCard.setValue(String.valueOf(s.totalStudents()),
                s.activeStudents() + " currently active");
        coursesCard.setValue(String.valueOf(s.totalCourses()), "In the course catalogue");
        enrollmentsCard.setValue(String.valueOf(s.totalEnrollments()),
                s.currentTermEnrollments() + " active in " + s.currentTerm());
        gpaCard.setValue(s.averageGpa().map(GradeScale::format).orElse("—"),
                s.deansListCount() + " Dean's List candidates");

        gradeChart.setData(s.gradeDistribution());
        programChart.setData(s.studentsByProgram());
        loadChart.setData(first(s.currentTermCourseLoad(), 6));
        loadSubtitle.setText("Students enrolled per course in " + s.currentTerm());

        topList.removeAll();
        List<DashboardService.RankedStudent> top = s.topStudents();
        if (top.isEmpty()) {
            topList.add(Ui.muted("No graded courses yet."));
        }
        for (int i = 0; i < top.size(); i++) {
            topList.add(new TopRow(i + 1, top.get(i)), "h 42!");
        }
        topList.revalidate();
        topList.repaint();
    }

    private static java.util.Map<String, Integer> first(java.util.Map<String, Integer> map, int n) {
        java.util.Map<String, Integer> result = new java.util.LinkedHashMap<>();
        map.entrySet().stream().limit(n).forEach(e -> result.put(e.getKey(), e.getValue()));
        return result;
    }

    /** One ranked student; click to open their academic record. */
    private final class TopRow extends JPanel {
        private boolean hover;

        TopRow(int rank, DashboardService.RankedStudent ranked) {
            super(new MigLayout("insets 0 6 0 10, fill, gap 0", "[]12[grow][]", "[center]"));
            setOpaque(false);
            Student st = ranked.student();
            JLabel name = new JLabel(st.fullName());
            name.putClientProperty(FlatClientProperties.STYLE, "font: bold");
            JPanel text = new JPanel(new MigLayout("insets 0, gap 0", "[grow,fill]", "[]0[]"));
            text.setOpaque(false);
            text.add(name, "wrap");
            text.add(Ui.muted(st.studentNumber() + " · " + st.program().code() + " · "
                    + Student.yearLevelLabel(st.yearLevel()), -2));
            JLabel gpa = new JLabel(GradeScale.format(ranked.gpa())) {
                @Override
                public void updateUI() {
                    super.updateUI();
                    setForeground(ranked.gpa().compareTo(DashboardService.DEANS_LIST_GPA) <= 0 ? Theme.success() : Theme.text());
                }
            };
            gpa.putClientProperty(FlatClientProperties.STYLE, "font: bold +2");
            Ui.textSafetyMargin(gpa);
            Ui.textSafetyMargin(name);
            add(new RankBadge(rank));
            add(text, "growx");
            add(gpa);

            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setToolTipText("Open " + st.fullName() + "'s academic record");
            MouseAdapter mouse = new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    frame.openStudentRecord(st.id());
                }

                @Override
                public void mouseEntered(MouseEvent e) {
                    hover = true;
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hover = false;
                    repaint();
                }
            };
            addMouseListener(mouse);
        }

        @Override
        protected void paintComponent(Graphics g) {
            if (hover) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Theme.tint(Theme.accent(), 0.10f));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                g2.dispose();
            }
        }
    }

    private static final class RankBadge extends JComponent {
        private final int rank;

        RankBadge(int rank) {
            this.rank = rank;
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(30, 30);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color c = rank == 1 ? Theme.warning() : Theme.accent();
                g2.setColor(Theme.tint(c, rank == 1 ? 0.22f : 0.14f));
                g2.fillOval(0, 0, getWidth() - 1, getHeight() - 1);
                g2.setColor(c);
                g2.setFont(getFont() != null ? getFont().deriveFont(java.awt.Font.BOLD) :
                        javax.swing.UIManager.getFont("defaultFont").deriveFont(java.awt.Font.BOLD));
                String s = String.valueOf(rank);
                java.awt.FontMetrics fm = g2.getFontMetrics();
                com.formdev.flatlaf.ui.FlatUIUtils.drawString(this, g2, s, (getWidth() - fm.stringWidth(s)) / 2,
                        (getHeight() - fm.getHeight()) / 2 + fm.getAscent());
            } finally {
                g2.dispose();
            }
        }
    }

    @Override
    public void createNew() {
        frame.showPage(Page.STUDENTS);
        frame.view(Page.STUDENTS).createNew();
    }
}
