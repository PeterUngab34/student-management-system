import { test } from 'node:test';
import assert from 'node:assert/strict';
import { escapeCsv, toCsv, BOM } from '../js/domain/csv.js';

test('CSV escaping follows RFC 4180 like the Java CsvWriter', () => {
  assert.equal(escapeCsv('plain'), 'plain');
  assert.equal(escapeCsv(null), '');
  assert.equal(escapeCsv('Dela Cruz, Juan'), '"Dela Cruz, Juan"');
  assert.equal(escapeCsv('say "hi"'), '"say ""hi"""');
  assert.equal(escapeCsv('a\nb'), '"a\nb"');
});

test('CSV starts with a UTF-8 BOM and uses CRLF line endings', () => {
  const csv = toCsv(['Student No.', 'Name'], [['2022-00094', 'Ibañez, Gabriel'], ['2026-00421', 'Dela Cruz, Vincent']]);
  assert.ok(csv.startsWith(BOM));
  assert.equal(csv, BOM + 'Student No.,Name\r\n2022-00094,"Ibañez, Gabriel"\r\n2026-00421,"Dela Cruz, Vincent"\r\n');
});
