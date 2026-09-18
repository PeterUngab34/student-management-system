// Entry point: loads sql.js, restores or creates the database, wires the shell (sidebar, theme,
// banner, status bar, keyboard shortcuts) and mounts the page for the current route.
import { openDatabase } from './db.js';
import { storage } from './storage.js';
import { createServices } from './domain/index.js';
import { h, clear, icon } from './ui/dom.js';
import { confirmDialog, alertDialog } from './ui/dialogs.js';
import { dashboardPage } from './ui/pages/dashboard.js';
import { studentsPage } from './ui/pages/students.js';
import { coursesPage } from './ui/pages/courses.js';
import { enrollmentsPage } from './ui/pages/enrollments.js';
import { consolePage } from './ui/pages/console.js';

const THEME_KEY = 'sms-web-theme';
const BANNER_KEY = 'sms-web-banner-dismissed';
/** Bump when the schema changes in a way that makes saved databases incompatible. */
const SCHEMA_VERSION = 1;
const VERSION_KEY = 'sms-web-schema-version';

const PAGES = [
  { id: 'dashboard', label: 'Dashboard', icon: 'dashboard', make: dashboardPage },
  { id: 'students', label: 'Students', icon: 'students', make: studentsPage },
  { id: 'courses', label: 'Courses', icon: 'courses', make: coursesPage },
  { id: 'enrollments', label: 'Enrollments & Grades', icon: 'enrollments', make: enrollmentsPage },
  { id: 'sql', label: 'SQL Console', icon: 'console', make: consolePage },
];

const app = {
  ctx: null,
  pages: {},
  current: null,
  ready: false,
  saveState: 'idle',

  flash(message) {
    const el = document.getElementById('status-flash');
    el.textContent = message;
    clearTimeout(this.flashTimer);
    this.flashTimer = setTimeout(() => { if (el.textContent === message) el.textContent = ''; }, 6000);
  },

  navigate(id, opts = {}) {
    if (location.hash !== '#/' + id) location.hash = '#/' + id;
    this.showPage(id, opts);
  },

  showPage(id, opts = {}) {
    const def = PAGES.find((p) => p.id === id) || PAGES[0];
    const page = this.pages[def.id] ??= def.make(this.ctx, this);
    const container = document.getElementById('page');
    if (this.current !== page) {
      clear(container);
      container.append(page.el);
      this.current = page;
    }
    page.refresh();
    document.title = def.label + ' · Student Management System';
    for (const a of document.querySelectorAll('#nav a')) {
      if (a.dataset.page === def.id) a.setAttribute('aria-current', 'page'); else a.removeAttribute('aria-current');
    }
    closeDrawer();
    if (opts.create && page.createNew) page.createNew();
  },

  async resetData() {
    const ok = await confirmDialog({
      title: 'Reset sample data', confirmLabel: 'Reset', danger: true,
      html: 'Discard every change made in this browser and reload the original sample data (4 programs, 25 students, 10 courses, 153 enrollments)?',
    });
    if (!ok) return false;
    clearTimeout(saveTimer);
    await storage.clear();
    this.ctx.db.close();
    await boot({ fresh: true });
    this.flash('Sample data restored');
    return true;
  },
};

let saveTimer = null;
async function persist() {
  clearTimeout(saveTimer);
  saveTimer = setTimeout(async () => {
    app.saveState = 'saving';
    let bytes;
    try { bytes = app.ctx.db.export(); } catch { return; } // database was closed (reset in progress)
    const ok = await storage.save(bytes);
    app.saveState = ok ? 'saved' : 'unsaved';
    app.lastSaved = ok ? Date.now() : app.lastSaved;
    updateStatus();
  }, 150);
}

function updateStatus() {
  const text = document.getElementById('status-db-text');
  const where = storage.mode === 'indexeddb' ? 'saved locally' : storage.mode === 'localstorage' ? 'saved locally (localStorage)' : 'not saved: storage unavailable';
  text.textContent = 'SQLite (sql.js) in your browser · ' + where;
  document.querySelector('.status-dot').style.background = storage.mode === 'memory' ? 'var(--warning)' : 'var(--success)';
}

