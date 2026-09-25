import { useEffect, useState } from 'react';
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom';
import { useCart } from '../context/CartContext.jsx';
import * as addressService from '../services/addressService.js';
import * as couponService from '../services/couponService.js';
import * as orderService from '../services/orderService.js';
import useApi from '../hooks/useApi.js';
import AddressForm from '../components/AddressForm.jsx';
import Spinner from '../components/Spinner.jsx';
import ErrorMessage from '../components/ErrorMessage.jsx';
import { formatPrice } from '../utils/format.js';
import { getErrorCode } from '../utils/errors.js';

// INVALID_COUPON, COUPON_EXPIRED, COUPON_MINIMUM_NOT_MET, COUPON_USAGE_LIMIT_REACHED, ...
const isCouponError = (error) => /COUPON/.test(getErrorCode(error) ?? '');

const PAYMENT_METHODS = [
  { value: 'UPI', label: 'UPI' },
  { value: 'CARD', label: 'Credit / debit card' },
  { value: 'CASH_ON_DELIVERY', label: 'Cash on delivery' },
];

export default function CheckoutPage() {
  const { cart, refresh } = useCart();
  const navigate = useNavigate();
  const location = useLocation();
  const addresses = useApi(() => addressService.getAddresses(), []);

  const [addressId, setAddressId] = useState(null);
  const [showAddressForm, setShowAddressForm] = useState(false);
  const [paymentMethod, setPaymentMethod] = useState('UPI');
  const [couponCode] = useState(location.state?.couponCode ?? '');
  const [preview, setPreview] = useState(null);
  const [placing, setPlacing] = useState(false);
  const [error, setError] = useState(null);

  // Default to the default address (the API returns it first).
  useEffect(() => {
    if (addressId === null && addresses.data?.length) setAddressId(addresses.data[0].id);
    if (addresses.data?.length === 0) setShowAddressForm(true);
  }, [addresses.data, addressId]);

  // Re-check the coupon from the cart page: it may have expired or run out since.
  useEffect(() => {
    if (!couponCode) return;
    couponService
      .validateCoupon(couponCode)
      .then(setPreview)
      .catch((err) => setError(err));
  }, [couponCode]);

  if (!cart) return <Spinner />;
  if (cart.items.length === 0 && !placing) return <Navigate to="/cart" replace />;

  async function placeOrder() {
    setPlacing(true);
    setError(null);
    try {
      // Only choices are sent. The server rebuilds the order from the cart with current prices,
      // re-validates the coupon and takes one use, all in one database transaction.
      const order = await orderService.placeOrder({ addressId, paymentMethod, couponCode: preview?.code });
      await refresh(); // the server emptied the cart
      navigate(paymentMethod === 'CASH_ON_DELIVERY' ? `/orders/${order.id}` : `/orders/${order.id}/pay`, {
        replace: true,
        state: { justPlaced: true },
      });
    } catch (err) {
      setError(err); // e.g. ITEM_UNAVAILABLE, RESTAURANT_CLOSED, COUPON_USAGE_LIMIT_REACHED
      setPlacing(false);
    }
  }

  return (
    <section className="page narrow">
      <h1>Checkout</h1>
      <ErrorMessage error={error} />

      <h2>Delivery address</h2>
      {addresses.loading && <Spinner />}
      <ErrorMessage error={addresses.error} onRetry={addresses.reload} />
      <div className="option-list">
        {addresses.data?.map((address) => (
          <label key={address.id} className={`option card ${addressId === address.id ? 'selected' : ''}`}>
            <input type="radio" name="address" checked={addressId === address.id} onChange={() => setAddressId(address.id)} />
            <span>
              <strong>{address.label}</strong>
              {address.isDefault && <span className="badge">Default</span>}
              <br />
              <span className="muted small">
                {address.line1}
                {address.line2 && `, ${address.line2}`}, {address.city}, {address.state} {address.postalCode}
              </span>
            </span>
          </label>
        ))}
      </div>
      {showAddressForm ? (
        <AddressForm
          onSaved={(saved) => {
            setShowAddressForm(false);
            setAddressId(saved.id);
            addresses.reload();
          }}
          onCancel={addresses.data?.length ? () => setShowAddressForm(false) : undefined}
        />
      ) : (
        <button type="button" className="btn btn-outline btn-sm" onClick={() => setShowAddressForm(true)}>
          + Add a new address
        </button>
      )}

      <h2>Payment method</h2>
      <div className="option-list">
        {PAYMENT_METHODS.map((method) => (
          <label key={method.value} className={`option card ${paymentMethod === method.value ? 'selected' : ''}`}>
            <input
              type="radio"
              name="payment"
              value={method.value}
              checked={paymentMethod === method.value}
              onChange={() => setPaymentMethod(method.value)}
            />
            <span>{method.label}</span>
          </label>
        ))}
      </div>

      <div className="summary card">
        <h2>Order summary</h2>
        <p className="muted">{cart.restaurantName}</p>
        {cart.items.map((line) => (
          <div key={line.id} className="row-between small">
            <span>
              {line.name} × {line.quantity}
            </span>
            <span>{formatPrice(line.lineTotal)}</span>
          </div>
        ))}
        <hr />
        <div className="row-between">
          <span>Subtotal</span>
          <span>{formatPrice(cart.subtotal)}</span>
        </div>
        {preview && (
          <div className="row-between success-text">
            <span>Coupon {preview.code}</span>
            <span>− {formatPrice(preview.discount)}</span>
          </div>
        )}
        <div className="row-between total">
          <span>To pay</span>
          <span>{formatPrice(preview ? preview.total : cart.subtotal)}</span>
        </div>
        <button
          type="button"
          className="btn btn-primary btn-block"
          disabled={!addressId || placing || !cart.checkoutReady}
          onClick={placeOrder}
        >
          {placing ? 'Placing order...' : paymentMethod === 'CASH_ON_DELIVERY' ? 'Place order' : 'Continue to payment'}
        </button>
        <p className="muted small center">
          <Link to="/cart">Back to cart</Link>
        </p>
      </div>
      {isCouponError(error) && (
        <p className="muted small">The coupon was not applied. You can place the order without it.</p>
      )}
    </section>
  );
}
