import { useState } from 'react';
import { Link, Navigate, useNavigate, useParams } from 'react-router-dom';
import * as orderService from '../services/orderService.js';
import * as paymentService from '../services/paymentService.js';
import useApi from '../hooks/useApi.js';
import Spinner from '../components/Spinner.jsx';
import ErrorMessage from '../components/ErrorMessage.jsx';
import { formatEnum, formatPrice } from '../utils/format.js';

// Stand-in for the real gateway widget. In production, Razorpay/Stripe's own UI collects the
// card/UPI details and gives us a token; here the tester chooses the token and so the outcome.
const SCENARIOS = [
  { token: 'tok_visa', label: 'Payment succeeds' },
  { token: 'tok_chargeDeclined', label: 'Card is declined' },
  { token: 'tok_insufficientFunds', label: 'Insufficient funds' },
  { token: 'tok_timeout', label: 'Gateway is down' },
];

export default function PaymentPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const order = useApi(() => orderService.getOrder(id), [id]);
  const [token, setToken] = useState(SCENARIOS[0].token);
  const [paying, setPaying] = useState(false);
  const [result, setResult] = useState(null);
  const [error, setError] = useState(null);

  if (order.loading) return <Spinner />;
  if (order.error) return <ErrorMessage error={order.error} />;
  const o = order.data;

  // Nothing to pay: cash on delivery, already paid, or cancelled.
  if (o.paymentMethod === 'CASH_ON_DELIVERY' || o.paymentStatus !== 'PENDING' || o.status !== 'PLACED') {
    return <Navigate to={`/orders/${id}`} replace />;
  }

  async function pay() {
    setPaying(true);
    setError(null);
    setResult(null);
    try {
      const payment = await paymentService.pay(o.id, token);
      if (payment.status === 'SUCCESS') {
        navigate(`/orders/${o.id}`, { replace: true, state: { paid: true } });
        return;
      }
      setResult(payment); // FAILED is a normal outcome (201), not an HTTP error
    } catch (err) {
      setError(err); // e.g. ALREADY_PAID, PAYMENT_IN_PROGRESS
    } finally {
      setPaying(false);
    }
  }

  return (
    <section className="page narrow">
      <h1>Pay for order #{o.id}</h1>
      <div className="summary card">
        <div className="row-between">
          <span>{o.restaurantName}</span>
          <span>{formatEnum(o.paymentMethod)}</span>
        </div>
        <div className="row-between total">
          <span>Amount</span>
          <span>{formatPrice(o.totalAmount)}</span>
        </div>
      </div>

      <div className="card">
        <h2>Simulated payment gateway</h2>
        <p className="muted small">No real money moves. Pick the outcome to test:</p>
        <div className="option-list">
          {SCENARIOS.map((scenario) => (
            <label key={scenario.token} className={`option ${token === scenario.token ? 'selected' : ''}`}>
              <input type="radio" name="scenario" checked={token === scenario.token} onChange={() => setToken(scenario.token)} />
              <span>
                {scenario.label} <code className="muted small">{scenario.token}</code>
              </span>
            </label>
          ))}
        </div>
        {result && (
          <div className="alert alert-error" role="alert">
            Payment failed: {result.failureReason}. You have not been charged; you can try again.
          </div>
        )}
        <ErrorMessage error={error} />
        <button type="button" className="btn btn-primary btn-block" disabled={paying} onClick={pay}>
          {paying ? 'Processing...' : `Pay ${formatPrice(o.totalAmount)}`}
        </button>
        <p className="muted small center">
          <Link to={`/orders/${o.id}`}>Pay later</Link>
        </p>
      </div>
    </section>
  );
}
