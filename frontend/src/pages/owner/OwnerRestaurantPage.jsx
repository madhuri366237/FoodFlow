import { useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import * as ownerService from '../../services/ownerService.js';
import * as restaurantService from '../../services/restaurantService.js';
import useApi from '../../hooks/useApi.js';
import Spinner from '../../components/Spinner.jsx';
import ErrorMessage from '../../components/ErrorMessage.jsx';
import RestaurantForm from './RestaurantForm.jsx';
import { formatPrice } from '../../utils/format.js';
import { getErrorMessage, getFieldErrors } from '../../utils/errors.js';

/**
 * Manage one restaurant: details, open/closed, and the full menu (add, edit, enable/disable,
 * delete). Every change is a request; the backend checks that this owner owns the restaurant.
 */
export default function OwnerRestaurantPage() {
  const { id } = useParams();
  const restaurants = useApi(() => ownerService.getMyRestaurants(), []);
  const menu = useApi(() => ownerService.getFullMenu(id), [id]);
  const categories = useApi(() => restaurantService.getCategories(), []);
  const [editing, setEditing] = useState(false);
  const [editingItem, setEditingItem] = useState(null); // null | 'new' | item
  const [error, setError] = useState(null);

  const restaurant = restaurants.data?.content.find((r) => r.id === Number(id));

  async function run(action) {
    setError(null);
    try {
      await action();
    } catch (err) {
      setError(getErrorMessage(err));
    }
  }

  if (restaurants.loading) return <Spinner />;
  if (restaurants.error) return <ErrorMessage error={restaurants.error} />;
  if (!restaurant) return <ErrorMessage message="Restaurant not found among your restaurants." />;

  return (
    <section className="page">
      <p>
        <Link to="/owner">← Dashboard</Link>
      </p>
      <div className="row-between">
        <h1>{restaurant.name}</h1>
        <button
          type="button"
          className={`btn btn-sm ${restaurant.open ? 'btn-outline' : 'btn-primary'}`}
          onClick={() => run(async () => {
            await ownerService.setOpen(restaurant.id, !restaurant.open);
            restaurants.reload();
          })}
        >
          {restaurant.open ? 'Close for orders' : 'Open for orders'}
        </button>
      </div>
      {!restaurant.active && (
        <div className="alert alert-warning">An admin has deactivated this restaurant; customers can&apos;t see it.</div>
      )}
      {error && <div className="alert alert-error">{error}</div>}

      {editing ? (
        <RestaurantForm
          restaurant={restaurant}
          onSaved={() => {
            setEditing(false);
            restaurants.reload();
          }}
          onCancel={() => setEditing(false)}
        />
      ) : (
        <div className="card row-between">
          <div>
            <p>{restaurant.description || <span className="muted">No description</span>}</p>
            <p className="muted small">
              {restaurant.address} · {restaurant.phone}
            </p>
          </div>
          <button type="button" className="btn btn-outline btn-sm" onClick={() => setEditing(true)}>
            Edit details
          </button>
        </div>
      )}

      <div className="row-between section">
        <h2>Menu</h2>
        {editingItem === null && (
          <button type="button" className="btn btn-primary btn-sm" onClick={() => setEditingItem('new')}>
            + Add dish
          </button>
        )}
      </div>

      {editingItem !== null && (
        <MenuItemForm
          restaurantId={restaurant.id}
          item={editingItem === 'new' ? null : editingItem}
          categories={categories.data ?? []}
          onSaved={() => {
            setEditingItem(null);
            menu.reload();
          }}
          onCancel={() => setEditingItem(null)}
        />
      )}

      {menu.loading && <Spinner />}
      <ErrorMessage error={menu.error} onRetry={menu.reload} />
      {menu.data?.length === 0 && <p className="muted">No dishes yet.</p>}
      <table className="table card">
        <thead>
          <tr>
            <th>Dish</th>
            <th>Category</th>
            <th>Price</th>
            <th>Available</th>
            <th aria-label="Actions" />
          </tr>
        </thead>
        <tbody>
          {menu.data?.map((item) => (
            <tr key={item.id} className={item.available ? '' : 'muted'}>
              <td>{item.name}</td>
              <td>{item.categoryName}</td>
              <td>{formatPrice(item.price)}</td>
              <td>
                <label className="radio">
                  <input
                    type="checkbox"
                    checked={item.available}
                    onChange={() => run(async () => {
                      await ownerService.setAvailability(item.id, !item.available);
                      menu.reload();
                    })}
                  />
                  {item.available ? 'Yes' : 'Sold out'}
                </label>
              </td>
              <td className="row gap">
                <button type="button" className="btn-link" onClick={() => setEditingItem(item)}>
                  Edit
                </button>
                <button
                  type="button"
                  className="btn-link danger"
                  onClick={() =>
                    window.confirm(`Delete "${item.name}"? Past orders keep their copy of it.`) &&
                    run(async () => {
                      await ownerService.deleteMenuItem(item.id);
                      menu.reload();
                    })
                  }
                >
                  Delete
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </section>
  );
}

function MenuItemForm({ restaurantId, item, categories, onSaved, onCancel }) {
  const [form, setForm] = useState({
    name: item?.name ?? '',
    description: item?.description ?? '',
    price: item?.price ?? '',
    categoryId: item?.categoryId ?? '',
    available: item?.available ?? true,
  });
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState(null);
  const fieldErrors = getFieldErrors(error);
  const update = (field) => (event) => setForm({ ...form, [field]: event.target.value });

  async function submit(event) {
    event.preventDefault();
    setSaving(true);
    setError(null);
    const body = {
      name: form.name,
      description: form.description || null,
      price: form.price === '' ? null : Number(form.price),
      categoryId: form.categoryId === '' ? null : Number(form.categoryId),
      available: form.available,
      imageUrl: null,
    };
    try {
      if (item) await ownerService.updateMenuItem(item.id, body);
      else await ownerService.createMenuItem(restaurantId, body);
      onSaved();
    } catch (err) {
      setError(err); // e.g. 409 duplicate dish name, 400 price
    } finally {
      setSaving(false);
    }
  }

  const fieldError = (name) => fieldErrors[name] && <small className="field-error">{fieldErrors[name]}</small>;

  return (
    <form className="form card" onSubmit={submit}>
      <h3>{item ? `Edit ${item.name}` : 'New dish'}</h3>
      <div className="grid-2">
        <label className="field">
          <span>Name</span>
          <input required maxLength={120} value={form.name} onChange={update('name')} />
          {fieldError('name')}
        </label>
        <label className="field">
          <span>Category</span>
          <select required value={form.categoryId} onChange={update('categoryId')}>
            <option value="">Choose...</option>
            {categories.map((category) => (
              <option key={category.id} value={category.id}>
                {category.name}
              </option>
            ))}
          </select>
          {fieldError('categoryId')}
        </label>
      </div>
      <label className="field">
        <span>Description</span>
        <input maxLength={500} value={form.description} onChange={update('description')} />
      </label>
      <label className="field">
        <span>Price (₹)</span>
        <input required type="number" min="0.01" step="0.01" value={form.price} onChange={update('price')} />
        {fieldError('price')}
      </label>
      {error && !Object.keys(fieldErrors).length && <p className="field-error">{getErrorMessage(error)}</p>}
      <div className="row gap">
        <button type="submit" className="btn btn-primary" disabled={saving}>
          {saving ? 'Saving...' : 'Save dish'}
        </button>
        <button type="button" className="btn btn-outline" onClick={onCancel}>
          Cancel
        </button>
      </div>
    </form>
  );
}
