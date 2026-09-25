import { useState } from 'react';
import { Link } from 'react-router-dom';
import * as dashboardService from '../../services/dashboardService.js';
import * as ownerService from '../../services/ownerService.js';
import useApi from '../../hooks/useApi.js';
import StatTile from '../../components/StatTile.jsx';
import DailyRevenueChart from '../../components/DailyRevenueChart.jsx';
import StatusBreakdown from '../../components/StatusBreakdown.jsx';
import Spinner from '../../components/Spinner.jsx';
import ErrorMessage from '../../components/ErrorMessage.jsx';
import RestaurantForm from './RestaurantForm.jsx';
import { formatPrice } from '../../utils/format.js';

export default function OwnerDashboardPage() {
  const [restaurantId, setRestaurantId] = useState('');
  const [creating, setCreating] = useState(false);
  const restaurants = useApi(() => ownerService.getMyRestaurants(), []);
  const dashboard = useApi(() => dashboardService.getOwnerDashboard(restaurantId || null), [restaurantId]);

  return (
    <section className="page">
      <div className="row-between">
        <h1>Owner dashboard</h1>
        <div className="row gap">
          <Link to="/owner/orders" className="btn btn-outline btn-sm">
            Incoming orders
          </Link>
          <button type="button" className="btn btn-primary btn-sm" onClick={() => setCreating(true)}>
            + New restaurant
          </button>
        </div>
      </div>

      {creating && (
        <RestaurantForm
          onSaved={() => {
            setCreating(false);
            restaurants.reload();
            dashboard.reload();
          }}
          onCancel={() => setCreating(false)}
        />
      )}

      {restaurants.data?.content.length > 1 && (
        <label className="field inline-field">
          <span>Show figures for</span>
          <select value={restaurantId} onChange={(e) => setRestaurantId(e.target.value)}>
            <option value="">All my restaurants</option>
            {restaurants.data.content.map((r) => (
              <option key={r.id} value={r.id}>
                {r.name}
              </option>
            ))}
          </select>
        </label>
      )}

      {dashboard.loading && !dashboard.data && <Spinner />}
      <ErrorMessage error={dashboard.error} onRetry={dashboard.reload} />
      {dashboard.data && <Figures d={dashboard.data} />}

      <h2>My restaurants</h2>
      <ErrorMessage error={restaurants.error} onRetry={restaurants.reload} />
      {restaurants.data?.content.length === 0 && !creating && (
        <p className="muted">You haven&apos;t added a restaurant yet. Click “New restaurant” to start.</p>
      )}
      <ul className="order-list">
        {restaurants.data?.content.map((r) => (
          <li key={r.id}>
            <Link to={`/owner/restaurants/${r.id}`} className="card order-row">
              <div>
                <strong>{r.name}</strong>
                <p className="muted small">{r.address}</p>
              </div>
              <div className="order-row-end">
                {!r.active && <span className="badge badge-cancelled">Deactivated by admin</span>}
                <span className={`badge ${r.open ? 'badge-delivered' : 'badge-pending'}`}>{r.open ? 'Open' : 'Closed'}</span>
                <span className="rating">★ {r.ratingCount ? Number(r.rating).toFixed(1) : 'New'}</span>
              </div>
            </Link>
          </li>
        ))}
      </ul>
    </section>
  );
}

function Figures({ d }) {
  const o = d.orders;
  return (
    <>
      <div className="stat-grid">
        <StatTile label="Today's orders" value={o.todayOrders} hint={`${formatPrice(o.todayRevenue)} revenue today`} />
        <StatTile label="Pending" value={o.activeOrders} hint="not yet delivered" />
        <StatTile label="Completed" value={o.deliveredOrders} hint={`${o.cancelledOrders} cancelled`} />
        <StatTile label="Revenue" value={formatPrice(o.revenue)} hint={`avg ${formatPrice(o.averageOrderValue)} / order`} />
      </div>
      <div className="dashboard-grid">
        <DailyRevenueChart points={d.last7Days} />
        <StatusBreakdown counts={d.ordersByStatus} />
      </div>
      <div className="card">
        <strong>Best sellers</strong>
        {d.topDishes.length === 0 ? (
          <p className="muted small">No paid orders yet.</p>
        ) : (
          <table className="table">
            <thead>
              <tr>
                <th>Dish</th>
                <th>Sold</th>
                <th>Revenue</th>
              </tr>
            </thead>
            <tbody>
              {d.topDishes.map((dish) => (
                <tr key={dish.name}>
                  <td>{dish.name}</td>
                  <td>{dish.quantity}</td>
                  <td>{formatPrice(dish.revenue)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </>
  );
}
