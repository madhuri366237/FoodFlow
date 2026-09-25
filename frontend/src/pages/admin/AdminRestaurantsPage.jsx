import { useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import * as adminService from '../../services/adminService.js';
import useApi from '../../hooks/useApi.js';
import { AdminNav } from './AdminDashboardPage.jsx';
import Pagination from '../../components/Pagination.jsx';
import Spinner from '../../components/Spinner.jsx';
import ErrorMessage from '../../components/ErrorMessage.jsx';
import { getErrorMessage } from '../../utils/errors.js';

export default function AdminRestaurantsPage() {
  const [params, setParams] = useSearchParams();
  const filters = { active: params.get('active') ?? '', keyword: params.get('keyword') ?? '', page: Number(params.get('page') ?? 0) };
  const restaurants = useApi(() => adminService.getRestaurants(filters), [params.toString()]);
  const [keyword, setKeyword] = useState(filters.keyword);
  const [error, setError] = useState(null);

  function setFilter(name, value) {
    const next = new URLSearchParams(params);
    if (value) next.set(name, value);
    else next.delete(name);
    if (name !== 'page') next.delete('page');
    setParams(next);
  }

  async function toggle(restaurant) {
    const verb = restaurant.active ? 'Deactivate' : 'Reactivate';
    if (!window.confirm(`${verb} ${restaurant.name}?${restaurant.active ? ' Customers will no longer see it.' : ''}`)) return;
    setError(null);
    try {
      const updated = await adminService.setRestaurantActive(restaurant.id, !restaurant.active);
      restaurants.setData((page) => ({ ...page, content: page.content.map((r) => (r.id === updated.id ? updated : r)) }));
    } catch (err) {
      setError(getErrorMessage(err));
    }
  }

  return (
    <section className="page">
      <h1>Restaurants</h1>
      <AdminNav />
      <form
        className="filters filters-3 card"
        onSubmit={(e) => {
          e.preventDefault();
          setFilter('keyword', keyword.trim());
        }}
      >
        <input type="search" placeholder="Restaurant or dish" value={keyword} onChange={(e) => setKeyword(e.target.value)} aria-label="Search restaurants" />
        <select value={filters.active} onChange={(e) => setFilter('active', e.target.value)} aria-label="Status">
          <option value="">Active and deactivated</option>
          <option value="true">Active</option>
          <option value="false">Deactivated</option>
        </select>
        <button type="submit" className="btn btn-primary">
          Search
        </button>
      </form>
      {error && <div className="alert alert-error">{error}</div>}
      {restaurants.loading && <Spinner />}
      <ErrorMessage error={restaurants.error} onRetry={restaurants.reload} />
      <div className="table-wrap">
        <table className="table card">
          <thead>
            <tr>
              <th>Restaurant</th>
              <th>Owner</th>
              <th>Rating</th>
              <th>Open</th>
              <th>Status</th>
            </tr>
          </thead>
          <tbody>
            {restaurants.data?.content.map((r) => (
              <tr key={r.id}>
                <td>
                  <strong>{r.name}</strong>
                  <br />
                  <span className="muted small">{r.address}</span>
                </td>
                <td>
                  {r.ownerName}
                  <br />
                  <span className="muted small">{r.ownerEmail}</span>
                </td>
                <td>{r.ratingCount ? `★ ${Number(r.rating).toFixed(1)} (${r.ratingCount})` : '-'}</td>
                <td>{r.open ? 'Yes' : 'No'}</td>
                <td>
                  <button type="button" className={`btn btn-sm ${r.active ? 'btn-outline' : 'btn-primary'}`} onClick={() => toggle(r)}>
                    {r.active ? 'Deactivate' : 'Reactivate'}
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <Pagination page={restaurants.data} onChange={(page) => setFilter('page', String(page))} />
    </section>
  );
}
