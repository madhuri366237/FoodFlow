import { useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import * as orderService from '../../services/orderService.js';
import * as ownerService from '../../services/ownerService.js';
import useApi from '../../hooks/useApi.js';
import StatusBadge from '../../components/StatusBadge.jsx';
import Spinner from '../../components/Spinner.jsx';
import ErrorMessage from '../../components/ErrorMessage.jsx';
import { formatDateTime, formatEnum, formatPrice } from '../../utils/format.js';
import { getErrorMessage } from '../../utils/errors.js';

// The action buttons are exactly order.allowedTransitions, as computed by the server's state
// machine for THIS user (e.g. "Confirm" is absent until an online order has been paid).
export default function OwnerOrderDetailPage() {
  const { id } = useParams();
  const order = useApi(() => orderService.getOrder(id), [id]);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);

  if (order.loading && !order.data) return <Spinner />;
  if (order.error) return <ErrorMessage error={order.error} />;
  const o = order.data;

  async function move(status) {
    let note = null;
    if (status === 'CANCELLED') {
      note = window.prompt('Reason for rejecting this order?');
      if (note === null) return;
    }
    setBusy(true);
    setError(null);
    try {
      order.setData(await ownerService.changeOrderStatus(o.id, status, note));
    } catch (err) {
      setError(getErrorMessage(err)); // 409 INVALID_STATUS_TRANSITION / PAYMENT_PENDING / concurrent change
      order.reload();
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="page narrow">
      <p>
        <Link to="/owner/orders">← Orders</Link>
      </p>
      <div className="row-between">
        <h1>Order #{o.id}</h1>
        <StatusBadge status={o.status} />
      </div>
      <p className="muted">
        {o.restaurantName} · {formatDateTime(o.createdAt)}
      </p>
      {error && <div className="alert alert-error">{error}</div>}

      <div className="card">
        <p>
          <strong>{o.customerName}</strong> {o.customerPhone && <span className="muted">· {o.customerPhone}</span>}
        </p>
        <p className="muted small">{o.deliveryAddress}</p>
        <p className="small">
          {formatEnum(o.paymentMethod)} · <StatusBadge status={o.paymentStatus} />
        </p>
      </div>

      <div className="summary card">
        {o.items.map((item) => (
          <div key={item.id} className="row-between">
            <span>
              {item.name} × {item.quantity}
            </span>
            <span>{formatPrice(item.lineTotal)}</span>
          </div>
        ))}
        <hr />
        <div className="row-between total">
          <span>Total</span>
          <span>{formatPrice(o.totalAmount)}</span>
        </div>
      </div>

      {o.status === 'PLACED' && o.paymentMethod !== 'CASH_ON_DELIVERY' && o.paymentStatus === 'PENDING' && (
        <p className="alert alert-warning">Waiting for the customer to pay before you can confirm.</p>
      )}

      <div className="row gap section">
        {o.allowedTransitions.map((next) => (
          <button
            key={next}
            type="button"
            disabled={busy}
            className={`btn ${next === 'CANCELLED' ? 'btn-outline danger' : 'btn-primary'}`}
            onClick={() => move(next)}
          >
            {next === 'CANCELLED' ? 'Reject order' : `Mark ${formatEnum(next).toLowerCase()}`}
          </button>
        ))}
      </div>

      <h2>History</h2>
      <ul className="status-breakdown card">
        {o.statusHistory.map((entry, index) => (
          <li key={index} className="row-between small">
            <span>
              {formatEnum(entry.status)}
              {entry.note && <span className="muted"> ({entry.note})</span>}
            </span>
            <span className="muted">{formatDateTime(entry.at)}</span>
          </li>
        ))}
      </ul>
    </section>
  );
}
