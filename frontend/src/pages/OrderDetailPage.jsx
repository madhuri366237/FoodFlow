import { useEffect, useState } from 'react';
import { Link, useLocation, useParams } from 'react-router-dom';
import * as orderService from '../services/orderService.js';
import * as restaurantService from '../services/restaurantService.js';
import useApi from '../hooks/useApi.js';
import StatusBadge from '../components/StatusBadge.jsx';
import Spinner from '../components/Spinner.jsx';
import ErrorMessage from '../components/ErrorMessage.jsx';
import { formatDateTime, formatEnum, formatPrice } from '../utils/format.js';
import { getErrorMessage } from '../utils/errors.js';

const STEPS = ['PLACED', 'CONFIRMED', 'PREPARING', 'READY_FOR_PICKUP', 'OUT_FOR_DELIVERY', 'DELIVERED'];
const TERMINAL = ['DELIVERED', 'CANCELLED'];
const POLL_MS = 15000;

export default function OrderDetailPage() {
  const { id } = useParams();
  const location = useLocation();
  const order = useApi(() => orderService.getOrder(id), [id]);
  const [actionError, setActionError] = useState(null);
  const [cancelling, setCancelling] = useState(false);

  // Simple live tracking: re-fetch every 15 s until the order is delivered or cancelled.
  // (WebSockets/SSE would push updates instead; polling is enough at this scale.)
  const status = order.data?.status;
  useEffect(() => {
    if (!status || TERMINAL.includes(status)) return undefined;
    // Skip polls while the tab is in the background; nobody is looking at it.
    const timer = setInterval(() => document.visibilityState === 'visible' && order.reload(), POLL_MS);
    return () => clearInterval(timer);
  }, [status, order.reload]);

  if (order.loading && !order.data) return <Spinner />;
  if (order.error && !order.data) return <ErrorMessage error={order.error} onRetry={order.reload} />;
  const o = order.data;

  async function cancel() {
    const reason = window.prompt('Why are you cancelling? (optional)');
    if (reason === null) return; // dialog dismissed
    setCancelling(true);
    setActionError(null);
    try {
      order.setData(await orderService.cancelOrder(o.id, reason));
      // A paid order is refunded just AFTER the cancel commits (Phase 7), so fetch again shortly.
      setTimeout(order.reload, 1500);
    } catch (err) {
      setActionError(getErrorMessage(err));
    } finally {
      setCancelling(false);
    }
  }

  const reached = (step) => o.statusHistory.some((entry) => entry.status === step);
  const timeOf = (step) => o.statusHistory.find((entry) => entry.status === step)?.at;
  const awaitingPayment = o.status === 'PLACED' && o.paymentMethod !== 'CASH_ON_DELIVERY' && o.paymentStatus === 'PENDING';

  return (
    <section className="page narrow">
      {location.state?.justPlaced && <div className="alert alert-success">Order placed! The restaurant has been notified.</div>}
      {location.state?.paid && <div className="alert alert-success">Payment successful.</div>}

      <div className="row-between">
        <h1>Order #{o.id}</h1>
        <StatusBadge status={o.status} />
      </div>
      <p className="muted">
        <Link to={`/restaurants/${o.restaurantId}`}>{o.restaurantName}</Link> · {formatDateTime(o.createdAt)}
      </p>

      {awaitingPayment && (
        <div className="alert alert-warning">
          Waiting for payment. The restaurant starts preparing once it&apos;s paid.{' '}
          <Link to={`/orders/${o.id}/pay`} className="btn btn-sm btn-primary">
            Pay now
          </Link>
        </div>
      )}
      {actionError && <div className="alert alert-error">{actionError}</div>}

      {o.status === 'CANCELLED' ? (
        <div className="alert alert-error">
          Cancelled{o.cancellationReason ? `: ${o.cancellationReason}` : ''}.
          {o.paymentStatus === 'REFUNDED' && ' Your payment has been refunded.'}
        </div>
      ) : (
        <ol className="timeline card">
          {STEPS.map((step) => (
            <li key={step} className={reached(step) ? 'done' : ''}>
              <span className="dot" aria-hidden="true" />
              <span>{formatEnum(step)}</span>
              {timeOf(step) && <span className="muted small">{formatDateTime(timeOf(step))}</span>}
            </li>
          ))}
        </ol>
      )}

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
        <div className="row-between">
          <span>Subtotal</span>
          <span>{formatPrice(o.subtotal)}</span>
        </div>
        {Number(o.discountAmount) > 0 && (
          <div className="row-between success-text">
            <span>Discount {o.couponCode && `(${o.couponCode})`}</span>
            <span>− {formatPrice(o.discountAmount)}</span>
          </div>
        )}
        <div className="row-between total">
          <span>Total</span>
          <span>{formatPrice(o.totalAmount)}</span>
        </div>
        <p className="muted small">
          {formatEnum(o.paymentMethod)} · payment {formatEnum(o.paymentStatus).toLowerCase()}
        </p>
        <p className="muted small">Deliver to: {o.deliveryAddress}</p>
      </div>

      {/* The server says which actions this user may take (allowedTransitions). */}
      {o.allowedTransitions.includes('CANCELLED') && (
        <button type="button" className="btn btn-outline danger" disabled={cancelling} onClick={cancel}>
          {cancelling ? 'Cancelling...' : 'Cancel order'}
        </button>
      )}

      {o.status === 'DELIVERED' && !o.reviewed && (
        <ReviewForm order={o} onReviewed={() => order.setData({ ...o, reviewed: true })} />
      )}
      {o.reviewed && <p className="muted">Thanks for reviewing this order.</p>}
    </section>
  );
}

function ReviewForm({ order, onReviewed }) {
  const [rating, setRating] = useState(5);
  const [comment, setComment] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState(null);

  async function submit(event) {
    event.preventDefault();
    setSubmitting(true);
    setError(null);
    try {
      await restaurantService.createReview(order.restaurantId, { orderId: order.id, rating, comment: comment || null });
      onReviewed();
    } catch (err) {
      setError(getErrorMessage(err));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <form className="form card" onSubmit={submit}>
      <h2>Rate your order</h2>
      <div className="star-input" role="radiogroup" aria-label="Rating">
        {[1, 2, 3, 4, 5].map((value) => (
          <button
            key={value}
            type="button"
            role="radio"
            aria-checked={rating === value}
            aria-label={`${value} star${value > 1 ? 's' : ''}`}
            className={value <= rating ? 'on' : ''}
            onClick={() => setRating(value)}
          >
            ★
          </button>
        ))}
      </div>
      <label className="field">
        <span>Comment (optional)</span>
        <textarea maxLength={1000} rows={3} value={comment} onChange={(e) => setComment(e.target.value)} />
      </label>
      {error && <p className="field-error">{error}</p>}
      <button type="submit" className="btn btn-primary" disabled={submitting}>
        {submitting ? 'Submitting...' : 'Submit review'}
      </button>
    </form>
  );
}
