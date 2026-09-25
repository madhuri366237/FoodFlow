import { useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import * as restaurantService from '../services/restaurantService.js';
import useApi from '../hooks/useApi.js';
import RestaurantCard from '../components/RestaurantCard.jsx';
import Pagination from '../components/Pagination.jsx';
import Spinner from '../components/Spinner.jsx';
import ErrorMessage from '../components/ErrorMessage.jsx';
import EmptyState from '../components/EmptyState.jsx';

const SORTS = [
  { value: 'rating,desc', label: 'Top rated' },
  { value: 'name,asc', label: 'Name (A-Z)' },
  { value: 'createdAt,desc', label: 'Newest' },
];

/**
 * The URL is the state: /restaurants?keyword=biryani&minRating=4&page=1
 * Filters survive reloads, the back button works, and a search can be shared as a link.
 * Filtering, sorting and paging all happen in PostgreSQL; the browser receives one page.
 */
export default function RestaurantsPage() {
  const [params, setParams] = useSearchParams();
  const filters = {
    keyword: params.get('keyword') ?? '',
    categoryId: params.get('categoryId') ?? '',
    minRating: params.get('minRating') ?? '',
    open: params.get('open') ?? '',
    sort: params.get('sort') ?? 'rating,desc',
    page: Number(params.get('page') ?? 0),
  };
  const [keywordInput, setKeywordInput] = useState(filters.keyword);
  useEffect(() => setKeywordInput(filters.keyword), [filters.keyword]);

  const categories = useApi(() => restaurantService.getCategories(), []);
  const results = useApi(() => restaurantService.searchRestaurants({ ...filters, size: 12 }), [params.toString()]);

  // Any filter change goes back to page 0; only the pager changes the page.
  function setFilter(name, value) {
    const next = new URLSearchParams(params);
    if (value === '' || value === null) next.delete(name);
    else next.set(name, value);
    if (name !== 'page') next.delete('page');
    setParams(next);
  }

  return (
    <section className="page">
      <h1>Restaurants</h1>

      <form
        className="filters card"
        onSubmit={(event) => {
          event.preventDefault();
          setFilter('keyword', keywordInput.trim());
        }}
      >
        <input
          type="search"
          placeholder="Restaurant or dish"
          value={keywordInput}
          onChange={(e) => setKeywordInput(e.target.value)}
          aria-label="Keyword"
        />
        <select value={filters.categoryId} onChange={(e) => setFilter('categoryId', e.target.value)} aria-label="Category">
          <option value="">All categories</option>
          {categories.data?.map((category) => (
            <option key={category.id} value={category.id}>
              {category.name}
            </option>
          ))}
        </select>
        <select value={filters.minRating} onChange={(e) => setFilter('minRating', e.target.value)} aria-label="Minimum rating">
          <option value="">Any rating</option>
          <option value="4.5">4.5+</option>
          <option value="4">4.0+</option>
          <option value="3.5">3.5+</option>
        </select>
        <select value={filters.open} onChange={(e) => setFilter('open', e.target.value)} aria-label="Open now">
          <option value="">Open or closed</option>
          <option value="true">Open now</option>
        </select>
        <select value={filters.sort} onChange={(e) => setFilter('sort', e.target.value)} aria-label="Sort">
          {SORTS.map((sort) => (
            <option key={sort.value} value={sort.value}>
              {sort.label}
            </option>
          ))}
        </select>
        <button type="submit" className="btn btn-primary">
          Search
        </button>
      </form>

      {results.loading && <Spinner />}
      <ErrorMessage error={results.error} onRetry={results.reload} />
      {results.data && results.data.content.length === 0 && (
        <EmptyState title="No restaurants match">
          <p className="muted">Try another keyword or remove a filter.</p>
        </EmptyState>
      )}
      <div className="card-grid">
        {results.data?.content.map((restaurant) => (
          <RestaurantCard key={restaurant.id} restaurant={restaurant} />
        ))}
      </div>
      <Pagination page={results.data} onChange={(page) => setFilter('page', String(page))} />
    </section>
  );
}
