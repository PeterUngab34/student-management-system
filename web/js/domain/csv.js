// RFC 4180 CSV with a UTF-8 BOM so Excel shows names like "Ibañez" correctly (CsvWriter in Java).

export const BOM = '﻿';

/** Quotes a value when it contains a comma, quote or line break; doubles embedded quotes. */
export function escapeCsv(value) {
  if (value == null) return '';
  const s = String(value);
  const needsQuotes = s.includes(',') || s.includes('"') || s.includes('\n') || s.includes('\r');
  return needsQuotes ? '"' + s.replace(/"/g, '""') + '"' : s;
}

export function toCsv(header, rows) {
  const lines = [header, ...rows].map((row) => row.map(escapeCsv).join(','));
  return BOM + lines.map((l) => l + '\r\n').join('');
}
