// Shared test fixture: a fresh in-memory SQLite database built from the real schema and seed scripts.
import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import initSqlJs from 'sql.js';
import { openDatabase } from '../js/db.js';
import { createServices } from '../js/domain/index.js';

const here = dirname(fileURLToPath(import.meta.url));
export const SCHEMA = readFileSync(resolve(here, '../sql/schema.sqlite.sql'), 'utf8');
export const SEED = readFileSync(resolve(here, '../sql/seed.sqlite.sql'), 'utf8');

let sqlPromise;
export function sqlJs() {
  sqlPromise ??= initSqlJs();
  return sqlPromise;
}

/** Fixed clock matching the README screenshots: 19 September 2026 (1st Sem 2026-2027). */
export const CLOCK = () => new Date(2026, 8, 19, 10, 0, 0);

/** A context like the Java TestDatabase: seeded, or schema only (with one program). */
export async function context(seeded = true, clock = CLOCK) {
  const SQL = await sqlJs();
  const db = openDatabase(SQL, { schema: SCHEMA, seed: seeded ? SEED : '' });
  if (!seeded) {
    db.run("INSERT INTO programs (code, name, department) VALUES ('BSCpE', 'BS Computer Engineering', 'College of Engineering')");
  }
  return createServices(db, clock);
}

export function student(overrides = {}) {
  return {
    studentNumber: '2024-00001',
    firstName: 'Ana',
    lastName: 'Reyes',
    email: 'ana.reyes@example.edu.ph',
    phone: null,
    birthDate: '2006-05-01',
    programId: 1,
    yearLevel: 3,
    status: 'ACTIVE',
    ...overrides,
  };
}
