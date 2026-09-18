#!/usr/bin/env node
// Generates web/sql/seed.sqlite.sql from the project's single source of truth,
// sql/seed.sql, applying a small MySQL -> SQLite compatibility transform.
//
//   node web/scripts/sync-seed.mjs          # (re)write web/sql/seed.sqlite.sql
//   node web/scripts/sync-seed.mjs --check  # exit 1 if the generated file is stale
//
// The test suite (web/tests/seed-sync.test.mjs) runs the --check logic too, so a
// change to sql/seed.sql that is not synced fails CI.

import { readFileSync, writeFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
export const SOURCE = resolve(here, '../../sql/seed.sql');
export const TARGET = resolve(here, '../sql/seed.sqlite.sql');

/** MySQL -> SQLite transform. The seed avoids MySQL-only syntax on purpose, so this is mostly defensive. */
export function toSqlite(mysql) {
  let sql = mysql.replace(/\r\n/g, '\n');
  sql = sql.replace(/`/g, '');                                        // identifier quotes
  sql = sql.replace(/^\s*(USE|SET|START TRANSACTION|COMMIT|LOCK TABLES|UNLOCK TABLES)\b[^\n]*\n/gim, '');
  sql = sql.replace(/\bINSERT\s+IGNORE\s+INTO\b/gi, 'INSERT OR IGNORE INTO');
  sql = sql.replace(/\)\s*ENGINE\s*=\s*\w+[^;]*;/gi, ');');
  sql = sql.replace(/\bTRUE\b/g, '1').replace(/\bFALSE\b/g, '0');
  return sql.trimEnd() + '\n';
}

export function generate() {
  const source = readFileSync(SOURCE, 'utf8');
  const header = [
    '-- =============================================================================',
    '--  GENERATED FILE - do not edit. Source of truth: sql/seed.sql (repository root).',
    '--  Regenerate with:  node web/scripts/sync-seed.mjs',
    '--  The web edition runs this on first load, after sql/schema.sqlite.sql.',
    '-- =============================================================================',
    '',
  ].join('\n');
  return header + toSqlite(source);
}

export function isInSync() {
  try {
    return readFileSync(TARGET, 'utf8').replace(/\r\n/g, '\n') === generate();
  } catch {
    return false;
  }
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  if (process.argv.includes('--check')) {
    if (isInSync()) {
      console.log('web/sql/seed.sqlite.sql is in sync with sql/seed.sql');
    } else {
      console.error('web/sql/seed.sqlite.sql is OUT OF SYNC with sql/seed.sql - run: node web/scripts/sync-seed.mjs');
      process.exit(1);
    }
  } else {
    writeFileSync(TARGET, generate(), 'utf8');
    console.log('wrote', TARGET);
  }
}
