import { Link } from 'react-router-dom';

export default function RestaurantCard({ restaurant }) {
  const rating = Number(restaurant.rating);
  return (
    <Link to={`/restaurants/${restaurant.id}`} className="card restaurant-card">
      <div className="restaurant-card-image" aria-hidden="true">
        {restaurant.imageUrl ? <img src={restaurant.imageUrl} alt="" /> : <span>{restaurant.name.charAt(0)}</span>}
      </div>
      <div className="restaurant-card-body">
        <div className="row-between">
          <h3>{restaurant.name}</h3>
          <span className="rating" title={`${restaurant.ratingCount} reviews`}>
            ★ {restaurant.ratingCount > 0 ? rating.toFixed(1) : 'New'}
          </span>
        </div>
        {restaurant.description && <p className="muted clamp">{restaurant.description}</p>}
        <p className="muted small">{restaurant.address}</p>
        {!restaurant.open && <span className="badge badge-cancelled">Closed now</span>}
      </div>
    </Link>
  );
}
