// Course catalogue CRUD (CoursesView in Java).
import { h, icon, debounce, escapeHtml, downloadText, isoToday } from '../dom.js';
import { DataTable } from '../table.js';
import { confirmDialog, alertDialog } from '../dialogs.js';
import { courseDialog } from '../forms.js';
import { BusinessRuleError } from '../../domain/errors.js';
import { toCsv } from '../../domain/csv.js';

export function coursesPage(ctx, app) {
  let counts = new Map();
  const search = h('input.input', { type: 'search', placeholder: 'Search code or title', 'aria-label': 'Search courses', autocomplete: 'off', id: 'courses-search' });
  const subtitle = h('p.page-sub');
  const countLabel = h('span', { id: 'courses-count' });
  const editBtn = h('button.btn.btn-ghost', { type: 'button', disabled: true, onClick: () => editSelected() }, icon('edit', 16), 'Edit');
  const deleteBtn = h('button.btn.btn-ghost', { type: 'button', disabled: true, id: 'courses-delete', onClick: () => deleteSelected() }, icon('trash', 16), 'Delete');

  const table = new DataTable([
    { key: 'code', label: 'Code' },
    { key: 'title', label: 'Title' },
    { key: 'units', label: 'Units', align: 'center' },
    { key: 'enrollments', label: 'Enrollments', align: 'center', sortValue: (c) => counts.get(c.id) || 0, render: (c) => String(counts.get(c.id) || 0) },
    { key: 'description', label: 'Description', className: 'wrap', render: (c) => c.description ?? '' },
  ], { minWidth: 820, emptyText: 'No courses match your search.', onSelect: updateActions, onActivate: () => editSelected(), onDelete: () => deleteSelected() });

  const el = h('div',
    h('div.page-header',
      h('div', h('h1.page-title', 'Courses'), subtitle),
      h('div.page-actions',
        h('button.btn', { type: 'button', title: 'Export the rows currently shown (Ctrl+E)', onClick: () => exportCsv() }, icon('export', 16), 'Export CSV'),
        h('button.btn.btn-primary', { type: 'button', id: 'courses-add', title: 'Add a new course (Ctrl+N)', onClick: () => createNew() }, icon('plus', 16), 'Add Course'))),
    h('div.toolbar', h('div.search-wrap.search', icon('search', 16), search)),
    table.el,
    h('div.footer-row', countLabel, h('span.spacer'), editBtn, deleteBtn));

  search.addEventListener('input', debounce(reload, 200));

  function reload() {
    const rows = ctx.courses.search(search.value);
    counts = ctx.courses.enrollmentCounts();
    table.setRows(rows);
    const total = ctx.courses.count();
    subtitle.textContent = total + ' courses in the catalogue';
    countLabel.textContent = rows.length === total ? 'Showing all ' + total + ' courses' : 'Showing ' + rows.length + ' of ' + total + ' courses';
    updateActions(table.selected());
  }

  function updateActions(selected) {
    editBtn.disabled = deleteBtn.disabled = !selected;
  }

  async function createNew() {
    const saved = await courseDialog(ctx, null);
    if (saved) { reload(); table.select(saved.id); app.flash('Added course ' + saved.code); }
  }

  async function editSelected() {
    const c = table.selected();
    if (!c) return;
    const saved = await courseDialog(ctx, c);
    if (saved) { reload(); table.select(saved.id); app.flash('Saved changes to ' + saved.code); }
  }

  async function deleteSelected() {
    const c = table.selected();
    if (!c) return;
    const ok = await confirmDialog({
      title: 'Delete course', confirmLabel: 'Delete', danger: true,
      html: 'Delete <b>' + escapeHtml(c.code + ' – ' + c.title) + '</b>?<br><br>This cannot be undone.',
    });
    if (!ok) return;
    try {
      ctx.courses.delete(c.id);
      reload();
      app.flash('Deleted course ' + c.code);
    } catch (e) {
      if (e instanceof BusinessRuleError) await alertDialog({ title: 'Cannot delete course', html: escapeHtml(e.message) }); else throw e;
    }
  }

  function csvText() {
    const rows = table.visibleRows().map((c) => [c.code, c.title, String(c.units), String(counts.get(c.id) || 0), c.description ?? '']);
    return toCsv(['Code', 'Title', 'Units', 'Enrollments', 'Description'], rows);
  }

  function exportCsv() {
    const text = csvText();
    downloadText('courses-' + isoToday() + '.csv', text);
    app.flash('Exported ' + table.visibleRows().length + ' row(s) to courses-' + isoToday() + '.csv');
    return text;
  }

  return { el, refresh: reload, createNew, focusSearch() { search.focus(); search.select(); }, exportCsv, csvText, table };
}
