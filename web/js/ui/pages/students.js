// Student list with live search, filters, sortable columns and CRUD actions (StudentsView in Java).
import { h, icon, pill, debounce, escapeHtml, downloadText, isoToday } from '../dom.js';
import { DataTable } from '../table.js';
import { confirmDialog, alertDialog } from '../dialogs.js';
import { studentDialog } from '../forms.js';
import { openStudentRecord } from '../record.js';
import { formatGrade } from '../../domain/grades.js';
import { fullName, sortableName } from '../../domain/students.js';
import { STUDENT_STATUSES, STUDENT_STATUS_LABELS, yearLevelLabel } from '../../domain/labels.js';
import { BusinessRuleError } from '../../domain/errors.js';
import { toCsv } from '../../domain/csv.js';

export function studentsPage(ctx, app) {
  let gpas = new Map();

  const search = h('input.input', { type: 'search', placeholder: 'Search name, student no. or e-mail', 'aria-label': 'Search students', autocomplete: 'off', id: 'students-search' });
  const programFilter = h('select.select', { 'aria-label': 'Filter by program', id: 'students-program' });
  const yearFilter = h('select.select', { 'aria-label': 'Filter by year level', id: 'students-year' },
    h('option', { value: '' }, 'All years'), [1, 2, 3, 4, 5].map((y) => h('option', { value: y }, yearLevelLabel(y))));
  const statusFilter = h('select.select', { 'aria-label': 'Filter by status', id: 'students-status' },
    h('option', { value: '' }, 'Any status'), STUDENT_STATUSES.map((s) => h('option', { value: s }, STUDENT_STATUS_LABELS[s])));
  const subtitle = h('p.page-sub');
  const countLabel = h('span', { id: 'students-count' });
  const recordBtn = h('button.btn.btn-ghost', { type: 'button', disabled: true, onClick: () => openRecord() }, icon('record', 16), 'Record');
  const editBtn = h('button.btn.btn-ghost', { type: 'button', disabled: true, onClick: () => editSelected() }, icon('edit', 16), 'Edit');
  const deleteBtn = h('button.btn.btn-ghost', { type: 'button', disabled: true, onClick: () => deleteSelected() }, icon('trash', 16), 'Delete');

  const table = new DataTable([
    { key: 'studentNumber', label: 'Student No.' },
    { key: 'name', label: 'Name', sortValue: (s) => sortableName(s), render: (s) => sortableName(s) },
    { key: 'program', label: 'Program', sortValue: (s) => s.program.code, render: (s) => s.program.code },
    { key: 'yearLevel', label: 'Year', align: 'center' },
    { key: 'email', label: 'E-mail' },
    { key: 'status', label: 'Status', render: (s) => pill(s.status, STUDENT_STATUS_LABELS[s.status]) },
    { key: 'gpa', label: 'GPA', align: 'right', className: 'num', sortValue: (s) => gpas.get(s.id) ?? null, render: (s) => formatGrade(gpas.get(s.id) ?? null) },
  ], {
    minWidth: 860,
    emptyText: 'No students match your search.',
    onSelect: updateActions,
    onActivate: () => editSelected(),
    onDelete: () => deleteSelected(),
  });

  const el = h('div',
    h('div.page-header',
      h('div', h('h1.page-title', 'Students'), subtitle),
      h('div.page-actions',
        h('button.btn', { type: 'button', id: 'students-export', title: 'Export the rows currently shown (Ctrl+E)', onClick: () => exportCsv() }, icon('export', 16), 'Export CSV'),
        h('button.btn.btn-primary', { type: 'button', id: 'students-add', title: 'Add a new student (Ctrl+N)', onClick: () => createNew() }, icon('plus', 16), 'Add Student'))),
    h('div.toolbar',
      h('div.search-wrap.search', icon('search', 16), search),
      programFilter, yearFilter, statusFilter,
      h('button.btn.btn-ghost', { type: 'button', title: 'Clear search and filters', onClick: () => clearFilters() }, icon('close', 14), 'Clear')),
    table.el,
    h('div.footer-row', countLabel, h('span.spacer'), recordBtn, editBtn, deleteBtn));

  const reloadDebounced = debounce(reload, 200);
  search.addEventListener('input', reloadDebounced);
  for (const f of [programFilter, yearFilter, statusFilter]) f.addEventListener('change', reload);

  function populatePrograms() {
    const current = programFilter.value;
    programFilter.replaceChildren(h('option', { value: '' }, 'All programs'),
      ...ctx.students.programs().map((p) => h('option', { value: p.id }, p.code + ' – ' + p.name.replace('BS ', ''))));
    programFilter.value = current;
  }

  function currentFilter() {
    return {
      keyword: search.value,
      programId: programFilter.value ? Number(programFilter.value) : null,
      yearLevel: yearFilter.value ? Number(yearFilter.value) : null,
      status: statusFilter.value || null,
    };
  }

  function clearFilters() {
    search.value = '';
    programFilter.value = '';
    yearFilter.value = '';
    statusFilter.value = '';
    reloadDebounced.cancel();
    reload();
  }

  function reload() {
    const rows = ctx.students.search(currentFilter());
    gpas = ctx.enrollments.gpaByStudent();
    table.setRows(rows);
    const total = ctx.students.count();
    subtitle.textContent = total + ' students enrolled across ' + (programFilter.options.length - 1) + ' programs';
    countLabel.textContent = rows.length === total
      ? 'Showing all ' + total + ' students · double-click a row to edit · click a header to sort'
      : 'Showing ' + rows.length + ' of ' + total + ' students';
    updateActions(table.selected());
  }

  function updateActions(selected) {
    const has = !!selected;
    recordBtn.disabled = editBtn.disabled = deleteBtn.disabled = !has;
  }

  async function createNew() {
    const saved = await studentDialog(ctx, null);
    if (saved) {
      reload();
      table.select(saved.id);
      app.flash('Added ' + fullName(saved) + ' (' + saved.studentNumber + ')');
    }
  }

  async function editSelected() {
    const s = table.selected();
    if (!s) return;
    const saved = await studentDialog(ctx, s);
    if (saved) {
      reload();
      table.select(saved.id);
      app.flash('Saved changes to ' + fullName(saved));
    }
  }

  async function deleteSelected() {
    const s = table.selected();
    if (!s) return;
    const enrollments = ctx.enrollments.academicRecord(s.id).enrollments.length;
    const ok = await confirmDialog({
      title: 'Delete student', confirmLabel: 'Delete', danger: true,
      html: 'Delete <b>' + escapeHtml(fullName(s)) + '</b> (' + escapeHtml(s.studentNumber) + ')?'
        + (enrollments > 0 ? '<br>Their ' + enrollments + ' enrollment record(s) and grades will also be removed.' : '')
        + '<br><br>This cannot be undone.',
    });
    if (!ok) return;
    try {
      ctx.students.delete(s.id);
      reload();
      app.flash('Deleted ' + fullName(s));
    } catch (e) {
      if (e instanceof BusinessRuleError) { await alertDialog({ title: 'Cannot delete student', html: escapeHtml(e.message) }); reload(); } else throw e;
    }
  }

  function openRecord() {
    const s = table.selected();
    if (s) openStudentRecord(ctx, s.id, { onChange: reload });
  }

  function csvText() {
    const rows = table.visibleRows().map((s) => [s.studentNumber, s.lastName, s.firstName, s.program.code, String(s.yearLevel), s.email,
      s.phone ?? '', s.birthDate ?? '', STUDENT_STATUS_LABELS[s.status], gpas.has(s.id) ? formatGrade(gpas.get(s.id)) : '']);
    return toCsv(['Student No.', 'Last Name', 'First Name', 'Program', 'Year Level', 'Email', 'Phone', 'Birth Date', 'Status', 'GPA'], rows);
  }

  function exportCsv() {
    const text = csvText();
    downloadText('students-' + isoToday() + '.csv', text);
    app.flash('Exported ' + table.visibleRows().length + ' row(s) to students-' + isoToday() + '.csv');
    return text;
  }

  return {
    el,
    refresh() { populatePrograms(); reload(); },
    createNew,
    focusSearch() { search.focus(); search.select(); },
    exportCsv,
    csvText,
    table,
  };
}
