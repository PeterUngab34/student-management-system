// SQL console: run read-only SELECT / WITH queries against the live SQLite database.
import { h, clear, icon } from '../dom.js';

export const EXAMPLES = [
  {
    title: 'Students with their program',
    desc: 'JOIN students to programs',
    sql: `SELECT s.student_number, s.last_name, s.first_name, p.code AS program, s.year_level, s.status
FROM students s
JOIN programs p ON p.program_id = s.program_id
ORDER BY s.last_name, s.first_name;`,
  },
  {
    title: 'GPA per program',
    desc: 'GROUP BY with a unit-weighted average',
    sql: `-- GPA = sum(grade x units) / sum(units) over completed courses (lower is better)
SELECT p.code AS program,
       COUNT(DISTINCT s.student_id) AS students,
       ROUND(SUM(e.grade * c.units) * 1.0 / SUM(c.units), 2) AS weighted_gpa,
       SUM(c.units) AS graded_units
FROM programs p
JOIN students s    ON s.program_id = p.program_id
JOIN enrollments e ON e.student_id = s.student_id AND e.status = 'COMPLETED'
JOIN courses c     ON c.course_id = e.course_id
GROUP BY p.code
ORDER BY weighted_gpa;`,
  },
  {
    title: "Dean's List candidates",
    desc: 'HAVING on a computed GPA (1.75 or better)',
    sql: `SELECT s.student_number, s.first_name || ' ' || s.last_name AS student, p.code AS program,
       ROUND(SUM(e.grade * c.units) * 1.0 / SUM(c.units), 2) AS gpa
FROM students s
JOIN programs p    ON p.program_id = s.program_id
JOIN enrollments e ON e.student_id = s.student_id AND e.status = 'COMPLETED'
JOIN courses c     ON c.course_id = e.course_id
GROUP BY s.student_id
HAVING gpa <= 1.75
ORDER BY gpa, s.last_name;`,
  },
  {
    title: 'Grade distribution',
    desc: 'COUNT per grade bucket',
    sql: `SELECT printf('%.2f', grade) AS grade, COUNT(*) AS enrollments
FROM enrollments
WHERE status = 'COMPLETED'
GROUP BY grade
ORDER BY grade;`,
  },
  {
    title: 'Current term course load',
    desc: 'Enrollments per course this term',
    sql: `SELECT c.course_code, c.title, COUNT(*) AS enrolled
FROM enrollments e
JOIN courses c ON c.course_id = e.course_id
WHERE e.school_year = '2026-2027' AND e.semester = 'FIRST' AND e.status <> 'DROPPED'
GROUP BY c.course_id
ORDER BY enrolled DESC, c.course_code;`,
  },
  {
    title: 'Failed and retaken courses',
    desc: 'Self-join on enrollments (CTE)',
    sql: `WITH failed AS (
  SELECT student_id, course_id, school_year, semester
  FROM enrollments WHERE grade = 5.00
)
SELECT s.student_number, s.last_name, c.course_code,
       f.school_year || ' ' || f.semester AS failed_in,
       r.school_year || ' ' || r.semester AS retaken_in, r.status AS retake_status
FROM failed f
JOIN students s ON s.student_id = f.student_id
JOIN courses c  ON c.course_id = f.course_id
LEFT JOIN enrollments r ON r.student_id = f.student_id AND r.course_id = f.course_id
                       AND r.school_year > f.school_year;`,
  },
  {
    title: 'Schema',
    desc: 'Tables and their DDL from sqlite_master',
    sql: `SELECT name, sql FROM sqlite_master WHERE type IN ('table', 'index') AND name NOT LIKE 'sqlite_%' ORDER BY type DESC, name;`,
  },
];

