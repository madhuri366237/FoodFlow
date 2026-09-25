import { useState } from 'react';
import * as ownerService from '../../services/ownerService.js';
import { getErrorMessage, getFieldErrors } from '../../utils/errors.js';

const EMPTY = { name: '', description: '', address: '', phone: '', imageUrl: '' };

// Create (no `restaurant` prop) or edit an existing restaurant.
export default function RestaurantForm({ restaurant, onSaved, onCancel }) {
  const [form, setForm] = useState(() =>
    restaurant
      ? {
          name: restaurant.name,
          description: restaurant.description ?? '',
          address: restaurant.address,
          phone: restaurant.phone,
          imageUrl: restaurant.imageUrl ?? '',
        }
      : EMPTY,
  );
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState(null);
  const fieldErrors = getFieldErrors(error);

  const update = (field) => (event) => setForm({ ...form, [field]: event.target.value });

  async function submit(event) {
    event.preventDefault();
    setSaving(true);
    setError(null);
    const body = { ...form, description: form.description || null, imageUrl: form.imageUrl || null };
    try {
      const saved = restaurant
        ? await ownerService.updateRestaurant(restaurant.id, body)
        : await ownerService.createRestaurant(body);
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
      <h2>{restaurant ? 'Edit restaurant' : 'New restaurant'}</h2>
      {field('name', 'Name', { required: true, maxLength: 120 })}
      <label className="field">
        <span>Description</span>
        <textarea rows={2} maxLength={1000} value={form.description} onChange={update('description')} />
      </label>
      {field('address', 'Address', { required: true, maxLength: 255 })}
      <div className="grid-2">
        {field('phone', 'Phone', { required: true, inputMode: 'tel' })}
        {field('imageUrl', 'Image URL (optional)', { type: 'url', placeholder: 'https://...' })}
      </div>
      {error && !Object.keys(fieldErrors).length && <p className="field-error">{getErrorMessage(error)}</p>}
      <div className="row gap">
        <button type="submit" className="btn btn-primary" disabled={saving}>
          {saving ? 'Saving...' : 'Save'}
        </button>
        <button type="button" className="btn btn-outline" onClick={onCancel}>
          Cancel
        </button>
      </div>
    </form>
  );
}
