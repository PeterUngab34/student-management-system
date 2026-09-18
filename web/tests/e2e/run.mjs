#!/usr/bin/env node
// End-to-end check in headless Chrome over the DevTools protocol (no extra dependencies).
// Serves the repository so that /student-management-system/ maps to web/ (mirroring GitHub Pages),
// drives the app, asserts behaviour, and writes screenshots to tests/e2e/out/ (+ docs/screenshot.png with --docs).
//
//   node tests/e2e/run.mjs [--docs] [--chrome "C:/path/to/chrome.exe"]

import { createServer } from 'node:http';
import { readFile, mkdir, writeFile, rm, mkdtemp } from 'node:fs/promises';
import { existsSync } from 'node:fs';
import { spawn } from 'node:child_process';
import { dirname, resolve, join, extname } from 'node:path';
import { tmpdir } from 'node:os';
import { fileURLToPath } from 'node:url';
import assert from 'node:assert/strict';

const here = dirname(fileURLToPath(import.meta.url));
const WEB = resolve(here, '../..');
const OUT = join(here, 'out');
const BASE_PATH = '/student-management-system/';
const args = process.argv.slice(2);
const WRITE_DOCS = args.includes('--docs');
const CHROME = args.includes('--chrome') ? args[args.indexOf('--chrome') + 1]
  : process.env.CHROME || ['C:/Program Files/Google/Chrome/Application/chrome.exe', 'C:/Program Files (x86)/Google/Chrome/Application/chrome.exe',
    '/usr/bin/google-chrome', '/usr/bin/chromium-browser', '/usr/bin/chromium', '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome'].find(existsSync);

const MIME = { '.html': 'text/html; charset=utf-8', '.js': 'text/javascript; charset=utf-8', '.mjs': 'text/javascript; charset=utf-8', '.css': 'text/css; charset=utf-8',
  '.wasm': 'application/wasm', '.svg': 'image/svg+xml', '.sql': 'text/plain; charset=utf-8', '.json': 'application/json', '.png': 'image/png', '.md': 'text/markdown' };

function startServer() {
  return new Promise((res) => {
    const server = createServer(async (req, resp) => {
      const url = new URL(req.url, 'http://x');
      if (!url.pathname.startsWith(BASE_PATH)) { resp.writeHead(404); resp.end('not under ' + BASE_PATH); return; }
      let rel = decodeURIComponent(url.pathname.slice(BASE_PATH.length)) || 'index.html';
      if (rel.endsWith('/')) rel += 'index.html';
      const file = resolve(WEB, rel);
      if (!file.startsWith(WEB)) { resp.writeHead(403); resp.end(); return; }
      try {
        const data = await readFile(file);
        resp.writeHead(200, { 'content-type': MIME[extname(file)] || 'application/octet-stream', 'cache-control': 'no-store' });
        resp.end(data);
      } catch {
        resp.writeHead(404); resp.end('not found: ' + rel);
      }
    });
    server.listen(0, '127.0.0.1', () => res({ server, port: server.address().port }));
  });
}

