// A student's transcript: profile, cumulative GPA, per-term GPA and every enrollment (StudentRecordDialog in Java).
import { h, clear, icon, escapeHtml } from './dom.js';
import { contentDialog } from './dialogs.js';
import { enrollDialog } from './forms.js';
import { DataTable } from './table.js';
import { formatGrade, describeGrade, remarks } from '../domain/grades.js';
import { fullName, initials } from '../domain/students.js';
import { STUDENT_STATUS_LABELS, yearLevelLabel } from '../domain/labels.js';

const STATUS_COLOR = { ACTIVE: 'var(--success)', ON_LEAVE: 'var(--warning)', INACTIVE: 'var(--text-faint)', GRADUATED: 'var(--purple)' };

export function openStudentRecord(ctx, studentId, { onChange } = {}) {
  const dlg = contentDialog({ title: 'Academic Record', width: 1080, className: 'record' });
  let changed = false;

  function build() {
    const record = ctx.enrollments.academicRecord(studentId);
    const s = record.student;

    const profile = h('div.card.profile',
      h('div.avatar', { 'aria-hidden': 'true' }, initials(s).toUpperCase(), h('span.dot', { style: { background: STATUS_COLOR[s.status] } })),
      h('div',
        h('div.profile-name', fullName(s)),
        h('div.muted', s.studentNumber + '  ·  ' + s.program.name + '  ·  ' + yearLevelLabel(s.yearLevel) + '  ·  ' + STUDENT_STATUS_LABELS[s.status]),
        h('div.muted.small', s.email + (s.phone ? '  ·  ' + s.phone : ''))),
      metric(formatGrade(record.gpa), 'Cumulative GPA', record.gpa == null ? 'No grades yet' : describeGrade(record.gpa), 'record-gpa'),
      metric(String(record.unitsEarned), 'Units earned', record.enrollments.length + ' enrollment(s)', 'record-units'));

    const table = new DataTable([
      { key: 'term', label: 'Term', sortValue: (e) => e.termKey },
      { key: 'courseCode', label: 'Course' },
      { key: 'courseTitle', label: 'Title' },
      { key: 'units', label: 'Units', align: 'center' },
      { key: 'grade', label: 'Grade', align: 'right', className: 'num', render: (e) => h('span', { class: e.grade != null && e.grade > 300 ? 'grade-fail' : '' }, formatGrade(e.grade)) },
      { key: 'remarks', label: 'Remarks', sortValue: (e) => remarks(e), render: (e) => remarks(e) },
    ], { minWidth: 640, emptyText: 'No enrollments yet.' });
    table.setRows(record.enrollments);
    const courses = h('div.card', { style: { padding: '14px 8px 6px' } }, h('h3.card-title', { style: { padding: '0 10px 8px' } }, 'Courses taken'), table.el);
    courses.querySelector('.table-card').classList.remove('card');
    courses.querySelector('.table-card').style.padding = '0';

    const terms = h('div.card.card-pad', h('h3.card-title', 'GPA per term'),
      record.terms.length ? h('div.terms', [...record.terms].reverse().map((t) => h('div.term-row',
        h('div', h('div.term-name', t.term), h('div.term-meta', t.courses + ' course(s) · ' + t.units + ' units' + (t.gpa == null ? ' · in progress' : ''))),
        h('div.term-gpa', formatGrade(t.gpa))))) : h('p.muted', { style: { marginTop: '10px' } }, 'No terms yet.'));

    const enroll = h('button.btn', {
      type: 'button', disabled: s.status !== 'ACTIVE',
      title: s.status === 'ACTIVE' ? 'Enroll ' + s.firstName + ' in a course' : 'Only active students can be enrolled',
      onClick: async () => { const saved = await enrollDialog(ctx, s.id); if (saved) { changed = true; build(); } },
    }, icon('plus', 16), 'Enroll in Course…');
    const close = h('button.btn.btn-primary', { type: 'button', style: { minWidth: '100px' }, onClick: () => dlg.close() }, 'Close');

    dlg.setBody(profile, h('div.record-grid', courses, terms), h('div.dlg-actions', { style: { marginTop: '16px' } }, h('span.push', enroll), close));
  }

  build();
  dlg.dialog.addEventListener('close', () => { if (changed && onChange) onChange(); }, { once: true });
  return dlg;
}

function metric(value, caption, hint, testId) {
  return h('div.metric', h('div.metric-value', { 'data-testid': testId }, value), h('div.metric-caption', caption), h('div.metric-hint', hint));
}