async function loadScripts() {
  const [schema, seed] = await Promise.all([
    fetch('sql/schema.sqlite.sql').then((r) => { if (!r.ok) throw new Error('schema.sqlite.sql: HTTP ' + r.status); return r.text(); }),
    fetch('sql/seed.sqlite.sql').then((r) => { if (!r.ok) throw new Error('seed.sqlite.sql: HTTP ' + r.status); return r.text(); }),
  ]);
  return { schema, seed };
}

let SQL = null;
async function boot({ fresh = false } = {}) {
  SQL ??= await initSqlJs({ locateFile: (file) => 'vendor/sqljs/' + file });
  const scripts = await loadScripts();
  let bytes = null;
  if (!fresh) {
    bytes = await storage.load();
    let version = null;
    try { version = Number(localStorage.getItem(VERSION_KEY)); } catch { /* ignore */ }
    if (bytes && version !== SCHEMA_VERSION) bytes = null; // stale layout: start over from the seed
  }
  let db;
  try {
    db = openDatabase(SQL, { bytes, schema: scripts.schema, seed: scripts.seed });
    if (bytes) db.queryInt('SELECT COUNT(*) FROM enrollments'); // sanity check the restored file
  } catch (e) {
    console.warn('Saved database could not be opened, starting from the seed:', e);
    db = openDatabase(SQL, { schema: scripts.schema, seed: scripts.seed });
    bytes = null;
  }
  try { localStorage.setItem(VERSION_KEY, String(SCHEMA_VERSION)); } catch { /* ignore */ }
  db.onChange(persist);
  app.ctx = createServices(db);
  app.pages = {};
  app.current = null;
  app.restored = !!bytes;
  if (!bytes) await storage.save(db.export());
  updateStatus();
  route();
  app.ready = true;
}

