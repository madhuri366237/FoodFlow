import { Link, NavLink, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext.jsx';
import { useCart } from '../context/CartContext.jsx';

// Links depend on the role: customers get cart/orders; owners and admins get their dashboards
// in Phase 11. Hiding a link is cosmetic; the backend still checks every request.
export default function Navbar() {
  const { user, isAuthenticated, hasRole, logout } = useAuth();
  const { itemCount } = useCart();
  const navigate = useNavigate();

  function handleLogout() {
    logout();
    navigate('/');
  }

  return (
    <header className="navbar">
      <div className="container navbar-inner">
        <Link to="/" className="brand">
          FoodFlow
        </Link>
        <nav className="nav-links">
          <NavLink to="/restaurants">Restaurants</NavLink>
          {hasRole('RESTAURANT_OWNER') && (
            <>
              <NavLink to="/owner" end>
                Dashboard
              </NavLink>
              <NavLink to="/owner/orders">Incoming orders</NavLink>
            </>
          )}
          {hasRole('ADMIN') && <NavLink to="/admin">Admin</NavLink>}
          {hasRole('CUSTOMER') && (
            <>
              <NavLink to="/dashboard">Dashboard</NavLink>
              <NavLink to="/orders">Orders</NavLink>
              <NavLink to="/cart" className="cart-link">
                Cart{itemCount > 0 && <span className="cart-count">{itemCount}</span>}
              </NavLink>
            </>
          )}
          {isAuthenticated ? (
            <>
              <NavLink to="/profile" title={user.email}>
                {user.name.split(' ')[0]}
              </NavLink>
              <button type="button" className="btn btn-sm btn-outline" onClick={handleLogout}>
                Logout
              </button>
            </>
          ) : (
            <>
              <NavLink to="/login">Login</NavLink>
              <Link to="/register" className="btn btn-sm btn-primary">
                Sign up
              </Link>
            </>
          )}
        </nav>
      </div>
    </header>
  );
}
