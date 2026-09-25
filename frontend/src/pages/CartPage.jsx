import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useCart } from '../context/CartContext.jsx';
import * as couponService from '../services/couponService.js';
import QuantityStepper from '../components/QuantityStepper.jsx';
import EmptyState from '../components/EmptyState.jsx';
import Spinner from '../components/Spinner.jsx';
import { formatPrice } from '../utils/format.js';
import { getErrorMessage } from '../utils/errors.js';

export default function CartPage() {
  const { cart, updateQuantity, removeItem, clear } = useCart();
  const navigate = useNavigate();
  const [busyLine, setBusyLine] = useState(null);
  const [error, setError] = useState(null);
  const [couponInput, setCouponInput] = useState('');
  const [coupon, setCoupon] = useState(null); // server's preview: { code, discount, total }
  const [couponError, setCouponError] = useState(null);

  if (!cart) return <Spinner label="Loading your cart..." />;
  if (cart.items.length === 0) {
    return (
      <EmptyState title="Your cart is empty">
        <Link to="/restaurants" className="btn btn-primary">
          Browse restaurants
        </Link>
      </EmptyState>
    );
  }

  // Every change goes to the server and the returned cart replaces ours: totals are never
  // computed in the browser. A coupon preview is tied to the old subtotal, so it's reset.
  async function run(lineId, action) {
    setBusyLine(lineId);
    setError(null);
    setCoupon(null);
    try {
      await action();
    } catch (err) {
      setError(getErrorMessage(err));
    } finally {
      setBusyLine(null);
    }
  }

  async function applyCoupon(event) {
    event.preventDefault();
    setCouponError(null);
    try {
      setCoupon(await couponService.validateCoupon(couponInput.trim()));
    } catch (err) {
      setCoupon(null);
      setCouponError(getErrorMessage(err)); // COUPON_EXPIRED, COUPON_MINIMUM_NOT_MET, ...
    }
  }

  return (
    <section className="page narrow">
      <div className="row-between">
        <h1>Your cart</h1>
        <button type="button" className="btn btn-sm btn-outline" onClick={() => run('all', clear)}>
          Clear cart
        </button>
      </div>
      <p className="muted">
        From <Link to={`/restaurants/${cart.restaurantId}`}>{cart.restaurantName}</Link>
      </p>
      {error && <div className="alert alert-error">{error}</div>}

      <ul className="cart-lines card">
        {cart.items.map((line) => (
          <li key={line.id} className={line.available ? 'cart-line' : 'cart-line unavailable'}>
            <div>
              <strong>{line.name}</strong>
              <p className="muted small">{formatPrice(line.unitPrice)} each</p>
              {!line.available && <p className="field-error small">No longer available. Remove it to check out.</p>}
            </div>
            <QuantityStepper
              value={line.quantity}
              disabled={busyLine === line.id}
              onChange={(quantity) => run(line.id, () => updateQuantity(line.id, quantity))}
            />
            <span className="price">{formatPrice(line.lineTotal)}</span>
            <button type="button" className="btn-link danger" onClick={() => run(line.id, () => removeItem(line.id))}>
              Remove
            </button>
          </li>
        ))}
      </ul>

      <form className="coupon-form" onSubmit={applyCoupon}>
        <input
          placeholder="Coupon code"
          value={couponInput}
          onChange={(e) => setCouponInput(e.target.value.toUpperCase())}
          aria-label="Coupon code"
        />
        <button type="submit" className="btn btn-outline" disabled={!couponInput.trim()}>
          Apply
        </button>
      </form>
      {couponError && <p className="field-error">{couponError}</p>}

      <div className="summary card">
        <div className="row-between">
          <span>Subtotal</span>
          <span>{formatPrice(cart.subtotal)}</span>
        </div>
        {coupon && (
          <div className="row-between success-text">
            <span>
              Coupon {coupon.code}{' '}
              <button type="button" className="btn-link" onClick={() => setCoupon(null)}>
                remove
              </button>
            </span>
            <span>− {formatPrice(coupon.discount)}</span>
          </div>
        )}
        <div className="row-between total">
          <span>Total</span>
          <span>{formatPrice(coupon ? coupon.total : cart.subtotal)}</span>
        </div>
        <button
          type="button"
          className="btn btn-primary btn-block"
          disabled={!cart.checkoutReady}
          onClick={() => navigate('/checkout', { state: { couponCode: coupon?.code ?? '' } })}
        >
          Proceed to checkout
        </button>
        {!cart.checkoutReady && <p className="muted small center">Remove unavailable items or wait until the restaurant opens.</p>}
      </div>
    </section>
  );
}