function route() {
  const id = (location.hash.replace(/^#\/?/, '') || 'dashboard').split('?')[0];
  app.showPage(id);
}

// ---- shell ---------------------------------------------------------------------------

function applyTheme(theme) {
  document.documentElement.setAttribute('data-theme', theme);
  document.querySelector('meta[name="theme-color"]').setAttribute('content', theme === 'dark' ? '#0b0f17' : '#f7f8fb');
  try { localStorage.setItem(THEME_KEY, theme); } catch { /* ignore */ }
  const dark = theme === 'dark';
  const btn = document.getElementById('theme-toggle');
  clear(btn);
  btn.append(icon(dark ? 'sun' : 'moon', 18), h('span', dark ? 'Light mode' : 'Dark mode'), h('span.shortcut', 'Ctrl+T'));
  btn.setAttribute('aria-pressed', dark ? 'false' : 'true');
  const top = document.getElementById('theme-toggle-top');
  clear(top);
  top.append(icon(dark ? 'sun' : 'moon', 20));
}

function toggleTheme() {
  const next = document.documentElement.getAttribute('data-theme') === 'dark' ? 'light' : 'dark';
  applyTheme(next);
  app.flash((next === 'light' ? 'Light' : 'Dark') + ' theme enabled');
}

function openDrawer() {
  document.getElementById('app').classList.add('drawer-open');
  document.getElementById('backdrop').hidden = false;
  document.getElementById('menu-toggle').setAttribute('aria-expanded', 'true');
  document.querySelector('#nav a[aria-current="page"], #nav a')?.focus();
}

function closeDrawer() {
  const wasOpen = document.getElementById('app').classList.contains('drawer-open');
  document.getElementById('app').classList.remove('drawer-open');
  document.getElementById('backdrop').hidden = true;
  document.getElementById('menu-toggle').setAttribute('aria-expanded', 'false');
  if (wasOpen) document.getElementById('menu-toggle').focus();
}

function isDrawerOpen() {
  return document.getElementById('app').classList.contains('drawer-open');
}

function buildNav() {
  const nav = document.getElementById('nav');
  nav.append(...PAGES.map((p, i) => h('a', { href: '#/' + p.id, 'data-page': p.id }, icon(p.icon, 18), h('span', p.label), h('span.shortcut', 'Ctrl+' + (i + 1)))));
}

function shortcutsDialog() {
  const rows = [
    ['Ctrl+1 … Ctrl+5', 'Dashboard · Students · Courses · Enrollments · SQL Console'],
    ['Ctrl+N', 'Add a student / course / enrollment on the current page'],
    ['Ctrl+F', 'Focus the search box'],
    ['Ctrl+E', 'Export the current table to CSV'],
    ['Ctrl+T', 'Toggle light / dark theme'],
    ['↑ ↓ Home End', 'Move the selection in a table'],
    ['Enter', 'Edit the selected row (record a grade on the Enrollments page)'],
    ['Delete', 'Delete the selected row'],
    ['Escape', 'Close a dialog or the navigation drawer'],
    ['?', 'Show this list'],
  ];
  return alertDialog({
    title: 'Keyboard shortcuts',
    html: '<div class="shortcuts">' + rows.map(([k, d]) => '<span><kbd>' + k + '</kbd></span><span>' + d + '</span>').join('') + '</div>',
    label: 'Close',
  });
}

function installShortcuts() {
  document.addEventListener('keydown', (e) => {
    const inDialog = !!document.querySelector('dialog[open]');
    if (e.key === 'Escape' && !inDialog && isDrawerOpen()) { e.preventDefault(); closeDrawer(); return; }
    const editing = /^(INPUT|TEXTAREA|SELECT)$/.test(document.activeElement?.tagName || '');
    if (e.key === '?' && !editing && !inDialog) { e.preventDefault(); shortcutsDialog(); return; }
    if (!(e.ctrlKey || e.metaKey) || e.altKey || inDialog) return;
    const n = Number(e.key);
    if (n >= 1 && n <= PAGES.length) { e.preventDefault(); app.navigate(PAGES[n - 1].id); return; }
    switch (e.key.toLowerCase()) {
      case 'n': if (app.current?.createNew) { e.preventDefault(); app.current.createNew(); } break;
      case 'f': if (app.current?.focusSearch) { e.preventDefault(); app.current.focusSearch(); } break;
      case 'e': if (app.current?.exportCsv) { e.preventDefault(); app.current.exportCsv(); } break;
      case 't': e.preventDefault(); toggleTheme(); break;
      default:
    }
  });
}

function installShell() {
  buildNav();
  let theme = 'dark';
  try { theme = localStorage.getItem(THEME_KEY) || 'dark'; } catch { /* ignore */ }
  applyTheme(theme === 'light' ? 'light' : 'dark');
  document.getElementById('theme-toggle').addEventListener('click', toggleTheme);
  document.getElementById('theme-toggle-top').addEventListener('click', toggleTheme);
  document.getElementById('reset-data').addEventListener('click', () => app.resetData());
  document.getElementById('menu-toggle').addEventListener('click', () => (isDrawerOpen() ? closeDrawer() : openDrawer()));
  document.getElementById('backdrop').addEventListener('click', closeDrawer);
  document.querySelector('.status-right').addEventListener('click', shortcutsDialog);
  document.querySelector('.status-right').style.cursor = 'pointer';

  const banner = document.getElementById('banner');
  let dismissed = false;
  try { dismissed = localStorage.getItem(BANNER_KEY) === '1'; } catch { /* ignore */ }
  banner.hidden = dismissed;
  document.getElementById('banner-close').addEventListener('click', () => {
    banner.hidden = true;
    try { localStorage.setItem(BANNER_KEY, '1'); } catch { /* ignore */ }
  });

  window.addEventListener('hashchange', route);
  installShortcuts();
}

installShell();
boot().catch((e) => {
  console.error(e);
  const page = document.getElementById('page');
  clear(page);
  page.append(h('div.error-box', h('h2', 'The database could not be loaded'), h('p', { style: { marginTop: '8px' } }, String(e.message || e)),
    h('p.muted', { style: { marginTop: '8px' } }, 'This page needs WebAssembly and must be served over HTTP (not opened as a file).')));
  document.getElementById('status-db-text').textContent = 'Database failed to load';
});

// Debug / test hook: window.sms.ctx gives access to the services and the live database.
window.sms = app;
