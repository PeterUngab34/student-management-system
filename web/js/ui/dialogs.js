// Modal dialogs built on <dialog>: form dialogs with a per-field error line (like FormDialog in Java),
// confirmations, and simple alerts. Escape closes every dialog; focus returns to the opener.
import { h, clear } from './dom.js';
import { ValidationError, BusinessRuleError } from '../domain/errors.js';
import { DataAccessError } from '../db.js';

const host = () => document.getElementById('dialogs');

function open(dialog) {
  const opener = document.activeElement;
  host().append(dialog);
  dialog.showModal();
  dialog.addEventListener('close', () => {
    dialog.remove();
    if (opener && typeof opener.focus === 'function' && document.contains(opener)) opener.focus();
  }, { once: true });
  return dialog;
}

let seq = 0;
const uid = (prefix) => prefix + '-' + (++seq);

/**
 * Field spec: { key, label, type: 'text'|'select'|'number'|'textarea'|'readonly', options: [{value,label}],
 *               value, placeholder, full, required, autofocus, inputmode, maxlength }
 * onSubmit(values) may throw ValidationError (mapped to fields) or BusinessRuleError (general line).
 * Resolves with the value returned by onSubmit, or null when cancelled.
 */
export function formDialog({ title, subtitle, fields, submitLabel = 'Save', width = 600, onSubmit, validate }) {
  return new Promise((resolve) => {
    const inputs = {};
    const errorEls = {};
    const generalError = h('div.form-error', { role: 'alert' });
    const headingId = uid('dlg-h');

    const fieldEls = fields.map((f) => {
      const id = uid('f');
      const errId = id + '-err';
      let input;
      if (f.type === 'select') {
        input = h('select.select', { id, name: f.key, 'aria-describedby': errId },
          f.options.map((o) => h('option', { value: o.value, selected: String(o.value) === String(f.value ?? '') }, o.label)));
      } else if (f.type === 'textarea') {
        input = h('textarea.textarea', { id, name: f.key, rows: 3, placeholder: f.placeholder, 'aria-describedby': errId, maxlength: f.maxlength }, f.value ?? '');
      } else if (f.type === 'readonly') {
        input = h('input.input', { id, name: f.key, value: f.value ?? '', readonly: true, tabindex: -1, 'aria-describedby': errId });
      } else {
        input = h('input.input', {
          id, name: f.key, type: f.type || 'text', value: f.value ?? '', placeholder: f.placeholder,
          inputmode: f.inputmode, min: f.min, max: f.max, step: f.step, maxlength: f.maxlength, autocomplete: 'off',
          'aria-describedby': errId, 'aria-required': f.required ? 'true' : null,
        });
      }
      if (f.autofocus) input.setAttribute('autofocus', '');
      inputs[f.key] = input;
      errorEls[f.key] = h('div.field-error', { id: errId, 'aria-live': 'polite' });
      return h('div.field', { class: f.full ? 'full' : '' },
        h('label', { for: id }, f.label + (f.required ? ' *' : '')),
        input,
        errorEls[f.key]);
    });

    const form = h('form', { method: 'dialog', novalidate: true },
      h('div.form-grid', fieldEls),
      generalError,
      h('div.dlg-actions',
        h('button.btn', { type: 'button', onClick: () => dialog.close() }, 'Cancel'),
        h('button.btn.btn-primary', { type: 'submit' }, submitLabel)));

    const dialog = h('dialog.dlg', { 'aria-labelledby': headingId, style: { '--dlg-w': width + 'px' } },
      h('div.dlg-body',
        h('h2.dlg-heading', { id: headingId }, title),
        subtitle ? h('p.dlg-sub', subtitle) : null,
        form));

    let result = null;
    form.addEventListener('submit', (ev) => {
      ev.preventDefault();
      for (const k of Object.keys(errorEls)) { errorEls[k].textContent = ''; inputs[k].removeAttribute('aria-invalid'); }
      generalError.textContent = '';
      const values = {};
      for (const [k, el] of Object.entries(inputs)) values[k] = el.value;
      const uiErrors = validate ? validate(values) || {} : {};
      if (Object.keys(uiErrors).length) { showErrors(uiErrors); return; }
      try {
        result = onSubmit(values);
        dialog.close();
      } catch (e) {
        if (e instanceof ValidationError) showErrors(e.errors);
        else if (e instanceof BusinessRuleError) generalError.textContent = e.message;
        else if (e instanceof DataAccessError) generalError.textContent = 'Database error: ' + e.message;
        else throw e;
      }
    });

    function showErrors(errors) {
      let first = null;
      for (const [field, message] of Object.entries(errors)) {
        if (errorEls[field]) {
          errorEls[field].textContent = message;
          inputs[field].setAttribute('aria-invalid', 'true');
          first ??= inputs[field];
        } else {
          generalError.textContent += (generalError.textContent ? ' ' : '') + message;
        }
      }
      if (first) first.focus();
    }

    dialog.addEventListener('close', () => resolve(result), { once: true });
    open(dialog);
    const focusTarget = fields.find((f) => f.autofocus);
    (focusTarget ? inputs[focusTarget.key] : form.querySelector('input, select, textarea'))?.focus();
  });
}

/** Confirmation with a danger/primary action. `message` may contain markup (already escaped by the caller). */
export function confirmDialog({ title, html, confirmLabel = 'OK', danger = false }) {
  return new Promise((resolve) => {
    let ok = false;
    const headingId = uid('dlg-h');
    const dialog = h('dialog.dlg', { 'aria-labelledby': headingId, style: { '--dlg-w': '460px' } },
      h('div.dlg-body',
        h('h2.dlg-heading', { id: headingId }, title),
        h('div.confirm-text', { html, style: { marginTop: '12px' } }),
        h('div.dlg-actions',
          h('button.btn', { type: 'button', onClick: () => dialog.close() }, 'Cancel'),
          h('button.btn', { class: danger ? 'btn-danger' : 'btn-primary', type: 'button', onClick: () => { ok = true; dialog.close(); } }, confirmLabel))));
    dialog.addEventListener('close', () => resolve(ok), { once: true });
    open(dialog);
    dialog.querySelector('.btn-danger, .btn-primary').focus();
  });
}

export function alertDialog({ title, html, label = 'OK' }) {
  return new Promise((resolve) => {
    const headingId = uid('dlg-h');
    const dialog = h('dialog.dlg', { 'aria-labelledby': headingId, role: 'alertdialog', style: { '--dlg-w': '460px' } },
      h('div.dlg-body',
        h('h2.dlg-heading', { id: headingId }, title),
        h('div.confirm-text', { html, style: { marginTop: '12px' } }),
        h('div.dlg-actions', h('button.btn.btn-primary', { type: 'button', onClick: () => dialog.close() }, label))));
    dialog.addEventListener('close', () => resolve(), { once: true });
    open(dialog);
    dialog.querySelector('button').focus();
  });
}

/** A custom-content dialog; returns { dialog, body, close }. */
export function contentDialog({ title, width = 720, className = '' }) {
  const headingId = uid('dlg-h');
  const body = h('div');
  const dialog = h('dialog.dlg', { class: className, 'aria-labelledby': headingId, style: { '--dlg-w': width + 'px' } },
    h('div.dlg-body', h('h2.dlg-heading.sr-only', { id: headingId }, title), body));
  open(dialog);
  return { dialog, body, close: () => dialog.close(), setBody: (...c) => { clear(body); body.append(...c); } };
}
