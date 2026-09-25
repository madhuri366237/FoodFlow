import { Link, useSearchParams } from 'react-router-dom';
import * as orderService from '../services/orderService.js';
import useApi from '../hooks/useApi.js';
import StatusBadge from '../components/StatusBadge.jsx';
import Pagination from '../components/Pagination.jsx';
import Spinner from '../components/Spinner.jsx';
import ErrorMessage from '../components/ErrorMessage.jsx';
import EmptyState from '../components/EmptyState.jsx';
import { formatDateTime, formatPrice } from '../utils/format.js';

export default function OrdersPage() {
  const [params, setParams] = useSearchParams();
  const page = Number(params.get('page') ?? 0);
  const orders = useApi(() => orderService.getMyOrders(page, 10), [page]);

  return (
    <section className="page narrow">
      <h1>My orders</h1>
      {orders.loading && <Spinner />}
      <ErrorMessage error={orders.error} onRetry={orders.reload} />
      {orders.data?.content.length === 0 && (
        <EmptyState title="No orders yet">
          <Link to="/restaurants" className="btn btn-primary">
            Order something tasty
          </Link>
        </EmptyState>
      )}
      <ul className="order-list">
        {orders.data?.content.map((order) => (
          <li key={order.id}>
            <Link to={`/orders/${order.id}`} className="card order-row">
              <div>
                <strong>{order.restaurantName}</strong>
                <p className="muted small">
                  #{order.id} · {order.itemCount} item{order.itemCount === 1 ? '' : 's'} · {formatDateTime(order.createdAt)}
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
      <Pagination page={orders.data} onChange={(next) => setParams({ page: String(next) })} />
    </section>
  );
}
