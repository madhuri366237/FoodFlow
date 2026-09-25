import apiClient from './apiClient.js';

function compact(params) {
  return Object.fromEntries(Object.entries(params).filter(([, value]) => value !== undefined && value !== ''));
}

export async function getUsers(params) {
  const response = await apiClient.get('/admin/users', { params: compact(params) });
  return response.data;
}

export async function setUserEnabled(id, enabled) {
  const response = await apiClient.patch(`/admin/users/${id}/status`, { enabled });
  return response.data;
}

export async function getRestaurants(params) {
  const response = await apiClient.get('/admin/restaurants', { params: compact(params) });
  return response.data;
}

export async function setRestaurantActive(id, active) {
  const response = await apiClient.patch(`/admin/restaurants/${id}/status`, { active });
  return response.data;
}

export async function getOrders(params) {
  const response = await apiClient.get('/admin/orders', { params: compact(params) });
  return response.data;
}

export async function getCoupons(page = 0) {
  const response = await apiClient.get('/admin/coupons', { params: { page, size: 20 } });
  return response.data;
}

export async function createCoupon(coupon) {
  const response = await apiClient.post('/admin/coupons', coupon);
  return response.data;
}

export async function updateCoupon(id, coupon) {
  const response = await apiClient.put(`/admin/coupons/${id}`, coupon);
  return response.data;
}
