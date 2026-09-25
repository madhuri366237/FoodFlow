import { useState } from 'react';
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext.jsx';
import { homeFor } from '../utils/roles.js';
import ErrorMessage from '../components/ErrorMessage.jsx';

export default function LoginPage() {
  const { login, isAuthenticated, user } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  // ProtectedRoute stored the page the user was trying to open.
  const from = location.state?.from?.pathname;

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState(null);

  if (isAuthenticated) {
    return <Navigate to={from ?? homeFor(user)} replace />;
  }

  async function submit(event) {
    event.preventDefault();
    setSubmitting(true);
    setError(null);
    try {
      const loggedIn = await login(email, password);
      navigate(from ?? homeFor(loggedIn), { replace: true });
    } catch (err) {
      // 401 INVALID_CREDENTIALS: the backend deliberately doesn't say whether
      // the email or the password was wrong, and neither do we.
      setError(err);
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <section className="page narrow">
      <h1>Log in</h1>
      <form className="form card" onSubmit={submit}>
        <ErrorMessage error={error} />
        <label className="field">
          <span>Email</span>
          <input type="email" autoComplete="email" required value={email} onChange={(e) => setEmail(e.target.value)} />
        </label>
        <label className="field">
          <span>Password</span>
          <input
            type="password"
            autoComplete="current-password"
            required
            value={password}
            onChange={(e) => setPassword(e.target.value)}
          />
        </label>
        <button type="submit" className="btn btn-primary btn-block" disabled={submitting}>
          {submitting ? 'Logging in...' : 'Log in'}
        </button>
        <p className="muted center">
          New here? <Link to="/register" state={location.state}>Create an account</Link>
        </p>
      </form>
    </section>
  );
}
