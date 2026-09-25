import { useState } from 'react';
import { Link, useLocation, useParams } from 'react-router-dom';
import * as restaurantService from '../services/restaurantService.js';
import useApi from '../hooks/useApi.js';
import { useAuth } from '../context/AuthContext.jsx';
import { useCart } from '../context/CartContext.jsx';
import Spinner from '../components/Spinner.jsx';
import ErrorMessage from '../components/ErrorMessage.jsx';
import { formatDateTime, formatPrice } from '../utils/format.js';
import { getErrorMessage } from '../utils/errors.js';

// Groups the flat menu by category name, keeping the server's order within each group.
function groupByCategory(items) {
  const groups = new Map();
  for (const item of items) {
    if (!groups.has(item.categoryName)) groups.set(item.categoryName, []);
    groups.get(item.categoryName).push(item);
  }
  return [...groups.entries()];
}

export default function RestaurantDetailPage() {
  const { id } = useParams();
  const location = useLocation();
  const { isAuthenticated, hasRole } = useAuth();
  const { cart, addItem } = useCart();
  const [adding, setAdding] = useState(null);
  const [notice, setNotice] = useState(null);

  const restaurant = useApi(() => restaurantService.getRestaurant(id), [id]);
  const menu = useApi(() => restaurantService.getMenu(id), [id]);
  const reviews = useApi(() => restaurantService.getReviews(id), [id]);

  const quantityInCart = (menuItemId) =>
    cart?.restaurantId === Number(id) ? cart.items.find((line) => line.menuItemId === menuItemId)?.quantity ?? 0 : 0;

  async function add(item) {
    setAdding(item.id);
    setNotice(null);
    try {
      const added = await addItem(item.id, 1);
      if (added) setNotice({ type: 'success', text: `${item.name} added to your cart.` });
    } catch (err) {
      setNotice({ type: 'error', text: getErrorMessage(err) }); // e.g. RESTAURANT_CLOSED
    } finally {
      setAdding(null);
    }
  }

  if (restaurant.loading) return <Spinner />;
  if (restaurant.error) return <ErrorMessage error={restaurant.error} onRetry={restaurant.reload} />;
  const r = restaurant.data;

  return (
    <section className="page">
      <header className="restaurant-header card">
        <div>
          <h1>{r.name}</h1>
          {r.description && <p className="muted">{r.description}</p>}
          <p className="muted small">
            {r.address} · {r.phone}
          </p>
        </div>
        <div className="rating-big">
          <strong>★ {r.ratingCount > 0 ? Number(r.rating).toFixed(1) : 'New'}</strong>
          <span className="muted small">{r.ratingCount} reviews</span>
        </div>
      </header>

      {!r.open && <div className="alert alert-warning">This restaurant is closed right now and isn&apos;t taking orders.</div>}
      {notice && (
        <div className={`alert alert-${notice.type}`} role="status">
          {notice.text} {notice.type === 'success' && <Link to="/cart">View cart</Link>}
        </div>
      )}

      <h2>Menu</h2>
      {menu.loading && <Spinner />}
      <ErrorMessage error={menu.error} onRetry={menu.reload} />
      {menu.data?.length === 0 && <p className="muted">No dishes available right now.</p>}
      {menu.data &&
        groupByCategory(menu.data).map(([category, items]) => (
          <div key={category} className="menu-group">
            <h3>{category}</h3>
            <ul className="menu-list">
              {items.map((item) => (
                <li key={item.id} className="menu-item">
                  <div>
                    <strong>{item.name}</strong>
                    {item.description && <p className="muted small">{item.description}</p>}
                  </div>
                  <span className="price">{formatPrice(item.price)}</span>
                  {hasRole('CUSTOMER') && (
                    <button
                      type="button"
                      className="btn btn-sm btn-outline"
                      disabled={!r.open || adding === item.id}
                      onClick={() => add(item)}
                    >
                      {quantityInCart(item.id) > 0 ? `Add (${quantityInCart(item.id)} in cart)` : 'Add'}
                    </button>
                  )}
                  {!isAuthenticated && (
                    <Link to="/login" state={{ from: location }} className="btn btn-sm btn-outline">
                      Log in to order
                    </Link>
                  )}
                </li>
              ))}
            </ul>
          </div>
        ))}

      <h2>Reviews</h2>
      {reviews.data?.content.length === 0 && <p className="muted">No reviews yet.</p>}
      <ul className="review-list">
        {reviews.data?.content.map((review) => (
          <li key={review.id} className="card">
            <div className="row-between">
              <strong>{review.customerName}</strong>
              <span className="rating">{'★'.repeat(review.rating)}{'☆'.repeat(5 - review.rating)}</span>
            </div>
            {review.comment && <p>{review.comment}</p>}
            <p className="muted small">{formatDateTime(review.createdAt)}</p>
          </li>
        ))}
      </ul>
    </section>
  );
}
