// Sortable, keyboard-navigable data table with single-row selection (JTable + TableRowSorter in Swing).
import { h, clear } from './dom.js';

/**
 * columns: [{ key, label, render(row) -> Node|string, sortValue(row), align: 'right'|'center', sortable: true, className }]
 * options: { rowKey(row), onSelect(row|null), onActivate(row), onDelete(row), emptyText, minWidth, defaultSort: {key, asc} }
 */
export class DataTable {
  constructor(columns, options = {}) {
    this.columns = columns;
    this.options = options;
    this.rows = [];
    this.sort = options.defaultSort || null;
    this.selectedKey = null;
    this.rowKey = options.rowKey || ((r) => r.id);

    this.thead = h('thead', h('tr', columns.map((c, i) => this.headerCell(c, i))));
    this.tbody = h('tbody');
    this.table = h('table.data', { role: 'grid', 'aria-rowcount': 0, style: options.minWidth ? { '--table-min': options.minWidth + 'px' } : null }, this.thead, this.tbody);
    this.el = h('div.card.table-card', h('div.table-wrap', this.table));
    this.table.addEventListener('keydown', (e) => this.onKey(e));
  }

  headerCell(c, i) {
    const sortable = c.sortable !== false;
    const th = h('th', {
      class: [c.align, sortable ? 'sortable' : ''].filter(Boolean).join(' '),
      scope: 'col', tabindex: sortable ? 0 : null, 'data-col': i,
      onClick: sortable ? () => this.toggleSort(c.key) : null,
      onKeydown: sortable ? (e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); this.toggleSort(c.key); } } : null,
    }, c.label, h('span.sort-ind', { 'aria-hidden': 'true' }));
    return th;
  }

  toggleSort(key) {
    if (this.sort && this.sort.key === key) this.sort = { key, asc: !this.sort.asc };
    else this.sort = { key, asc: true };
    this.render();
  }

  setRows(rows) {
    this.rows = rows;
    this.render();
  }

  sortedRows() {
    if (!this.sort) return this.rows.slice();
    const col = this.columns.find((c) => c.key === this.sort.key);
    if (!col) return this.rows.slice();
    const val = col.sortValue || ((r) => r[col.key]);
    const dir = this.sort.asc ? 1 : -1;
    return this.rows.map((r, i) => ({ r, i, v: val(r) })).sort((a, b) => {
      const av = a.v, bv = b.v;
      // nulls (e.g. no GPA) always sort last
      if (av == null && bv == null) return a.i - b.i;
      if (av == null) return 1;
      if (bv == null) return -1;
      let c = typeof av === 'number' && typeof bv === 'number' ? av - bv : String(av).localeCompare(String(bv), undefined, { numeric: true, sensitivity: 'base' });
      if (c === 0) c = a.i - b.i;
      return c * dir;
    }).map((x) => x.r);
  }

  render() {
    const sorted = this.sortedRows();
    this.view = sorted;
    for (const th of this.thead.querySelectorAll('th')) {
      const c = this.columns[Number(th.dataset.col)];
      const ind = th.querySelector('.sort-ind');
      if (this.sort && c.key === this.sort.key) {
        th.setAttribute('aria-sort', this.sort.asc ? 'ascending' : 'descending');
        ind.textContent = this.sort.asc ? '▲' : '▼';
      } else {
        th.removeAttribute('aria-sort');
        ind.textContent = '';
      }
    }
    clear(this.tbody);
    this.table.setAttribute('aria-rowcount', sorted.length);
    if (!sorted.length) {
      this.tbody.append(h('tr', h('td.empty', { colspan: this.columns.length }, this.options.emptyText || 'Nothing to show.')));
      return;
    }
    let hasSelection = false;
    sorted.forEach((row, idx) => {
      const key = this.rowKey(row);
      const selected = key === this.selectedKey;
      hasSelection ||= selected;
      const tr = h('tr', {
        'data-key': key, 'aria-selected': selected ? 'true' : 'false', tabindex: selected || (idx === 0 && this.selectedKey == null) ? 0 : -1,
        onClick: () => this.select(key, true),
        onDblclick: () => this.options.onActivate && this.options.onActivate(row),
      }, this.columns.map((c) => {
        const content = c.render ? c.render(row) : row[c.key];
        return h('td', { class: [c.align, c.className].filter(Boolean).join(' ') }, content);
      }));
      this.tbody.append(tr);
    });
    if (!hasSelection && this.selectedKey != null) {
      this.selectedKey = null;
      this.options.onSelect && this.options.onSelect(null);
    }
  }

  select(key, focus = false) {
    this.selectedKey = key;
    for (const tr of this.tbody.querySelectorAll('tr[data-key]')) {
      const on = String(tr.dataset.key) === String(key);
      tr.setAttribute('aria-selected', on ? 'true' : 'false');
      tr.tabIndex = on ? 0 : -1;
      if (on && focus) tr.focus({ preventScroll: false });
    }
    this.options.onSelect && this.options.onSelect(this.selected());
  }

  selected() {
    if (this.selectedKey == null) return null;
    return this.rows.find((r) => String(this.rowKey(r)) === String(this.selectedKey)) || null;
  }

  onKey(e) {
    const rows = [...this.tbody.querySelectorAll('tr[data-key]')];
    if (!rows.length) return;
    const current = rows.findIndex((tr) => tr === document.activeElement);
    const move = (i) => { const tr = rows[Math.max(0, Math.min(rows.length - 1, i))]; this.select(tr.dataset.key, true); };
    switch (e.key) {
      case 'ArrowDown': e.preventDefault(); move(current < 0 ? 0 : current + 1); break;
      case 'ArrowUp': e.preventDefault(); move(current < 0 ? 0 : current - 1); break;
      case 'Home': e.preventDefault(); move(0); break;
      case 'End': e.preventDefault(); move(rows.length - 1); break;
      case 'Enter': if (current >= 0) { e.preventDefault(); this.select(rows[current].dataset.key); this.options.onActivate && this.options.onActivate(this.selected()); } break;
      case ' ': if (current >= 0) { e.preventDefault(); this.select(rows[current].dataset.key); } break;
      case 'Delete': if (current >= 0 && this.options.onDelete) { e.preventDefault(); this.select(rows[current].dataset.key); this.options.onDelete(this.selected()); } break;
      default:
    }
  }

  /** Current view order, e.g. for CSV export. */
  visibleRows() {
    return this.view || this.sortedRows();
  }
}
