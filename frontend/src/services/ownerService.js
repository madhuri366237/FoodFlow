import apiClient from './apiClient.js';

// ---------- restaurants ----------

export async function getMyRestaurants(page = 0, size = 50) {
  const response = await apiClient.get('/owner/restaurants', { params: { page, size } });
  return response.data;
}

export async function createRestaurant(restaurant) {
  const response = await apiClient.post('/restaurants', restaurant);
  return response.data;
}

export async function updateRestaurant(id, restaurant) {
  const response = await apiClient.put(`/restaurants/${id}`, restaurant);
  return response.data;
}

export async function setOpen(id, open) {
  const response = await apiClient.patch(`/restaurants/${id}/open-status`, { open });
  return response.data;
}

// ---------- menu ----------

// The owner's view includes unavailable dishes.
export async function getFullMenu(restaurantId) {
  const response = await apiClient.get(`/owner/restaurants/${restaurantId}/menu`);
  return response.data;
}

export async function createMenuItem(restaurantId, item) {
  const response = await apiClient.post(`/restaurants/${restaurantId}/menu-items`, item);
  return response.data;
}

export async function updateMenuItem(id, item) {
  const response = await apiClient.put(`/menu-items/${id}`, item);
  return response.data;
}

export async function setAvailability(id, available) {
  const response = await apiClient.patch(`/menu-items/${id}/availability`, { available });
  return response.data;
}

export async function deleteMenuItem(id) {
  await apiClient.delete(`/menu-items/${id}`);
}

// ---------- orders ----------

export async function getOrders({ status, restaurantId, page = 0, size = 20 }) {
  const response = await apiClient.get('/owner/orders', {
    params: { ...(status && { status }), ...(restaurantId && { restaurantId }), page, size },
  });
  return response.data;
}

// The backend validates the move against the state machine (409 if illegal).
export async function changeOrderStatus(orderId, status, note) {
  const response = await apiClient.put(`/orders/${orderId}/status`, { status, note: note || null });
  return response.data;
}
