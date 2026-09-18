// Enrollment list with term/course filters, grade entry and drop/remove actions (EnrollmentsView in Java).
import { h, icon, pill, debounce, escapeHtml, downloadText, isoToday } from '../dom.js';
import { DataTable } from '../table.js';
import { confirmDialog, alertDialog } from '../dialogs.js';
import { enrollDialog, gradeDialog } from '../forms.js';
import { openStudentRecord } from '../record.js';
import { formatGrade, remarks } from '../../domain/grades.js';
import { SEMESTERS, SEMESTER_LABELS, ENROLLMENT_STATUS_LABELS } from '../../domain/labels.js';
import { BusinessRuleError } from '../../domain/errors.js';
import { toCsv } from '../../domain/csv.js';

export function enrollmentsPage(ctx, app) {
  const search = h('input.input', { type: 'search', placeholder: 'Search student or course', 'aria-label': 'Search enrollments', autocomplete: 'off', id: 'enrollments-search' });
  const yearFilter = h('select.select', { 'aria-label': 'Filter by school year', id: 'enrollments-year' });
  const semesterFilter = h('select.select', { 'aria-label': 'Filter by semester', id: 'enrollments-semester' },
    h('option', { value: '' }, 'All semesters'), SEMESTERS.map((s) => h('option', { value: s }, SEMESTER_LABELS[s])));
  const courseFilter = h('select.select', { 'aria-label': 'Filter by course', id: 'enrollments-course' });
  const subtitle = h('p.page-sub');
  const countLabel = h('span', { id: 'enrollments-count' });
  const recordBtn = h('button.btn.btn-ghost', { type: 'button', disabled: true, onClick: () => openRecord() }, icon('record', 16), 'Record');
  const gradeBtn = h('button.btn.btn-ghost', { type: 'button', disabled: true, id: 'enrollments-grade', onClick: () => gradeSelected() }, icon('grade', 16), 'Grade');
  const dropBtn = h('button.btn.btn-ghost', { type: 'button', disabled: true, onClick: () => dropSelected() }, icon('drop', 16), 'Drop');
  const removeBtn = h('button.btn.btn-ghost', { type: 'button', disabled: true, onClick: () => removeSelected() }, icon('trash', 16), 'Remove');

  const table = new DataTable([
    { key: 'studentNumber', label: 'Student No.' },
    { key: 'studentName', label: 'Student' },
    { key: 'courseCode', label: 'Course' },
    { key: 'courseTitle', label: 'Title' },
    { key: 'units', label: 'Units', align: 'center' },
    { key: 'term', label: 'Term', sortValue: (e) => e.termKey },
    { key: 'grade', label: 'Grade', align: 'right', className: 'num', render: (e) => h('span', { class: e.grade != null && e.grade > 300 ? 'grade-fail' : e.grade == null ? 'muted' : '' }, formatGrade(e.grade)) },
    { key: 'status', label: 'Status', render: (e) => pill(e.status, ENROLLMENT_STATUS_LABELS[e.status]) },
    { key: 'remarks', label: 'Remarks', sortValue: (e) => remarks(e), render: (e) => remarks(e) },
  ], { minWidth: 1000, emptyText: 'No enrollments match your search.', onSelect: updateActions, onActivate: () => gradeSelected(), onDelete: () => removeSelected() });

  const el = h('div',
    h('div.page-header',
      h('div', h('h1.page-title', 'Enrollments & Grades'), subtitle),
      h('div.page-actions',
        h('button.btn', { type: 'button', title: 'Export the rows currently shown (Ctrl+E)', onClick: () => exportCsv() }, icon('export', 16), 'Export CSV'),
        h('button.btn.btn-primary', { type: 'button', id: 'enrollments-add', title: 'Enroll a student in a course (Ctrl+N)', onClick: () => createNew() }, icon('plus', 16), 'Enroll Student'))),
    h('div.toolbar', h('div.search-wrap.search', icon('search', 16), search), yearFilter, semesterFilter, courseFilter),
    table.el,
    h('div.footer-row', countLabel, h('span.spacer'), recordBtn, gradeBtn, dropBtn, removeBtn));

  search.addEventListener('input', debounce(reload, 200));
  for (const f of [yearFilter, semesterFilter, courseFilter]) f.addEventListener('change', reload);

  function populateFilters() {
    const year = yearFilter.value;
    yearFilter.replaceChildren(h('option', { value: '' }, 'All school years'), ...ctx.enrollments.schoolYears().map((sy) => h('option', { value: sy }, 'SY ' + sy)));
    yearFilter.value = year;
    const course = courseFilter.value;
    courseFilter.replaceChildren(h('option', { value: '' }, 'All courses'), ...ctx.courses.findAll().map((c) => h('option', { value: c.id }, c.code)));
    courseFilter.value = course;
  }

  function reload() {
    const rows = ctx.enrollments.search({
      keyword: search.value,
      courseId: courseFilter.value ? Number(courseFilter.value) : null,
      schoolYear: yearFilter.value || null,
      semester: semesterFilter.value || null,
    });
    table.setRows(rows);
    const total = ctx.enrollments.count();
    subtitle.textContent = 'Current term: ' + SEMESTER_LABELS[ctx.enrollments.currentSemester()] + ', SY ' + ctx.enrollments.currentSchoolYear() + ' · grades on the 1.00 – 5.00 scale';
    countLabel.textContent = rows.length === total ? 'Showing all ' + total + ' enrollments · double-click a row to record a grade' : 'Showing ' + rows.length + ' of ' + total + ' enrollments';
    updateActions(table.selected());
  }

  function updateActions(e) {
    recordBtn.disabled = removeBtn.disabled = !e;
    gradeBtn.disabled = !e || e.status === 'DROPPED';
    dropBtn.disabled = !e || e.status !== 'ENROLLED';
  }

  async function createNew() {
    const saved = await enrollDialog(ctx);
    if (saved) { populateFilters(); reload(); table.select(saved.id); app.flash('Enrolled ' + saved.studentName + ' in ' + saved.courseCode); }
  }

  async function gradeSelected() {
    const e = table.selected();
    if (!e) return;
    if (e.status === 'DROPPED') { await alertDialog({ title: 'Dropped course', html: 'A dropped course cannot be graded.' }); return; }
    const saved = await gradeDialog(ctx, e);
    if (saved) {
      reload();
      table.select(saved.id);
      app.flash(saved.grade == null ? 'Cleared grade of ' + saved.studentName + ' in ' + saved.courseCode
        : 'Recorded ' + formatGrade(saved.grade) + ' for ' + saved.studentName + ' in ' + saved.courseCode);
    }
  }

  async function dropSelected() {
    const e = table.selected();
    if (!e) return;
    const ok = await confirmDialog({ title: 'Drop course', confirmLabel: 'Drop', html: 'Mark <b>' + escapeHtml(e.courseCode) + '</b> as dropped for <b>' + escapeHtml(e.studentName) + '</b> (' + escapeHtml(e.term) + ')?' });
    if (!ok) return;
    try { ctx.enrollments.drop(e.id); reload(); app.flash(e.studentName + ' dropped ' + e.courseCode); }
    catch (err) { if (err instanceof BusinessRuleError) await alertDialog({ title: 'Cannot drop course', html: escapeHtml(err.message) }); else throw err; }
  }

  async function removeSelected() {
    const e = table.selected();
    if (!e) return;
    const ok = await confirmDialog({
      title: 'Remove enrollment', confirmLabel: 'Delete', danger: true,
      html: 'Permanently delete the enrollment of <b>' + escapeHtml(e.studentName) + '</b> in <b>' + escapeHtml(e.courseCode) + '</b> (' + escapeHtml(e.term) + ')'
        + (e.grade != null ? ' including the grade ' + formatGrade(e.grade) : '') + '?<br><br>This cannot be undone.',
    });
    if (!ok) return;
    try { ctx.enrollments.delete(e.id); reload(); app.flash('Removed enrollment'); }
    catch (err) { if (err instanceof BusinessRuleError) { await alertDialog({ title: 'Cannot remove enrollment', html: escapeHtml(err.message) }); reload(); } else throw err; }
  }

  function openRecord() {
    const e = table.selected();
    if (e) openStudentRecord(ctx, e.studentId, { onChange: reload });
  }

  function csvText() {
    const rows = table.visibleRows().map((e) => [e.studentNumber, e.studentName, e.courseCode, e.courseTitle, String(e.units), e.schoolYear,
      SEMESTER_LABELS[e.semester], e.grade == null ? '' : formatGrade(e.grade), ENROLLMENT_STATUS_LABELS[e.status], remarks(e)]);
    return toCsv(['Student No.', 'Student', 'Course', 'Title', 'Units', 'School Year', 'Semester', 'Grade', 'Status', 'Remarks'], rows);
  }

  function exportCsv() {
    const text = csvText();
    downloadText('enrollments-' + isoToday() + '.csv', text);
    app.flash('Exported ' + table.visibleRows().length + ' row(s) to enrollments-' + isoToday() + '.csv');
    return text;
  }

  return { el, refresh() { populateFilters(); reload(); }, createNew, focusSearch() { search.focus(); search.select(); }, exportCsv, csvText, table };
}
