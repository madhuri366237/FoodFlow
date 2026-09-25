import { Link } from 'react-router-dom';
import * as dashboardService from '../services/dashboardService.js';
import useApi from '../hooks/useApi.js';
import { useAuth } from '../context/AuthContext.jsx';
import StatTile from '../components/StatTile.jsx';
import StatusBadge from '../components/StatusBadge.jsx';
import Spinner from '../components/Spinner.jsx';
import ErrorMessage from '../components/ErrorMessage.jsx';
import { formatDateTime, formatPrice } from '../utils/format.js';

export default function CustomerDashboardPage() {
  const { user } = useAuth();
  const dashboard = useApi(() => dashboardService.getCustomerDashboard(), []);

  if (dashboard.loading) return <Spinner />;
  if (dashboard.error) return <ErrorMessage error={dashboard.error} onRetry={dashboard.reload} />;
  const d = dashboard.data;

  return (
    <section className="page">
      <h1>Hi, {user.name.split(' ')[0]}</h1>
      <div className="stat-grid">
        <StatTile label="Orders" value={d.totalOrders} hint={`${d.deliveredOrders} delivered`} />
        <StatTile label="In progress" value={d.activeOrders} />
        <StatTile label="Total spent" value={formatPrice(d.totalSpent)} />
        <StatTile label="Saved with coupons" value={formatPrice(d.totalSaved)} />
      </div>

      {d.favoriteRestaurant && (
        <div className="card section">
          <span className="muted small">Your favourite</span>
          <h2>
            <Link to={`/restaurants/${d.favoriteRestaurant.id}`}>{d.favoriteRestaurant.name}</Link>
          </h2>
          <p className="muted">{d.favoriteRestaurant.orders} orders so far</p>
        </div>
      )}

      <div className="row-between section">
        <h2>Recent orders</h2>
        <Link to="/orders">All orders</Link>
      </div>
      {d.recentOrders.length === 0 && (
        <p className="muted">
          No orders yet. <Link to="/restaurants">Find something to eat</Link>.
        </p>
      )}
      <ul className="order-list">
        {d.recentOrders.map((order) => (
          <li key={order.id}>
            <Link to={`/orders/${order.id}`} className="card order-row">
              <div>
                <strong>{order.restaurantName}</strong>
                <p className="muted small">
                  #{order.id} · {formatDateTime(order.createdAt)}
                </p>
              </div>
              <div className="order-row-end">
                <StatusBadge status={order.status} />
                <span className="price">{formatPrice(order.totalAmount)}</span>
              </div>
            </Link>
          </li>
        ))}
      </ul>
    </section>
  );
}
