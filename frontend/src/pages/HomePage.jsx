import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import * as restaurantService from '../services/restaurantService.js';
import useApi from '../hooks/useApi.js';
import RestaurantCard from '../components/RestaurantCard.jsx';
import Spinner from '../components/Spinner.jsx';
import ErrorMessage from '../components/ErrorMessage.jsx';

export default function HomePage() {
  const navigate = useNavigate();
  const [keyword, setKeyword] = useState('');
  const categories = useApi(() => restaurantService.getCategories(), []);
  const popular = useApi(() => restaurantService.searchRestaurants({ size: 6, sort: 'rating,desc' }), []);

  function search(event) {
    event.preventDefault();
    navigate(`/restaurants?keyword=${encodeURIComponent(keyword.trim())}`);
  }

  return (
    <>
      <section className="hero">
        <h1>Hungry? Order from the best places near you.</h1>
        <form className="search-bar" onSubmit={search} role="search">
          <input
            type="search"
            placeholder="Search restaurants or dishes, e.g. biryani"
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            aria-label="Search restaurants or dishes"
          />
          <button type="submit" className="btn btn-primary">
            Search
          </button>
        </form>
      </section>

      <section className="section">
        <h2>Categories</h2>
        <ErrorMessage error={categories.error} />
        <div className="chips">
          {categories.data?.map((category) => (
            <Link key={category.id} to={`/restaurants?categoryId=${category.id}`} className="chip">
              {category.name}
            </Link>
          ))}
        </div>
      </section>

      <section className="section">
        <div className="row-between">
          <h2>Popular restaurants</h2>
          <Link to="/restaurants">See all</Link>
        </div>
        {popular.loading && <Spinner />}
        <ErrorMessage error={popular.error} onRetry={popular.reload} />
        <div className="card-grid">
          {popular.data?.content.map((restaurant) => (
            <RestaurantCard key={restaurant.id} restaurant={restaurant} />
          ))}
        </div>
      </section>
    </>
  );
}
