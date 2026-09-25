import { useState } from 'react';
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext.jsx';
import { homeFor } from '../utils/roles.js';
import { getErrorMessage, getFieldErrors } from '../utils/errors.js';

const INITIAL = { name: '', email: '', phone: '', password: '', accountType: 'CUSTOMER' };

export default function RegisterPage() {
  const { register, isAuthenticated, user } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const from = location.state?.from?.pathname;

  const [form, setForm] = useState(INITIAL);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState(null);
  const fieldErrors = getFieldErrors(error);

  if (isAuthenticated) {
    return <Navigate to={from ?? homeFor(user)} replace />;
  }

  const update = (field) => (event) => setForm({ ...form, [field]: event.target.value });

  async function submit(event) {
    event.preventDefault();
    setSubmitting(true);
    setError(null);
    try {
      const created = await register(form); // the backend returns a token: logged in right away
      navigate(from ?? homeFor(created), { replace: true });
    } catch (err) {
      setError(err);
    } finally {
      setSubmitting(false);
    }
  }

  const fieldError = (name) => fieldErrors[name] && <small className="field-error">{fieldErrors[name]}</small>;

  return (
    <section className="page narrow">
      <h1>Create your account</h1>
      <form className="form card" onSubmit={submit} noValidate>
        {error && !Object.keys(fieldErrors).length && <div className="alert alert-error">{getErrorMessage(error)}</div>}

        <label className="field">
          <span>Name</span>
          <input autoComplete="name" value={form.name} onChange={update('name')} />
          {fieldError('name')}
        </label>
        <label className="field">
          <span>Email</span>
          <input type="email" autoComplete="email" value={form.email} onChange={update('email')} />
          {fieldError('email')}
        </label>
        <label className="field">
          <span>Phone (optional)</span>
          <input type="tel" autoComplete="tel" placeholder="9876543210" value={form.phone} onChange={update('phone')} />
          {fieldError('phone')}
        </label>
        <label className="field">
          <span>Password</span>
          <input type="password" autoComplete="new-password" value={form.password} onChange={update('password')} />
          <small className="muted">8-72 characters, with at least one letter and one digit.</small>
          {fieldError('password')}
        </label>

        {/* Only the two self-service account types exist; ADMIN is not an option the API accepts. */}
        <fieldset className="field">
          <legend>I want to</legend>
          <label className="radio">
            <input type="radio" name="accountType" value="CUSTOMER" checked={form.accountType === 'CUSTOMER'} onChange={update('accountType')} />
            Order food
          </label>
          <label className="radio">
            <input
              type="radio"
              name="accountType"
              value="RESTAURANT_OWNER"
              checked={form.accountType === 'RESTAURANT_OWNER'}
              onChange={update('accountType')}
            />
            List my restaurant
          </label>
        </fieldset>

        <button type="submit" className="btn btn-primary btn-block" disabled={submitting}>
          {submitting ? 'Creating account...' : 'Sign up'}
        </button>
        <p className="muted center">
          Already have an account? <Link to="/login" state={location.state}>Log in</Link>
        </p>
      </form>
    </section>
  );
}