class Cdp {
  constructor(ws) { this.ws = ws; this.id = 0; this.pending = new Map(); this.handlers = []; ws.addEventListener('message', (m) => this.onMessage(JSON.parse(m.data))); }
  static async connect(url) {
    const ws = new WebSocket(url);
    await new Promise((res, rej) => { ws.addEventListener('open', res, { once: true }); ws.addEventListener('error', rej, { once: true }); });
    return new Cdp(ws);
  }
  onMessage(msg) {
    if (msg.id && this.pending.has(msg.id)) {
      const { res, rej } = this.pending.get(msg.id);
      this.pending.delete(msg.id);
      msg.error ? rej(new Error(msg.error.message)) : res(msg.result);
    } else if (msg.method) {
      for (const h of this.handlers) h(msg.method, msg.params);
    }
  }
  send(method, params = {}) {
    const id = ++this.id;
    this.ws.send(JSON.stringify({ id, method, params }));
    return new Promise((res, rej) => this.pending.set(id, { res, rej }));
  }
  on(fn) { this.handlers.push(fn); }
  async eval(expression) {
    const r = await this.send('Runtime.evaluate', { expression, awaitPromise: true, returnByValue: true });
    if (r.exceptionDetails) throw new Error('evaluate failed: ' + (r.exceptionDetails.exception?.description || r.exceptionDetails.text) + '\n  in: ' + expression.slice(0, 200));
    return r.result.value;
  }
  async waitFor(expression, { timeout = 15000, label = expression } = {}) {
    const start = Date.now();
    for (;;) {
      const v = await this.eval(expression);
      if (v) return v;
      if (Date.now() - start > timeout) throw new Error('timeout waiting for: ' + label);
      await sleep(100);
    }
  }
  async screenshot(file, { width, height, scale = 1, mobile = false } = {}) {
    if (width) await this.send('Emulation.setDeviceMetricsOverride', { width, height, deviceScaleFactor: scale, mobile });
    await sleep(250);
    const r = await this.send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: false });
    await writeFile(file, Buffer.from(r.data, 'base64'));
    return file;
  }
}

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

// ---- page helpers (run inside the browser) ----
const H = `
  window.__h = {
    q: (s) => document.querySelector(s),
    qa: (s) => [...document.querySelectorAll(s)],
    click: (s) => { const el = document.querySelector(s); if (!el) throw new Error('no element ' + s); el.click(); return true; },
    set: (s, v) => { const el = document.querySelector(s); if (!el) throw new Error('no element ' + s); el.value = v; el.dispatchEvent(new Event('input', { bubbles: true })); el.dispatchEvent(new Event('change', { bubbles: true })); return true; },
    field: (name, v) => { const el = document.querySelector('dialog[open] [name=' + name + ']'); if (!el) throw new Error('no field ' + name); el.value = v; el.dispatchEvent(new Event('input', { bubbles: true })); el.dispatchEvent(new Event('change', { bubbles: true })); return true; },
    selectByLabel: (name, text) => { const el = document.querySelector('dialog[open] [name=' + name + ']'); const o = [...el.options].find((o) => o.textContent.includes(text)); if (!o) throw new Error('no option ' + text); el.value = o.value; el.dispatchEvent(new Event('change', { bubbles: true })); return true; },
    submit: () => { document.querySelector('dialog[open] form').requestSubmit(); return true; },
    rows: () => document.querySelectorAll('#page table.data tbody tr[data-key]').length,
    errors: () => [...document.querySelectorAll('dialog[open] .field-error')].map((e) => e.textContent).filter(Boolean),
    key: (key) => { document.dispatchEvent(new KeyboardEvent('keydown', { key, bubbles: true })); return true; },
    text: (s) => document.querySelector(s)?.textContent?.trim(),
    overflow: () => document.documentElement.scrollWidth - document.documentElement.clientWidth,
  }; true`;