/** Accepts one SELECT/WITH statement. Comments are stripped before the check; multiple statements are rejected. */
export function checkReadOnly(sql) {
  const stripped = sql.replace(/--[^\n]*/g, '').replace(/\/\*[\s\S]*?\*\//g, '').trim().replace(/;\s*$/, '');
  if (!stripped) return { ok: false, error: 'Enter a query.' };
  if (!/^(SELECT|WITH|EXPLAIN|PRAGMA\s+(table_info|index_list|foreign_key_list|table_list))\b/i.test(stripped)) {
    return { ok: false, error: 'Only read-only SELECT / WITH queries can run here. Use the pages on the left to change data.' };
  }
  if (/;/.test(stripped.replace(/'[^']*'/g, ''))) {
    return { ok: false, error: 'Run one statement at a time.' };
  }
  return { ok: true, sql: stripped };
}

export function consolePage(ctx, app) {
  const editor = h('textarea.textarea', { id: 'sql-input', rows: 8, spellcheck: 'false', 'aria-label': 'SQL query', placeholder: 'SELECT … FROM students …' });
  editor.value = EXAMPLES[0].sql;
  const meta = h('span.console-meta', { id: 'sql-meta', 'aria-live': 'polite' }, 'Read-only · SELECT and WITH only · Ctrl+Enter to run');
  const results = h('div.results', { id: 'sql-results' });
  const errorBox = h('div.sql-error', { id: 'sql-error', role: 'alert', hidden: true });
  const runBtn = h('button.btn.btn-primary', { type: 'button', id: 'sql-run', onClick: run }, icon('play', 14), 'Run query');

  const schema = h('div.schema-list', ctx.db.query("SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%' ORDER BY rowid").map((t) => {
    const cols = ctx.db.query('PRAGMA table_info(' + t.name + ')').map((c) => c.name).join(', ');
    return h('div', h('b', t.name), ' (' + cols + ')');
  }));

  const el = h('div',
    h('div.page-header', h('div', h('h1.page-title', 'SQL Console'), h('p.page-sub', 'Query the live SQLite database that powers this page. Same schema as sql/schema.sql; the data is what you see on the other pages.'))),
    h('div.console-grid',
      h('div',
        h('div.card.card-pad.console-editor',
          h('label.sr-only', { for: 'sql-input' }, 'SQL query'),
          editor,
          h('div.console-actions', runBtn,
            h('button.btn', { type: 'button', onClick: () => { editor.value = ''; editor.focus(); } }, 'Clear'),
            meta)),
        errorBox,
        h('div', { style: { marginTop: '14px' } }, results)),
      h('div',
        h('div.card.card-pad', h('h2.card-title', 'Examples'), h('p.card-sub', 'Click to load, then run'),
          h('div.examples', { style: { marginTop: '12px' } }, EXAMPLES.map((ex) => h('button.example-btn', { type: 'button', onClick: () => { editor.value = ex.sql; run(); } },
            h('span.ex-title', ex.title), h('span.ex-desc', ex.desc))))),
        h('div.card.card-pad', { style: { marginTop: '18px' } }, h('h2.card-title', 'Tables'), h('div', { style: { marginTop: '8px' } }, schema)))));

  editor.addEventListener('keydown', (e) => { if ((e.ctrlKey || e.metaKey) && e.key === 'Enter') { e.preventDefault(); run(); } });

  function run() {
    errorBox.hidden = true;
    errorBox.textContent = '';
    clear(results);
    const check = checkReadOnly(editor.value);
    if (!check.ok) { showError(check.error); return; }
    const started = performance.now();
    let out;
    try {
      out = ctx.db.select(check.sql, { maxRows: 500 });
    } catch (e) {
      showError(e.message.replace(/^Query failed: /, ''));
      return;
    }
    const ms = (performance.now() - started).toFixed(1);
    meta.textContent = out.rows.length + (out.truncated ? '+' : '') + ' row(s) · ' + out.columns.length + ' column(s) · ' + ms + ' ms' + (out.truncated ? ' · showing the first 500 rows' : '');
    results.append(h('div.card.table-card', h('div.table-wrap',
      h('table.data', { 'aria-label': 'Query results' },
        h('thead', h('tr', out.columns.map((c) => h('th', { scope: 'col' }, c)))),
        h('tbody', out.rows.length ? out.rows.map((r) => h('tr', r.map((v) => h('td', { class: v == null ? 'null' : typeof v === 'number' ? 'right num' : '' }, v == null ? 'NULL' : String(v)))))
          : h('tr', h('td.empty', { colspan: Math.max(1, out.columns.length) }, 'The query returned no rows.')))))));
  }

  function showError(message) {
    errorBox.textContent = message;
    errorBox.hidden = false;
    meta.textContent = 'Query failed';
  }

  return { el, refresh() {}, focusSearch() { editor.focus(); }, run };
}
