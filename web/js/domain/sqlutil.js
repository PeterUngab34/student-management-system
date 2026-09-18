/** Escapes LIKE wildcards so user input is matched literally (used with ESCAPE '!'). */
export function likePattern(keyword) {
  const escaped = keyword.trim().toLowerCase().replace(/!/g, '!!').replace(/%/g, '!%').replace(/_/g, '!_');
  return '%' + escaped + '%';
}

export function hasText(s) {
  return s != null && String(s).trim() !== '';
}

/** Today's date in the browser's local time zone as YYYY-MM-DD parts. */
export function today(clock = () => new Date()) {
  const d = clock();
  return { year: d.getFullYear(), month: d.getMonth() + 1, day: d.getDate(), iso: isoDate(d) };
}

export function isoDate(d) {
  return d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0') + '-' + String(d.getDate()).padStart(2, '0');
}
