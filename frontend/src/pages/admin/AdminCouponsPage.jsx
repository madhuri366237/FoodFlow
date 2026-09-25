import { useState } from 'react';
import * as adminService from '../../services/adminService.js';
import useApi from '../../hooks/useApi.js';
import { AdminNav } from './AdminDashboardPage.jsx';
import Spinner from '../../components/Spinner.jsx';
import ErrorMessage from '../../components/ErrorMessage.jsx';
import { formatDateTime, formatPrice } from '../../utils/format.js';
import { getErrorMessage, getFieldErrors } from '../../utils/errors.js';

const inDays = (days) => new Date(Date.now() + days * 86400000).toISOString().slice(0, 16); // for <input type="datetime-local">

const EMPTY = {
  code: '',
  description: '',
  discountType: 'PERCENTAGE',
  discountValue: '',
  minimumOrderAmount: '',
  maximumDiscount: '',
  usageLimit: '',
  expiresAt: inDays(30),
};

// Turns the form's strings into the API's types; empty optional fields become null.
function toRequest(form, active) {
  const numberOrNull = (value) => (value === '' || value === null ? null : Number(value));
  return {
    code: form.code,
    description: form.description || null,
    discountType: form.discountType,
    discountValue: numberOrNull(form.discountValue),
    minimumOrderAmount: numberOrNull(form.minimumOrderAmount),
    maximumDiscount: numberOrNull(form.maximumDiscount),
    usageLimit: numberOrNull(form.usageLimit),
    expiresAt: new Date(form.expiresAt).toISOString(),
    active,
  };
}

export default function AdminCouponsPage() {
  const coupons = useApi(() => adminService.getCoupons(), []);
  const [form, setForm] = useState(EMPTY);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState(null);
  const fieldErrors = getFieldErrors(error);
  const update = (field) => (event) => setForm({ ...form, [field]: event.target.value });

  async function create(event) {
    event.preventDefault();
    setSaving(true);
    setError(null);
    try {
      await adminService.createCoupon(toRequest(form, true));
      setForm(EMPTY);
      coupons.reload();
    } catch (err) {
      setError(err);
    } finally {
      setSaving(false);
    }
  }

  async function toggleActive(coupon) {
    setError(null);
    try {
      await adminService.updateCoupon(coupon.id, { ...coupon, active: !coupon.active });
      coupons.reload();
    } catch (err) {
      setError(err);
    }
  }

  const fieldError = (name) => fieldErrors[name] && <small className="field-error">{fieldErrors[name]}</small>;

  return (
    <section className="page">
      <h1>Coupons</h1>
      <AdminNav />

      <form className="form card" onSubmit={create}>
        <h2>New coupon</h2>
        <div className="grid-3">
          <label className="field">
            <span>Code</span>
            <input required value={form.code} onChange={(e) => setForm({ ...form, code: e.target.value.toUpperCase() })} />
            {fieldError('code')}
          </label>
          <label className="field">
            <span>Type</span>
            <select value={form.discountType} onChange={update('discountType')}>
              <option value="PERCENTAGE">Percentage (%)</option>
              <option value="FIXED_AMOUNT">Fixed amount (₹)</option>
            </select>
          </label>
          <label className="field">
            <span>Value</span>
            <input required type="number" min="0.01" step="0.01" value={form.discountValue} onChange={update('discountValue')} />
            {fieldError('discountValue')}
          </label>
          <label className="field">
            <span>Minimum order (₹)</span>
            <input type="number" min="0" step="0.01" value={form.minimumOrderAmount} onChange={update('minimumOrderAmount')} />
          </label>
          <label className="field">
            <span>Maximum discount (₹)</span>
            <input type="number" min="0.01" step="0.01" value={form.maximumDiscount} onChange={update('maximumDiscount')} />
          </label>
          <label className="field">
            <span>Usage limit</span>
            <input type="number" min="1" step="1" value={form.usageLimit} onChange={update('usageLimit')} />
          </label>
        </div>
        <label className="field">
          <span>Description</span>
          <input maxLength={255} value={form.description} onChange={update('description')} />
        </label>
        <label className="field">
          <span>Expires at</span>
          <input required type="datetime-local" value={form.expiresAt} onChange={update('expiresAt')} />
        </label>
        {error && !Object.keys(fieldErrors).length && <p className="field-error">{getErrorMessage(error)}</p>}
        <button type="submit" className="btn btn-primary" disabled={saving}>
          {saving ? 'Creating...' : 'Create coupon'}
        </button>
      </form>

      {coupons.loading && <Spinner />}
      <ErrorMessage error={coupons.error} onRetry={coupons.reload} />
      <div className="table-wrap">
        <table className="table card">
          <thead>
            <tr>
              <th>Code</th>
              <th>Discount</th>
              <th>Minimum</th>
              <th>Used</th>
              <th>Expires</th>
              <th>Active</th>
            </tr>
          </thead>
          <tbody>
            {coupons.data?.content.map((c) => (
              <tr key={c.id}>
                <td>
                  <strong>{c.code}</strong>
                  {c.description && <div className="muted small">{c.description}</div>}
                </td>
                <td>
                  {c.discountType === 'PERCENTAGE' ? `${Number(c.discountValue)}%` : formatPrice(c.discountValue)}
                  {c.maximumDiscount && <span className="muted small"> (max {formatPrice(c.maximumDiscount)})</span>}
                </td>
                <td>{formatPrice(c.minimumOrderAmount)}</td>
                <td>
                  {c.usedCount}
                  {c.usageLimit ? ` / ${c.usageLimit}` : ''}
                </td>
                <td>{formatDateTime(c.expiresAt)}</td>
                <td>
                  <button type="button" className={`btn btn-sm ${c.active ? 'btn-outline' : 'btn-primary'}`} onClick={() => toggleActive(c)}>
                    {c.active ? 'Deactivate' : 'Activate'}
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  );
}
