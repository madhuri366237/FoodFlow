import { Link } from 'react-router-dom';
import * as dashboardService from '../../services/dashboardService.js';
import useApi from '../../hooks/useApi.js';
import StatTile from '../../components/StatTile.jsx';
import DailyRevenueChart from '../../components/DailyRevenueChart.jsx';
import StatusBreakdown from '../../components/StatusBreakdown.jsx';
import Spinner from '../../components/Spinner.jsx';
import ErrorMessage from '../../components/ErrorMessage.jsx';
import { formatPrice } from '../../utils/format.js';

export const ADMIN_LINKS = [
  { to: '/admin', label: 'Dashboard' },
  { to: '/admin/users', label: 'Users' },
  { to: '/admin/restaurants', label: 'Restaurants' },
  { to: '/admin/orders', label: 'Orders' },
  { to: '/admin/coupons', label: 'Coupons' },
];

export function AdminNav() {
  return (
    <nav className="chips admin-nav">
      {ADMIN_LINKS.map((link) => (
        <Link key={link.to} to={link.to} className="chip">
          {link.label}
        </Link>
      ))}
    </nav>
  );
}

export default function AdminDashboardPage() {
  const analytics = useApi(() => dashboardService.getAdminAnalytics(), []);

  return (
    <section className="page">
      <div className="row-between">
        <h1>Admin dashboard</h1>
        <button type="button" className="btn btn-outline btn-sm" onClick={analytics.reload}>
          Refresh
        </button>
      </div>
      <AdminNav />
      {analytics.loading && !analytics.data && <Spinner />}
      <ErrorMessage error={analytics.error} onRetry={analytics.reload} />
      {analytics.data && <Figures a={analytics.data} />}
    </section>
  );
}

function Figures({ a }) {
  const o = a.orders;
  return (
    <>
      <div className="stat-grid">
        <StatTile label="Total users" value={a.users.total} hint={`${a.users.customers} customers · ${a.users.owners} owners`} />
        <StatTile label="Restaurants" value={a.restaurants.total} hint={`${a.restaurants.active} active · ${a.restaurants.openNow} open now`} />
        <StatTile label="Total orders" value={o.totalOrders} hint={`${o.cancelledOrders} cancelled`} />
        <StatTile label="Total revenue" value={formatPrice(o.revenue)} hint={`avg ${formatPrice(o.averageOrderValue)}`} />
        <StatTile label="Today's orders" value={o.todayOrders} hint={`${formatPrice(o.todayRevenue)} today`} />
        <StatTile label="Pending orders" value={o.activeOrders} hint="placed but not delivered" />
      </div>
      <div className="dashboard-grid">
        <DailyRevenueChart points={a.last7Days} title="Platform revenue, last 7 days" />
        <StatusBreakdown counts={a.ordersByStatus} />
      </div>
      <div className="card">
        <strong>Top restaurants by revenue</strong>
        {a.topRestaurants.length === 0 ? (
          <p className="muted small">No orders yet.</p>
        ) : (
          <table className="table">
            <thead>
              <tr>
                <th>Restaurant</th>
                <th>Orders</th>
                <th>Revenue</th>
              </tr>
            </thead>
            <tbody>
              {a.topRestaurants.map((r) => (
                <tr key={r.id}>
                  <td>{r.name}</td>
                  <td>{r.orders}</td>
                  <td>{formatPrice(r.revenue)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
      {a.users.disabled > 0 && <p className="muted small">{a.users.disabled} disabled account(s).</p>}
    </>
  );
}
