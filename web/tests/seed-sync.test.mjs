import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { isInSync, toSqlite, SOURCE } from '../scripts/sync-seed.mjs';

test('web/sql/seed.sqlite.sql is generated from sql/seed.sql (run: node web/scripts/sync-seed.mjs)', () => {
  assert.ok(isInSync(), 'web/sql/seed.sqlite.sql is out of sync with sql/seed.sql');
});

test('the root seed is the single source of truth and contains the expected row counts', () => {
  const seed = readFileSync(SOURCE, 'utf8');
  const count = (re) => (seed.match(re) || []).length;
  assert.equal(count(/^\s*\('[A-Za-z]{2,5}', 'BS /gm), 4);        // programs
  assert.equal(count(/^\s*\('(19|20)\d{2}-\d{5}', '/gm), 25);      // students
  assert.equal(count(/^\s*\('[A-Z]{2,5} \d{3}', '/gm), 10);        // courses
  assert.equal(count(/^\s*\(\(SELECT student_id FROM students/gm), 153); // enrollments
});

test('the MySQL -> SQLite transform strips backticks and MySQL-only statements', () => {
  const out = toSqlite("USE x;\nSET NAMES utf8mb4;\nINSERT IGNORE INTO `t` (`a`) VALUES (TRUE);\n");
  assert.equal(out, 'INSERT OR IGNORE INTO t (a) VALUES (1);\n');
});
