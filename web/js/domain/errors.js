/** Per-field validation failure, like the Java ValidationException (errors keyed by field name). */
export class ValidationError extends Error {
  constructor(errors) {
    const messages = Object.values(errors);
    super(messages.join(' '));
    this.name = 'ValidationError';
    this.errors = errors;
  }

  has(field) {
    return field in this.errors;
  }
}

/** Collects field errors and throws once at the end, like ValidationException.Errors. */
export class Errors {
  constructor() {
    this.map = {};
  }

  add(field, message) {
    if (!(field in this.map)) this.map[field] = message;
  }

  has(field) {
    return field in this.map;
  }

  get any() {
    return Object.keys(this.map).length > 0;
  }

  throwIfAny() {
    if (this.any) throw new ValidationError(this.map);
  }
}

/** A business rule (not tied to one field) was violated, e.g. deleting a course with history. */
export class BusinessRuleError extends Error {
  constructor(message) {
    super(message);
    this.name = 'BusinessRuleError';
  }
}
