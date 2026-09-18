// Thin wrapper over a sql.js Database, mirroring the JdbcTemplate helper of the desktop app:
// every statement runs with bound parameters, results come back as plain objects.
// Works in the browser (sql-wasm.js) and in Node (the sql.js npm package) alike.

export class DataAccessError extends Error {
  constructor(message, cause) {
    super(message);
    this.name = 'DataAccessError';
    this.cause = cause;
  }
}

export class Db {
  /** @param {import('sql.js').Database} raw */
  constructor(raw) {
    this.raw = raw;
    this.listeners = new Set();
    this.raw.run('PRAGMA foreign_keys = ON');
  }

  /** Rows as objects. */
  query(sql, params = []) {
    let stmt;
    try {
      stmt = this.raw.prepare(sql);
      stmt.bind(params);
      const rows = [];
      while (stmt.step()) rows.push(stmt.getAsObject());
      return rows;
    } catch (e) {
      throw new DataAccessError('Query failed: ' + e.message, e);
    } finally {
      if (stmt) stmt.free();
    }
  }

  queryOne(sql, params = []) {
    const rows = this.query(sql, params);
    return rows.length ? rows[0] : null;
  }

  queryValue(sql, params = []) {
    const row = this.queryOne(sql, params);
    if (!row) return null;
    const keys = Object.keys(row);
    return keys.length ? row[keys[0]] : null;
  }

  queryInt(sql, params = []) {
    const v = this.queryValue(sql, params);
    return v == null ? 0 : Number(v);
  }

  /** INSERT / UPDATE / DELETE; returns the affected row count. */
  run(sql, params = []) {
    try {
      this.raw.run(sql, params);
      const changes = this.raw.getRowsModified();
      this.changed();
      return changes;
    } catch (e) {
      throw new DataAccessError('Update failed: ' + e.message, e);
    }
  }

  /** INSERT; returns the generated key. */
  insert(sql, params = []) {
    try {
      this.raw.run(sql, params);
      const id = Number(this.raw.exec('SELECT last_insert_rowid()')[0].values[0][0]);
      this.changed();
      return id;
    } catch (e) {
      throw new DataAccessError('Insert failed: ' + e.message, e);
    }
  }

  /** Runs a multi-statement script (schema / seed). */
  exec(script) {
    this.raw.exec(script);
    this.changed();
  }

  /** Columns + rows of a raw statement, for the SQL console. Read-only enforced via query_only. */
  select(sql, { maxRows = 500 } = {}) {
    let stmt;
    this.raw.run('PRAGMA query_only = ON');
    try {
      stmt = this.raw.prepare(sql);
      const columns = stmt.getColumnNames();
      const rows = [];
      let truncated = false;
      while (stmt.step()) {
        if (rows.length >= maxRows) { truncated = true; break; }
        rows.push(stmt.get());
      }
      return { columns, rows, truncated };
    } finally {
      if (stmt) stmt.free();
      this.raw.run('PRAGMA query_only = OFF');
    }
  }

  export() {
    return this.raw.export();
  }

  onChange(fn) {
    this.listeners.add(fn);
    return () => this.listeners.delete(fn);
  }

  changed() {
    for (const fn of this.listeners) fn();
  }

  close() {
    this.raw.close();
  }
}

/** Creates a database from saved bytes, or from the schema + seed scripts. */
export function openDatabase(SQL, { bytes = null, schema, seed } = {}) {
  if (bytes) {
    return new Db(new SQL.Database(bytes));
  }
  const db = new Db(new SQL.Database());
  db.raw.exec(schema);
  db.raw.exec(seed);
  return db;
}
