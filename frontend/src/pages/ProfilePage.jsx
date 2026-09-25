import { useState } from 'react';
import { useAuth } from '../context/AuthContext.jsx';
import * as addressService from '../services/addressService.js';
import useApi from '../hooks/useApi.js';
import AddressForm from '../components/AddressForm.jsx';
import ErrorMessage from '../components/ErrorMessage.jsx';
import { formatEnum } from '../utils/format.js';
import { getErrorMessage } from '../utils/errors.js';

export default function ProfilePage() {
  const { user, hasRole } = useAuth();

  return (
    <section className="page narrow">
      <h1>Profile</h1>
      <div className="card">
        <p>
          <strong>{user.name}</strong>
        </p>
        <p className="muted">{user.email}</p>
        {user.phone && <p className="muted">{user.phone}</p>}
        <p>
          <span className="badge">{formatEnum(user.role)}</span>
        </p>
      </div>
      {hasRole('CUSTOMER') && <Addresses />}
    </section>
  );
}

function Addresses() {
  const addresses = useApi(() => addressService.getAddresses(), []);
  const [adding, setAdding] = useState(false);
  const [error, setError] = useState(null);

  async function run(action) {
    setError(null);
    try {
      await action();
      addresses.reload();
    } catch (err) {
      setError(getErrorMessage(err));
    }
  }

  return (
    <>
      <div className="row-between">
        <h2>Saved addresses</h2>
        {!adding && (
          <button type="button" className="btn btn-sm btn-outline" onClick={() => setAdding(true)}>
            + Add address
          </button>
        )}
      </div>
      <ErrorMessage error={addresses.error} onRetry={addresses.reload} />
      {error && <div className="alert alert-error">{error}</div>}
      {adding && (
        <AddressForm
          onSaved={() => {
            setAdding(false);
            addresses.reload();
          }}
          onCancel={() => setAdding(false)}
        />
      )}
      {addresses.data?.length === 0 && !adding && <p className="muted">No saved addresses yet.</p>}
      <ul className="address-list">
        {addresses.data?.map((address) => (
          <li key={address.id} className="card row-between">
            <div>
              <strong>{address.label}</strong> {address.isDefault && <span className="badge">Default</span>}
              <p className="muted small">
                {address.line1}
                {address.line2 && `, ${address.line2}`}, {address.city}, {address.state} {address.postalCode}
              </p>
            </div>
            <div className="row gap">
              {!address.isDefault && (
                <button type="button" className="btn-link" onClick={() => run(() => addressService.makeDefault(address.id))}>
                  Make default
                </button>
              )}
              <button
                type="button"
                className="btn-link danger"
                onClick={() => window.confirm(`Delete "${address.label}"?`) && run(() => addressService.deleteAddress(address.id))}
              >
                Delete
              </button>
            </div>
          </li>
        ))}
      </ul>
    </>
  );
}
