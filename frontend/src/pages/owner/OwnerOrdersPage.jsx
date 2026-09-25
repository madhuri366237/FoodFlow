import { Link, useSearchParams } from 'react-router-dom';
import * as ownerService from '../../services/ownerService.js';
import useApi from '../../hooks/useApi.js';
import StatusBadge from '../../components/StatusBadge.jsx';
import Pagination from '../../components/Pagination.jsx';
import Spinner from '../../components/Spinner.jsx';
import ErrorMessage from '../../components/ErrorMessage.jsx';
import { formatDateTime, formatEnum, formatPrice } from '../../utils/format.js';

const FILTERS = ['', 'PLACED', 'CONFIRMED', 'PREPARING', 'READY_FOR_PICKUP', 'OUT_FOR_DELIVERY', 'DELIVERED', 'CANCELLED'];

export default function OwnerOrdersPage() {
  const [params, setParams] = useSearchParams();
  const status = params.get('status') ?? '';
  const page = Number(params.get('page') ?? 0);
  const orders = useApi(() => ownerService.getOrders({ status, page }), [status, page]);

  return (
    <section className="page narrow">
      <p>
        <Link to="/owner">← Dashboard</Link>
      </p>
      <div className="row-between">
        <h1>Incoming orders</h1>
        <button type="button" className="btn btn-outline btn-sm" onClick={orders.reload}>
          Refresh
        </button>
      </div>
      <div className="chips">
        {FILTERS.map((value) => (
          <button
            key={value || 'all'}
            type="button"
            className={`chip ${status === value ? 'chip-active' : ''}`}
            onClick={() => setParams(value ? { status: value } : {})}
          >
            {value ? formatEnum(value) : 'All'}
          </button>
        ))}
      </div>

      {orders.loading && <Spinner />}
      <ErrorMessage error={orders.error} onRetry={orders.reload} />
      {orders.data?.content.length === 0 && <p className="muted section">No orders here.</p>}
      <ul className="order-list section">
        {orders.data?.content.map((order) => (
          <li key={order.id}>
            <Link to={`/owner/orders/${order.id}`} className="card order-row">
              <div>
                <strong>
                  #{order.id} · {order.customerName}
                </strong>
                <p className="muted small">
                  {order.restaurantName} · {order.itemCount} items · {formatDateTime(order.createdAt)}
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
      <Pagination
        page={orders.data}
        onChange={(next) => setParams({ ...(status && { status }), page: String(next) })}
      />
    </section>
  );
}
