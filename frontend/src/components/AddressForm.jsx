import { useState } from 'react';
import * as addressService from '../services/addressService.js';
import { getErrorMessage, getFieldErrors } from '../utils/errors.js';

const EMPTY = { label: 'Home', line1: '', line2: '', city: '', state: '', postalCode: '' };

// Used on the checkout page and the profile page. Validation messages come from the backend
// (the single source of truth); the HTML attributes only give instant hints.
export default function AddressForm({ onSaved, onCancel }) {
  const [form, setForm] = useState(EMPTY);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState(null);
  const fieldErrors = getFieldErrors(error);

  const update = (field) => (event) => setForm({ ...form, [field]: event.target.value });

  async function submit(event) {
    event.preventDefault();
    setSaving(true);
    setError(null);
    try {
      const saved = await addressService.createAddress({ ...form, line2: form.line2 || null });
      setForm(EMPTY);
      onSaved(saved);
    } catch (err) {
      setError(err);
    } finally {
      setSaving(false);
    }
  }

  const field = (name, label, props = {}) => (
    <label className="field">
      <span>{label}</span>
      <input value={form[name]} onChange={update(name)} {...props} />
      {fieldErrors[name] && <small className="field-error">{fieldErrors[name]}</small>}
    </label>
  );

  return (
    <form className="form card" onSubmit={submit}>
      <div className="grid-2">
        {field('label', 'Label', { required: true, maxLength: 30, placeholder: 'Home, Work...' })}
        {field('postalCode', 'PIN code', { required: true, inputMode: 'numeric', pattern: '[1-9][0-9]{5}', maxLength: 6 })}
      </div>
      {field('line1', 'Address line 1', { required: true, maxLength: 255 })}
      {field('line2', 'Address line 2 (optional)', { maxLength: 255 })}
      <div className="grid-2">
        {field('city', 'City', { required: true, maxLength: 100 })}
        {field('state', 'State', { required: true, maxLength: 100 })}
      </div>
      {error && !Object.keys(fieldErrors).length && <p className="field-error">{getErrorMessage(error)}</p>}
      <div className="row gap">
        <button type="submit" className="btn btn-primary" disabled={saving}>
          {saving ? 'Saving...' : 'Save address'}
        </button>
        {onCancel && (
          <button type="button" className="btn btn-outline" onClick={onCancel}>
            Cancel
          </button>
        )}
      </div>
    </form>
  );
}
