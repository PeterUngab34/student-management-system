// Overview page: KPIs, grade distribution, enrollment by program, top students and course load.
import { h, clear, icon, longDate } from '../dom.js';
import { columnChart, barChart } from '../charts.js';
import { formatGrade } from '../../domain/grades.js';
import { DEANS_LIST_GPA } from '../../domain/dashboard.js';
import { fullName } from '../../domain/students.js';
import { yearLevelLabel } from '../../domain/labels.js';
import { openStudentRecord } from '../record.js';

const PROGRAM_COLORS = { BSCpE: 'var(--chart-1)', BSCS: 'var(--chart-2)', BSIT: 'var(--chart-3)', BSECE: 'var(--chart-4)' };
const FALLBACK_COLORS = ['var(--chart-1)', 'var(--chart-2)', 'var(--chart-3)', 'var(--chart-4)'];

export function dashboardPage(ctx, app) {
  const el = h('div');

  function refresh() {
    const s = ctx.dashboard.load();
    clear(el);

    const programColor = (code) => PROGRAM_COLORS[code] || FALLBACK_COLORS[s.studentsByProgram.findIndex(([c]) => c === code) % 4];

    el.append(
      h('div.page-header', h('div', h('h1.page-title', 'Dashboard'), h('p.page-sub', 'Overview for ' + s.currentTerm + ' · ' + longDate(new Date())))),
      h('div.stats',
        stat('Total students', s.totalStudents, s.activeStudents + ' currently active', 'students', 'tint-accent', 'stat-students'),
        stat('Courses offered', s.totalCourses, 'In the course catalogue', 'courses', 'tint-teal', 'stat-courses'),
        stat('Enrollments', s.totalEnrollments, s.currentTermEnrollments + ' active in ' + s.currentTerm, 'enrollments', 'tint-purple', 'stat-enrollments'),
        stat('Average GPA', formatGrade(s.averageGpa), s.deansListCount + " Dean's List candidates", 'trophy', 'tint-warning', 'stat-gpa')),
      h('div.grid-2',
        chartCard('Grade distribution', 'All graded courses · 1.00 is the highest grade',
          columnChart(s.gradeDistribution, {
            title: 'Grade distribution', valueLabel: 'Enrollments', emptyText: 'No grades recorded yet',
            color: (k) => (k === '5.00' ? 'var(--chart-danger)' : k === '3.00' ? 'var(--chart-4)' : 'var(--chart-1)'),
          })),
        chartCard('Students by program', 'Headcount per degree program',
          barChart(s.studentsByProgram, { title: 'Students by program', valueLabel: 'Students', color: programColor,
            legend: s.studentsByProgram.map(([code]) => [code, programColor(code)]) }))),
      h('div.grid-2',
        h('div.card.card-pad',
          h('h2.card-title', 'Top performers'),
          h('p.card-sub', "Best cumulative GPA · Dean's List at " + formatGrade(DEANS_LIST_GPA) + ' or better'),
          s.topStudents.length
            ? h('div.top-list', s.topStudents.map((t, i) => h('button.top-row', {
              type: 'button', title: 'Open ' + fullName(t.student) + "'s academic record",
              onClick: () => openStudentRecord(ctx, t.student.id, { onChange: refresh }),
            },
            h('span.rank', { class: i === 0 ? 'rank-1' : '' }, i + 1),
            h('span', h('div.top-name', fullName(t.student)), h('div.top-meta', t.student.studentNumber + ' · ' + t.student.program.code + ' · ' + yearLevelLabel(t.student.yearLevel))),
            h('span.top-gpa', { class: t.gpa <= DEANS_LIST_GPA ? 'deans' : '' }, formatGrade(t.gpa)))))
            : h('p.muted', { style: { marginTop: '12px' } }, 'No graded courses yet.')),
        chartCard('Current term course load', 'Students enrolled per course in ' + s.currentTerm,
          barChart(s.currentTermCourseLoad.slice(0, 6), { title: 'Current term course load', valueLabel: 'Students', emptyText: 'No enrollments this term yet', color: () => 'var(--chart-2)' }))));
  }

  return {
    el,
    refresh,
    createNew: () => app.navigate('students', { create: true }),
  };
}

function stat(label, value, hint, iconName, tint, testId) {
  return h('div.card.stat',
    h('div.stat-label', label),
    h('div.stat-icon', { class: tint, 'aria-hidden': 'true' }, icon(iconName, 20)),
    h('div.stat-value', { 'data-testid': testId }, String(value)),
    h('div.stat-hint', hint));
}

function chartCard(title, sub, chart) {
  return h('div.card.chart-card', h('h2.card-title', title), h('p.card-sub', sub), chart);
}
