import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { useAuth } from '../context/AuthContext.jsx';
import Spinner from '../components/Spinner.jsx';

/**
 * Guards a group of routes:
 *   - still checking the saved token -> spinner (don't flash the login page on reload)
 *   - not logged in                  -> /login, remembering where the user wanted to go
 *   - logged in with the wrong role  -> "no access" message
 *
 * This is USER EXPERIENCE, not security. Anyone can edit JavaScript in their own browser.
 * The real enforcement is the backend: every API call is checked again (JWT filter,
 * @PreAuthorize, ownership checks). A bypassed guard only shows an empty page full of 403s.
 */
export default function ProtectedRoute({ roles }) {
  const { status, user } = useAuth();
  const location = useLocation();

  if (status === 'loading') {
    return <Spinner label="Checking your session..." />;
  }
  if (status !== 'authenticated') {
    return <Navigate to="/login" replace state={{ from: location }} />;
  }
  if (roles && !roles.includes(user.role)) {
    return (
      <section className="page narrow">
        <h1>No access</h1>
        <p className="muted">This page is not available for your account type.</p>
      </section>
    );
  }
  return <Outlet />;
}