async function main() {
  if (!CHROME || !existsSync(CHROME)) throw new Error('Chrome not found. Pass --chrome <path> or set CHROME.');
  await mkdir(OUT, { recursive: true });
  const { server, port } = await startServer();
  const base = `http://127.0.0.1:${port}${BASE_PATH}`;
  const profile = await mkdtemp(join(tmpdir(), 'sms-e2e-'));
  const debugPort = 9222 + Math.floor(Math.random() * 500);
  const chrome = spawn(CHROME, ['--headless=new', '--disable-gpu', '--no-first-run', '--no-default-browser-check', '--hide-scrollbars',
    `--remote-debugging-port=${debugPort}`, `--user-data-dir=${profile}`, '--window-size=1400,900', 'about:blank'], { stdio: 'ignore' });
  const problems = [];
  const results = [];
  const step = (name, ok, detail = '') => { results.push({ name, ok, detail }); console.log((ok ? '  ok  ' : '  FAIL') + ' ' + name + (detail ? ' - ' + detail : '')); if (!ok) problems.push(name); };

  try {
    let targets;
    for (let i = 0; i < 50; i++) {
      try { targets = await (await fetch(`http://127.0.0.1:${debugPort}/json`)).json(); if (targets.length) break; } catch { /* retry */ }
      await sleep(200);
    }
    const page = targets.find((t) => t.type === 'page');
    const cdp = await Cdp.connect(page.webSocketDebuggerUrl);
    await cdp.send('Page.enable');
    await cdp.send('Runtime.enable');
    await cdp.send('Log.enable');
    const consoleErrors = [];
    cdp.on((method, params) => {
      if (method === 'Runtime.exceptionThrown') consoleErrors.push('exception: ' + (params.exceptionDetails.exception?.description || params.exceptionDetails.text));
      if (method === 'Runtime.consoleAPICalled' && (params.type === 'error' || params.type === 'warning')) consoleErrors.push(params.type + ': ' + params.args.map((a) => a.value ?? a.description).join(' '));
      if (method === 'Log.entryAdded' && params.entry.level === 'error') consoleErrors.push('log: ' + params.entry.text + ' ' + (params.entry.url || ''));
    });
    await cdp.send('Emulation.setDeviceMetricsOverride', { width: 1400, height: 900, deviceScaleFactor: 1, mobile: false });

    const load = async (hash = '') => {
      // go through about:blank so a hash-only difference still forces a full reload
      await cdp.send('Page.navigate', { url: 'about:blank' });
      await cdp.waitFor(`location.href === 'about:blank'`, { label: 'blank' });
      await cdp.send('Page.navigate', { url: base + hash });
      await cdp.waitFor('window.sms && window.sms.ready === true', { label: 'app ready' });
      await cdp.eval(H);
    };

    // 1. load, counts
    await load();
    step('page loads and database initializes', true);
    const stats = await cdp.eval(`['stat-students','stat-courses','stat-enrollments','stat-gpa'].map((k) => document.querySelector('[data-testid=' + k + ']').textContent)`);
    step('dashboard counts 25 / 10 / 153 and average GPA 2.01', JSON.stringify(stats) === JSON.stringify(['25', '10', '153', '2.01']), stats.join(' / '));
    step('status bar shows the SQLite mode', (await cdp.eval(`__h.text('#status-db-text')`)).startsWith('SQLite (sql.js) in your browser · saved locally'), await cdp.eval(`__h.text('#status-db-text')`));
    step('banner is visible with repo and download links', await cdp.eval(`!__h.q('#banner').hidden && __h.qa('#banner a').length === 2`));
    await cdp.screenshot(join(OUT, 'dashboard-dark.png'));

    // 2. students: search + filters
    await cdp.eval(`location.hash = '#/students'; true`);
    await cdp.waitFor(`__h.rows() === 25`, { label: '25 student rows' });
    step('students table shows 25 rows', true);
    await cdp.eval(`__h.set('#students-search', 'santos')`);
    await cdp.waitFor(`__h.rows() === 1`, { label: 'search filters to 1 row' });
    step('live search "santos" -> 1 row', (await cdp.eval(`__h.text('#page table.data tbody tr td:nth-child(2)')`)) === 'Santos, Maria');
    await cdp.eval(`__h.set('#students-search', 'ibañez')`);
    await cdp.waitFor(`__h.rows() === 1`, { label: 'search ibañez' });
    step('search with ñ finds Ibañez', true);
    await cdp.eval(`__h.set('#students-search', '')`);
    await cdp.waitFor(`__h.rows() === 25`);
    await cdp.eval(`__h.set('#students-program', [...document.querySelector('#students-program').options].find((o) => o.textContent.startsWith('BSCpE')).value)`);
    await cdp.waitFor(`__h.rows() === 10`, { label: 'program filter 10 rows' });
    step('program filter BSCpE -> 10 rows', true);
    await cdp.eval(`__h.set('#students-status', 'GRADUATED')`);
    await cdp.waitFor(`__h.rows() === 1`, { label: 'status filter' });
    step('status filter Graduated -> 1 row (Domingo)', (await cdp.eval(`__h.text('#page table.data tbody tr td:nth-child(2)')`)) === 'Domingo, Rafael');
    await cdp.eval(`__h.set('#students-program', ''); __h.set('#students-status', '')`);
    await cdp.waitFor(`__h.rows() === 25`);
    // sort by GPA (click header twice = descending)
    await cdp.eval(`[...document.querySelectorAll('#page table.data th')].find((t) => t.textContent.startsWith('GPA')).click(); true`);
    step('sort by GPA ascending puts Ramos (1.00) first', (await cdp.eval(`__h.text('#page table.data tbody tr td:nth-child(2)')`)) === 'Ramos, Kristine');
    await cdp.eval(`[...document.querySelectorAll('#page table.data th')].find((t) => t.textContent.startsWith('Name')).click(); true`);
    await cdp.screenshot(join(OUT, 'students-dark.png'));

    // 3. add student: validation errors then success
    await cdp.eval(`__h.click('#students-add')`);
    await cdp.waitFor(`!!__h.q('dialog[open] [name=studentNumber]')`, { label: 'student dialog' });
    const suggested = await cdp.eval(`__h.q('dialog[open] [name=studentNumber]').value`);
    step('add dialog suggests the next student number', suggested === '2026-00422', suggested);
    await cdp.eval(`__h.field('studentNumber', '2023-00123'); __h.field('firstName', 'Juan'); __h.field('lastName', 'Dela Cruz'); __h.field('email', 'juan.delacruz@'); __h.field('phone', '0917-555'); __h.field('birthDate', '2031-02-14'); __h.submit()`);
    await sleep(150);
    const errors = await cdp.eval(`__h.errors()`);
    step('validation errors match the desktop app', JSON.stringify(errors) === JSON.stringify([
      'Student number 2023-00123 is already in use.', 'Enter a valid e-mail address.', 'Use a PH mobile number, e.g. 09171234567.', 'Birth date cannot be in the future.']), errors.join(' | '));
    await cdp.screenshot(join(OUT, 'validation.png'));
    await cdp.eval(`__h.field('studentNumber', '2026-00422'); __h.field('email', 'juan.delacruz@student.example.edu.ph'); __h.field('phone', '09171234567'); __h.field('birthDate', '2005-06-18'); __h.submit()`);
    await cdp.waitFor(`!__h.q('dialog[open]') && __h.rows() === 26`, { label: 'student added' });
    step('new student appears (26 rows) with a status-bar flash', (await cdp.eval(`__h.text('#status-flash')`)) === 'Added Juan Dela Cruz (2026-00422)', await cdp.eval(`__h.text('#status-flash')`));
    step('new row is selected', (await cdp.eval(`__h.text('#page table.data tbody tr[aria-selected=true] td:nth-child(2)')`)) === 'Dela Cruz, Juan');

    // 4. CSV export content
    const csv = await cdp.eval(`window.sms.current.csvText()`);
    const lines = csv.split('\r\n').filter(Boolean);
    step('CSV export has BOM, header, 26 rows, quoted names', csv.charCodeAt(0) === 0xFEFF && lines[0] === '\uFEFFStudent No.,Last Name,First Name,Program,Year Level,Email,Phone,Birth Date,Status,GPA'
      && lines.length === 27 && csv.includes('Ibañez') && csv.includes('2026-00422,Dela Cruz,Juan,BSCpE,1,juan.delacruz@student.example.edu.ph,09171234567,2005-06-18,Active,'), lines.length + ' lines');

    // 5. enroll + record grade -> GPA in record view
    await cdp.eval(`location.hash = '#/enrollments'; true`);
    await cdp.waitFor(`__h.rows() === 153`, { label: '153 enrollment rows' });
    step('enrollments table shows 153 rows', true);
    await cdp.eval(`__h.click('#enrollments-add')`);
    await cdp.waitFor(`!!__h.q('dialog[open] [name=student]')`);
    await cdp.eval(`__h.selectByLabel('student', 'Dela Cruz, Juan'); __h.selectByLabel('course', 'CS 211'); __h.submit()`);
    await cdp.waitFor(`!__h.q('dialog[open]') && __h.rows() === 154`, { label: 'enrolled' });
    step('enroll succeeds (154 rows)', (await cdp.eval(`__h.text('#status-flash')`)) === 'Enrolled Juan Dela Cruz in CS 211');
    // duplicate enrollment rejected
    await cdp.eval(`__h.click('#enrollments-add')`);
    await cdp.waitFor(`!!__h.q('dialog[open] [name=student]')`);
    await cdp.eval(`__h.selectByLabel('student', 'Dela Cruz, Juan'); __h.selectByLabel('course', 'CS 211'); __h.submit()`);
    await sleep(150);
    const dup = await cdp.eval(`__h.errors()`);
    step('duplicate enrollment is rejected', dup[0] === 'Juan Dela Cruz is already enrolled in CS 211 for 1st Sem 2026-2027.', dup.join(' | '));
    await cdp.eval(`__h.key('Escape'); __h.q('dialog[open]').close(); true`);
    await cdp.waitFor(`!__h.q('dialog[open]')`);
    // record grade for the selected (new) row
    step('new enrollment is selected and gradable', await cdp.eval(`__h.text('#page table.data tbody tr[aria-selected=true] td:nth-child(2)') === 'Juan Dela Cruz' && !__h.q('#enrollments-grade').disabled`));
    await cdp.eval(`__h.click('#enrollments-grade')`);
    await cdp.waitFor(`!!__h.q('dialog[open] [name=grade]')`);
    await cdp.eval(`__h.field('grade', '175'); __h.submit()`);
    // <dialog> fires 'close' asynchronously; wait for the page to react rather than for the dialog to vanish
    await cdp.waitFor(`!__h.q('dialog[open]') && (__h.text('#status-flash') || '').startsWith('Recorded')`, { label: 'grade recorded' });
    step('grade 1.75 recorded', (await cdp.eval(`__h.text('#status-flash')`)) === 'Recorded 1.75 for Juan Dela Cruz in CS 211', await cdp.eval(`__h.text('#status-flash')`));
    step('row shows grade, Completed pill and Passed', await cdp.eval(`(() => { const tds = __h.qa('#page table.data tbody tr[aria-selected=true] td').map((t) => t.textContent.trim()); return tds[6] === '1.75' && tds[7] === 'Completed' && tds[8] === 'Passed'; })()`));
    await cdp.eval(`[...document.querySelectorAll('#page .footer-row button')].find((b) => b.textContent.trim() === 'Record').click(); true`);
    await cdp.waitFor(`!!__h.q('dialog[open] [data-testid=record-gpa]')`, { label: 'record dialog' });
    const recordGpa = await cdp.eval(`__h.text('dialog[open] [data-testid=record-gpa]')`);
    const termGpa = await cdp.eval(`__h.text('dialog[open] .term-gpa')`);
    step('academic record shows cumulative GPA 1.75 and term GPA 1.75', recordGpa === '1.75' && termGpa === '1.75', recordGpa + ' / ' + termGpa);
    await cdp.screenshot(join(OUT, 'record.png'));
    await cdp.eval(`__h.q('dialog[open]').dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true, cancelable: true })); __h.q('dialog[open]')?.close(); true`);
    await cdp.waitFor(`!__h.q('dialog[open]')`);
    // GPA visible on the students page as well
    await cdp.eval(`location.hash = '#/students'; true`);
    await cdp.waitFor(`__h.rows() === 26`);
    step('students page shows the new GPA', await cdp.eval(`__h.qa('#page table.data tbody tr').some((tr) => tr.children[1].textContent === 'Dela Cruz, Juan' && tr.children[6].textContent.trim() === '1.75')`));

    // 6. courses: delete blocked with history
    await cdp.eval(`location.hash = '#/courses'; true`);
    await cdp.waitFor(`__h.rows() === 10`, { label: '10 course rows' });
    await cdp.eval(`__h.q('#page table.data tbody tr[data-key]').click(); true`);
    await cdp.eval(`__h.click('#courses-delete')`);
    await cdp.waitFor(`!!__h.q('dialog[open] .btn-danger')`);
    await cdp.eval(`__h.click('dialog[open] .btn-danger')`);
    await cdp.waitFor(`__h.q('dialog[open]') && __h.text('dialog[open] .dlg-heading') === 'Cannot delete course'`, { label: 'blocked delete alert' });
    step('deleting a course with history is blocked', (await cdp.eval(`__h.text('dialog[open] .confirm-text')`)).includes('enrollment record(s) and cannot be deleted'));
    await cdp.eval(`__h.q('dialog[open]').close(); true`);
    step('courses still 10', await cdp.eval(`__h.rows() === 10`));

    // 7. SQL console
    await cdp.eval(`location.hash = '#/sql'; true`);
    await cdp.waitFor(`!!__h.q('#sql-run')`);
    await cdp.eval(`__h.set('#sql-input', "SELECT s.student_number, s.last_name, p.code AS program FROM students s JOIN programs p ON p.program_id = s.program_id ORDER BY s.last_name"); __h.click('#sql-run')`);
    await cdp.waitFor(`__h.qa('#sql-results tbody tr').length === 26`, { label: 'join results' });
    step('SQL console runs a JOIN (26 rows, program column)', await cdp.eval(`__h.qa('#sql-results th').map((t) => t.textContent).join(',') === 'student_number,last_name,program' && __h.text('#sql-meta').startsWith('26 row(s)')`));
    await cdp.eval(`__h.set('#sql-input', 'DELETE FROM students'); __h.click('#sql-run')`);
    step('SQL console rejects writes', await cdp.eval(`!__h.q('#sql-error').hidden && __h.text('#sql-error').includes('read-only')`));
    await cdp.eval(`__h.set('#sql-input', 'SELECT * FROM nope'); __h.click('#sql-run')`);
    step('SQL console shows SQLite errors', await cdp.eval(`!__h.q('#sql-error').hidden && __h.text('#sql-error').includes('no such table')`));
    await cdp.eval(`document.querySelectorAll('.example-btn')[1].click(); true`);
    await cdp.waitFor(`__h.qa('#sql-results tbody tr').length === 4`, { label: 'gpa per program' });
    step('example "GPA per program" groups 4 programs', true);
    await cdp.screenshot(join(OUT, 'sql.png'));

    // 8. reload -> edits persist
    await load('#/students');
    await cdp.waitFor(`__h.rows() === 26`, { label: 'persisted rows' });
    step('reload keeps the added student (26 rows, restored from IndexedDB)', await cdp.eval(`window.sms.restored === true`));

    // 9. reset -> back to 25
    await cdp.eval(`__h.click('#reset-data')`);
    await cdp.waitFor(`!!__h.q('dialog[open] .btn-danger')`);
    await cdp.eval(`__h.click('dialog[open] .btn-danger')`);
    await cdp.waitFor(`!__h.q('dialog[open]') && __h.rows() === 25`, { label: 'reset to 25' });
    step('reset sample data -> 25 students', (await cdp.eval(`window.sms.ctx.enrollments.count()`)) === 153);

    // 10. theme toggle
    await cdp.eval(`__h.click('#theme-toggle')`);
    step('theme toggle switches to light', await cdp.eval(`document.documentElement.getAttribute('data-theme') === 'light' && localStorage.getItem('sms-web-theme') === 'light'`));
    await cdp.eval(`location.hash = '#/dashboard'; true`);
    await sleep(200);
    await cdp.screenshot(join(OUT, 'dashboard-light.png'));
    await cdp.eval(`__h.key('t'); true`);
    await cdp.eval(`document.dispatchEvent(new KeyboardEvent('keydown', { key: 't', ctrlKey: true, bubbles: true })); true`);
    step('Ctrl+T toggles back to dark', await cdp.eval(`document.documentElement.getAttribute('data-theme') === 'dark'`));

    // 11. 375px: no horizontal overflow, drawer
    await cdp.send('Emulation.setDeviceMetricsOverride', { width: 375, height: 740, deviceScaleFactor: 2, mobile: true });
    for (const hash of ['dashboard', 'students', 'courses', 'enrollments', 'sql']) {
      await cdp.eval(`location.hash = '#/${hash}'; true`);
      await sleep(250);
      const overflow = await cdp.eval(`__h.overflow()`);
      step(`375px: no horizontal page overflow on ${hash}`, overflow <= 0, 'overflow ' + overflow + 'px');
    }
    await cdp.eval(`location.hash = '#/students'; true`);
    await sleep(200);
    await cdp.screenshot(join(OUT, 'mobile-students.png'));
    await cdp.eval(`__h.click('#menu-toggle')`);
    await sleep(250);
    step('375px: drawer opens', await cdp.eval(`__h.q('#app').classList.contains('drawer-open') && __h.q('#menu-toggle').getAttribute('aria-expanded') === 'true'`));
    await cdp.screenshot(join(OUT, 'mobile-drawer.png'));
    await cdp.eval(`__h.key('Escape')`);
    await sleep(250);
    step('375px: Escape closes the drawer', await cdp.eval(`!__h.q('#app').classList.contains('drawer-open') && __h.q('#backdrop').hidden`));
    await cdp.eval(`__h.click('#students-add')`);
    await cdp.waitFor(`!!__h.q('dialog[open]')`);
    step('375px: dialog fits the viewport', await cdp.eval(`__h.q('dialog[open]').getBoundingClientRect().width <= 375 && __h.overflow() <= 0`));
    await cdp.screenshot(join(OUT, 'mobile-dialog.png'));
    await cdp.eval(`__h.q('dialog[open]').close(); true`);

    // 12. polished screenshots at 960x600 @2x (dark)
    await cdp.send('Emulation.setDeviceMetricsOverride', { width: 960, height: 600, deviceScaleFactor: 2, mobile: false });
    await cdp.eval(`__h.click('#banner-close'); location.hash = '#/dashboard'; true`);
    await sleep(300);
    await cdp.screenshot(join(OUT, 'shot-dashboard.png'));
    await cdp.eval(`location.hash = '#/students'; true`);
    await sleep(300);
    await cdp.eval(`__h.qa('#page table.data tbody tr')[2].click(); true`);
    await sleep(100);
    await cdp.screenshot(join(OUT, 'shot-students.png'));
    if (WRITE_DOCS) {
      await mkdir(join(WEB, 'docs'), { recursive: true });
      await writeFile(join(WEB, 'docs', 'screenshot.png'), await readFile(join(OUT, process.env.DOCS_SHOT || 'shot-dashboard.png')));
      console.log('  wrote docs/screenshot.png');
    }
    await cdp.eval(`localStorage.removeItem('sms-web-banner-dismissed'); true`);

    // console errors collected during the whole run
    step('zero console errors / exceptions / failed requests', consoleErrors.length === 0, consoleErrors.join(' || ').slice(0, 600));
  } finally {
    chrome.kill();
    server.close();
    await sleep(300);
    await rm(profile, { recursive: true, force: true }).catch(() => {});
  }

  const passed = results.filter((r) => r.ok).length;
  console.log(`\n${passed}/${results.length} browser checks passed`);
  if (problems.length) { console.log('failed: ' + problems.join('; ')); process.exit(1); }
}

main().catch((e) => { console.error(e); process.exit(1); });
