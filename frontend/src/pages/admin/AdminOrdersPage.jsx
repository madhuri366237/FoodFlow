import { useSearchParams } from 'react-router-dom';
import * as adminService from '../../services/adminService.js';
import useApi from '../../hooks/useApi.js';
import { AdminNav } from './AdminDashboardPage.jsx';
import StatusBadge from '../../components/StatusBadge.jsx';
import Pagination from '../../components/Pagination.jsx';
import Spinner from '../../components/Spinner.jsx';
import ErrorMessage from '../../components/ErrorMessage.jsx';
import { formatDateTime, formatEnum, formatPrice } from '../../utils/format.js';

const STATUSES = ['PLACED', 'CONFIRMED', 'PREPARING', 'READY_FOR_PICKUP', 'OUT_FOR_DELIVERY', 'DELIVERED', 'CANCELLED'];

export default function AdminOrdersPage() {
  const [params, setParams] = useSearchParams();
  const status = params.get('status') ?? '';
  const page = Number(params.get('page') ?? 0);
  const orders = useApi(() => adminService.getOrders({ status, page }), [status, page]);

  return (
    <section className="page">
      <h1>All orders</h1>
      <AdminNav />
      <select className="filter-select" value={status} onChange={(e) => setParams(e.target.value ? { status: e.target.value } : {})} aria-label="Status">
        <option value="">All statuses</option>
        {STATUSES.map((s) => (
          <option key={s} value={s}>
            {formatEnum(s)}
          </option>
        ))}
      </select>
      {orders.loading && <Spinner />}
      <ErrorMessage error={orders.error} onRetry={orders.reload} />
      <div className="table-wrap">
        <table className="table card">
          <thead>
            <tr>
              <th>#</th>
              <th>Customer</th>
              <th>Restaurant</th>
              <th>Placed</th>
              <th>Status</th>
              <th>Payment</th>
              <th>Total</th>
            </tr>
          </thead>
          <tbody>
            {orders.data?.content.map((o) => (
              <tr key={o.id}>
                <td>{o.id}</td>
                <td>{o.customerName}</td>
                <td>{o.restaurantName}</td>
                <td>{formatDateTime(o.createdAt)}</td>
                <td>
                  <StatusBadge status={o.status} />
                </td>
                <td>
                  <StatusBadge status={o.paymentStatus} />
                </td>
                <td>{formatPrice(o.totalAmount)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <Pagination page={orders.data} onChange={(next) => setParams({ ...(status && { status }), page: String(next) })} />
    </section>
  );
}
